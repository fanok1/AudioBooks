# Source engine: what is still worth refining before the new sites

Date: 31 October 2026. Base: `master` at `e1eadf1` (13 adapters, 612 JVM tests). Scope: the engine behind
`source/`, `search/`, `net/`, the models that drive the adapters, the registration points and the tests, judged by one
question only: **what does it cost to add the next 7–10 sites, and where is that cost avoidable?** The candidates are
the ten of `docs/new-audiobook-sources.ru.md`; the preparation is `tasks/plan.md` §12 and `tasks/open-issues.md`.
This is an assessment and a proposal list, not a change. Comparison material: `smold22/AudioBook` (`source-api`,
`source-extra`, 30 Kotlin adapters), read in full for §6.

## 1. Verdict

The preparation reached its goal: the engine is now a real adapter architecture, not a transitional one. One
interface (`SourceParser`, 7 required methods, the rest defaults), one registry row (`Provider`), no site branches in
shared code (a grep over everything outside `source/` finds five deliberate leftovers, §7), failure as values
(`ProviderPage`), menus as data (`Sort`), one page fetch per book opening (`BookPages`), per-site proxy route and a
probe that uses the catalog path itself. That is a sound base; **nothing architectural blocks adding plain
HTML/JSON/DLE sites today.** No rewrite, no Kotlin, no plugin system and no DI container is warranted.

What is still expensive is not the architecture but the **volume per adapter**. The last site added through this
contract (`c208ec0`, audiokniga.life) cost 536 adapter lines, 230 test lines, 10 fixtures and three one-line
registrations. Roughly half of a typical adapter is hand-written POJO plumbing that every site repeats: null-checked
`selectFirst` → `attr`/`text` → setter chains (27–56 setter calls per adapter), a second, independent copy of the
description parser for `book()` in 9 of 13 adapters (~830 lines), the DLE sort form rebuilt by hand in each DLE
adapter, and private helpers (`number`, `names`, `listing`, `value`, `duration`, `toInt`) re-invented per file. The
toolkit (`Html`, `Players`, `Dle`) removed the *orchestration* duplication; it has not yet removed the *mapping*
duplication. That is where a good part of the next 300–500 lines per site, and most of the per-site review effort,
can be cut with small, low-risk changes.

Ranked by value per line changed, the recommendations are:

1. Never write `parseBook` again (the default `book()` already exists), reuse the shared fixture loader, and fix the
   one silent spot in the toolkit (`Html.cards` swallows every card without a log) (§4.2, §3.1). Zero risk, a
   howto rule and ~15 lines.
2. Toolkit hygiene the first candidates will hit: a correct shared DLE sort-form builder (today `Dle.sortedListing`
   hardcodes `desc` and the two DLE adapters each build their own), generic JSON helpers out of `Players`, a public
   `JsonPage` factory for fixtures, the signed-XSPlayer tuple parser out of Izibuk, a reusable `?period=` implementation
   (§4.4). Each 20–60 lines.
3. A field-mapping helper for cards and descriptions (`Html.book(..)`, `Html.description(..)`), proven first on
   Poleknig and AudioknigaLife under their fixture tests, then made the howto's example (§4.1). Largest per-site saving
   (~60–100 lines), but only after step 2 has shown what the real variations are.
4. A manifest guard test and a contract-invariant test over `Provider.values()` (§4.3, §4.5); keep per-site tests as
   assertions, not scaffolding.
5. Draw the signed-media boundary earlier: signed MP3 already exists (audioknigi.fun) and Biblus/Golosom bring more
   of it before the two deferred sites; the playback refresh matches chapters by count only. One focused test now,
   the shared-MP3 and HLS-export work later (§5).
6. Decide the two scaling constants (`Sources.MAX_ACTIVE = 13`, `SearchSession.MAX_PARALLEL = 13`) before the
   registry passes 13 rows (§5).

Everything above is a small or medium diff, each independently shippable and each guarded by the existing fixture
tests. The one medium-sized engine change worth considering, moving page fetching from Jsoup's `HttpURLConnection` to
the OkHttp client the app already has (§4.6), is not required for the candidates and touches six adapters; it is
listed with its real cost because it is the only remaining place where the engine has two of something (two cookie
jars, two connection pools).

A second opinion (Codex, gpt-6.1-sol) reviewed a draft of this document against the code; §10 lists what it
changed and where it was not followed.

## 2. Evidence base

| Measure | Value |
|---|---|
| Adapters | 13, 373–1202 lines (median 519); `source/` total 9 597 lines incl. toolkit |
| Toolkit | `Html` 165, `Players` 375, `Dle` 110, `Sort` 107, `SourceParser` 346 |
| Engine outside `source/` | `search/` 2 165, `net/` 2 600, `model/` 2 389 |
| Contract | 7 required methods, 38 default hooks |
| Last site added (`c208ec0`) | 16 files: adapter 536, test 230, fixtures 10, `Provider` +5, manifest +1, `BookPageFixtures` +1 |
| `book()` overridden | all 13; 9 with an independent parser of 52–148 lines (~830 lines in all), the 4 newest with `BookPOJO.of(description)` in 3–6 lines |
| Setter calls per adapter (`setUrl/Name/Photo/...`) | 27 (AknigXyz) – 56 (Akniga) |
| Site-specific code outside the contract | `SearchebleModel` (chips, 7 refs), `OtherSourceModel.label`, `DefaultCover`, `CustomProxyDialogFragment` default, cookie screen |
| Tests | 12 616 lines; `source/` 3 941 of which `*MenuTest` 111 tests pinned to pre-migration literals |

The adapter contract reaches the UI in exactly four places (`BooksFragment` menu building, `BooksPresenter.pageUrl`,
`ListingFilters` forwards, `Covers`), all through `Provider`. Everything else is behind the models. That is the
property that makes the rest of this document possible: a change inside `source/` cannot break a screen.

## 3. Architecture assessment

### 3.1 Strengths worth protecting

- **Parse is pure, fetch is owned by the engine.** `listing(Document, url, page)` and friends take a page; the
  engine fetches through `fetch()` with the site's timeout, cookies, POST mode, Referer and proxy route. The reference
  app fuses the two and therefore cannot test offline, cannot route per site and builds one HTTP client per adapter.
  Keep this split; every proposal below respects it.
- **Failure is typed at the page level.** Challenge → `CookesExeption`, HTTP ≥ 400 → `HttpStatusException`, truncated
  body → `IOException`, player missing → `IllegalStateException`, takedown → `BookRemovedException`;
  `ProviderPage.failed` maps them to `BLOCKED/TIMEOUT/ERROR`, and `EMPTY` is only a page with no card at all
  (`ProviderPage.of(items, hasMore, rawCards)`). The reference app swallows parser failures into empty track lists in
  ~25 adapters; ours cannot at the track level. One gap remains at the card level: `Html.cards` (`Html.java:67`)
  catches every exception per card *without* the log `Html.each` writes, and an adapter that calls the two-argument
  `ProviderPage.of(result, hasMore)` (Poleknig, `Poleknig.java:227`) reports a page whose every card failed as
  `EMPTY`, so a selector change on the site looks like "no more books" to the catalog and to `SourceProbe`. Fix: log in
  `cards` as in `each`, and pass `rawCards` (the howto already says so; the example adapter does not). 5 lines.
- **Capabilities are the methods themselves.** A feature the site lacks is a default returning "nothing", and the UI
  hides it. No separate boolean flags that can disagree with their methods.
- **Menus, periods, genre narrowing and duration bounds are data or hooks**, not XML per site and not presenter
  branches. A new sort kind is one enum row.
- **Request economy is explicit.** One page per book opening, bounded fan-out in search, probe = catalog page 1,
  no N+1 anywhere in the contract. The speed measurement (§12.9) proved the migration added no request.
- **Registration is one row**, and the two things that still live elsewhere (manifest host, former-host alias) are
  one line each.

### 3.2 Weaknesses, in order of cost to the next site

1. **Mapping boilerplate.** See §1. Concretely, `Poleknig.parseListing` (lines 155–229) is 75 lines of which about 12
   are selectors; `parseDescription` and `parseBook` (lines 230–353) are the same ten selectors written twice. The howto
   names Poleknig as the short example; a newcomer copies this shape.
2. **A site's identity is scattered.** For a new site: `Provider` row (server, title, `prefValue`, `prefKey`,
   `sourceId`, four section URLs, parser), `AndroidManifest.xml` host, optionally `Url.HOST_ALIASES`; and in tests
   `BookPageFixtures.BOOK_PAGES`. Two conventions coexist: the nine old sites keep their URLs in `Url.java`
   (`SERVER_*`, `INDEX_*`...), the four new ones in the adapter (`AknigXyz.SERVER`). `prefKey` duplicates what
   `Sources.active()` already decides (a second on/off per site, the search checkboxes), and `sourceId` is a `Consts`
   constant for old rows and a literal for new ones.
3. **Listing kind inferred from URL text.** `BooksFragment.onCreateMenu` hides the source item when the URL contains
   `reader|author|genre|serie|performer`; `Poleknig.sorts` uses the same trick. A site whose people pages are
   `/xfsearch/author/` or `/people/` silently gets the wrong menu. The fragment already knows `modelID`; the heuristic
   is a leftover.
4. **Generic helpers hidden in the wrong class, and one shared helper that is wrong.** `Players.string/seconds/arrayOf/
   json/array/object/resolve` are JSON helpers any JSON-API adapter needs (Lis10book's catalog is JSON), but they sit
   in the player-script class; new adapters will either reach into `Players` for non-player work or copy them.
   `Html.each`, `Html.cards`, `Html.rows` are three copies of one loop. `Dle.sortedListing` (`Dle.java:88`) hardcodes
   `dledirection=desc` and `set_direction_sort=desc`, which is why both DLE adapters build the real form themselves
   (`AknigXyz.sortedUrl`, `AknigXyz.java:217`, with `dle_direction_<key>`; `AudioknigiFun.sorted`,
   `AudioknigiFun.java:189`), and the title sort is ascending. Two of the first candidates (Audiokniga-online.ru,
   Slushat-knigi) are DLE; without a correct shared builder each copies ~15 lines and the session-key logic.
   `Izibuk.parseTracks` (`Izibuk.java:557`) holds the signed-XSPlayer tuple mapping (`mp3_url_prefix` + `sign`) that
   Biblus (XSPlayer, signed MP3) will need; `Players.xsPlaylist` covers the other XSPlayer schema only. `JsonPage`'s
   constructor is package-private and only `SiteHttp.json` builds one, so a JSON adapter's fixture test has no way to
   hand its parser a `JsonPage`.
5. **Period hooks are four methods of URL arithmetic per site** (`periods`, `periodOf`, `withPeriod`,
   `periodsAreDates`). Four of today's sites implement the same `?period=<word>` pattern by hand; Biblus
   (week/month) will be the fifth.
6. **Per-site tests are scaffolding-heavy.** Each `<Site>Test` repeats fixture loading, URL assertions and the same
   checklist (cards, next page, search, sorts, description, chapters). The `*MenuTest` files pin pre-migration literals
   with comments pointing at deleted HEAD line numbers; they did their job for the migration and will now cost a
   comment edit per intentional menu change.
7. **Two network stacks.** Pages go through Jsoup (`HttpURLConnection`, its own `JAR` map in `SiteHttp`); media,
   covers and the proxy check go through OkHttp (`App` client, `MemoryCookieJar`). Consequences: a session cookie a
   page sets does not reach media unless the adapter copies it (`mediaCookies`), two connection pools to the same
   hosts, no cancellation of page requests (T09), and challenge/proxy logic exists once per stack
   (`SiteHttp.executeOnce` vs `AppProxy` interceptors). Not blocking, but it is the one duplicated subsystem left.
8. **Data model is text-typed.** `BookPOJO.time` is free text parsed by four regexes (`ListingFilters.durationMinutes`),
   `reting` is a string that some sites fill with votes and others with views, `DescriptionPOJO.like/disLike/favorite`
   are strings. The POJOs are persisted (Gson in `BookPOJO.parceJsonToBookPojo`, Room, backup), so changing their
   shape is not cheap and is rightly deferred (T12). What *is* cheap is a convention and a helper so new sites emit
   the same spellings (§4.4).
9. **No uniform "preview only" signal.** A LitRes fragment (uKnig, Baza Knig, Golosknig cards) is today handled inside
   each adapter (filtering cards, or throwing). Lis10book marks `closed/fragment` explicitly; Golosom has
   `blocked`. The contract has `BookRemovedException` for takedowns but nothing for "plays, but only a fragment". This
   is a 10-line decision (a flag on `DescriptionPOJO` or a second exception), best taken with the first candidate that
   needs it, not before.

### 3.3 Scalability to 20–25 sources

- **Registry and dispatch:** `Provider.ofUrl` and `ofNetworkHost` are linear scans over `values()` with a
  `Url.canonical` per call; at 25 rows that is still microseconds. No change needed.
- **Search:** `SearchSession.MAX_PARALLEL = 13` was chosen to keep today's speed with 13 sites. At 25 sites it means 13
  concurrent page downloads on a phone radio; the right number is probably 6–8 and must be measured, not guessed
  (`SearchSession.java:60`). The case-variant retry pass (`finish()`) doubles the request count for sources that found
  nothing; with 25 sources that is up to 25 extra requests per query. Consider limiting the retry to the selected
  source and the first N, or to sources whose first pass was fast.
- **Active cap:** `Sources.MAX_ACTIVE = 13` and the string `sources_max` cap the user at 13 active sites. This is a
  product decision that must be made before the 14th row; technically it is one constant.
- **Health and cooldown:** `SourceHealth` stores last results and runs daily; nothing yet *uses* a bad result
  (a BLOCKED site is still searched first if selected). A 20-line consultation in `Provider.selectedFirst()` (push
  BLOCKED/TIMEOUT sites to the end, never remove them) is the cheapest scaling win for the multi-site search.
- **Settings UI:** source menu items (`SOURCE_ITEM_BASE + sourceId`), the search checkbox screen and the sources
  dialog are all built from the list; they grow linearly and need nothing. The drawer sections (`index/sections/
  authors/performers`) are per site and nullable; fine.
- **Threads:** page fetches run on `Schedulers.io()` (unbounded, cached threads); with 25 sources a search can
  occupy 13 io threads plus covers. Acceptable; the bound is `MAX_PARALLEL`, not the scheduler.

## 4. Proposals

Each item: what, why, cost (lines / files), risk, how it is verified. "Old adapters unchanged" means the proposal
adds an option for new sites and migrates old ones only where fixture tests already prove equivalence.

### 4.1 Field mapping for cards and descriptions — `Html.book`, `Html.description`

**What.** Two small fluent mappers in `source/Html.java` that read a selector and fill the POJO field only when the
value is non-empty, with the same conventions every adapter already follows (text trimmed, `absUrl("href")` for links,
`imgSrc` for covers):

```java
static BookPOJO card(Element c) {
    return Html.book(c).title("a.book-title").cover("img").author(".book-author a").reader(".book-reader a")
            .genre(".book-genres a").series("a[href*=/series/], .book-series a").time(".book-duration").get();
}

public static DescriptionPOJO parseDescription(Document d) {
    return Html.description(d).title("h1[itemprop=name]").cover("img.cover, img[src*=/covers/]")
            .author("h2.book-author > a").reader("div.book-reader > div > a").genre("div.book-genres > div > a")
            .series("a[href*=/series/]").time("div.book-duration > div").text("div#description").get();
}
```

Each "person" setter fills name and URL from one anchor; a selector list is a fallback chain (Poleknig's
`data-original`, misspelled `data-originl`, `src`); `authors(css)` joins several anchors as AudioknigaLife's `names`
does; `labeled("Автор", ...)` covers the sites that key fields by a label (`AknigXyz.card`, `AudioknigaLife.stat`,
Izibuk, Otrub). The card mapper's `get()` returns null when the title is empty so that `Html.cards` drops the card; the
description mapper always returns a POJO, so the model's existing "no book on page" `IOException`
(`BookDescriptionModel.java:117`) keeps working on an empty title.

**Why.** This is where the lines are. Poleknig's two parsers go from ~115 lines to ~25; AudioknigaLife's `card` from
45 to ~15. More important than lines: a reviewer reads a selector table and sees the site's structure at a glance,
and a new site cannot forget the empty-string guard or the `absUrl`.

**What it does not replace.** Whatever is not "first match → field": split authors, label rows with mixed text,
rating scales, listing-URL normalisation. Those stay as hand-written lines next to the mapper. The honest estimate is
that the mapper covers 60–80 % of a card and description, not all of it; that is still the single largest saving.

**Why this is not the "selector DSL" `plan.md` §10 warned against.** It is Java code, not configuration; an adapter
mixes it freely with hand-written parsing (pagination quirks, label rows, split authors), and nothing in the engine
interprets it. The warning was against describing whole sites as data; this is a builder of setters.

**Cost.** ~150 lines in `Html`, one test (~60 lines) against the Poleknig fixtures. Old adapters unchanged; migrate
Poleknig and AudioknigaLife as the proving slice (their fixture tests must pass byte-for-byte), then let the howto point
at them. Do this *after* §4.4, when the first JSON and DLE candidates have shown which variations recur; a mapper
designed from Poleknig alone would need overrides at the second site.

**Risk.** Low as long as the mapper stays optional. The semantic traps are known: first anchor vs. joined names,
fallback attribute order, and a title that is present but belongs to a wrapper element; each is one explicit method,
and the fixture tests of the two migrated adapters are the proof.

### 4.2 Stop writing `parseBook`

**What.** The default `book()` is already `BookPOJO.of(description(page, url), url)`. Make that the rule in the howto
and in review: a new adapter overrides `book()` only to add a field the card has and the description lacks (AknigXyz
adds the rating in 6 lines). Optionally, delete the 9 legacy copies where `BookPOJOTest.everySourceFixtureYieldsANamedBookWithAbsoluteCover`
and the adapter fixtures prove the default yields the same card.

**Why.** ~830 duplicated lines today; 50–150 per new site if the pattern is copied. The duplicate is also a correctness
hazard: a selector fixed in `parseDescription` and not in `parseBook` makes deep links disagree with the book page
(this is exactly the class of bug `IzibukSelectorsTest` had to guard twice).

**Cost.** Howto: 3 lines. Deleting legacy copies: one adapter at a time, each a −50..−150 diff verified by the
existing fixture test plus a one-off assertion that `BookPOJO.of(parseDescription(fixture))` equals the deleted
`parseBook(fixture)` field by field. Where they differ (rating, cover variant), keep a 5-line override.

**Risk.** Low per adapter; the differences are visible in the one-off comparison, which is deleted after.

### 4.3 One file per site by convention; one guard test for the manifest

**What.**

- Convention, already followed by the four newest adapters and to be written into the howto: a site's `SERVER`,
  `INDEX`, `GENRES`, `AUTHORS`, `READERS` and former hosts are constants of its adapter class, and the `Provider` row
  references them. The nine old sites' constants in `Url.java` can move the same way one adapter at a time, or stay.
  (The alternative, reading them through `SourceParser` methods inside the enum constructor, was considered and
  dropped: it runs adapter code before `Provider.values()` exists, the row saves only four arguments, and
  `Provider.java:72` deliberately forbids parser code from touching `Provider` during initialisation.)
- Former hosts: `Url.HOST_ALIASES` stays a plain table in `Url` (it is consulted during `Provider` resolution, so it
  must not depend on `Provider`); the howto lists it next to the manifest as the second one-line registration.
- `prefKey` derived (`"search_" + prefValue`) through a second `Provider` constructor for new rows; the old rows keep
  their stored keys. Or, the product decision: drop the search checkbox screen, since `Sources` already says which
  sites are active (one fewer concept, one fewer preference per site, −40 lines in `SettingsFragment`/`FilterAdapter`).
- A JVM test that parses `app/src/main/AndroidManifest.xml` and asserts the deep-link host set equals
  `{server host, former hosts, mobile hosts}` of every `Provider`. Today a forgotten manifest line fails silently (the
  site's links open in a browser).

**Why.** The reviewer opens one adapter and sees server, sections, aliases and parsing; the guard test replaces a
checklist item with a failing build.

**Cost.** Howto: a paragraph. Test ~40 lines. Constructor overload: 10 lines. No initialisation risk.

### 4.4 Toolkit hygiene for the first JSON, DLE and signed-MP3 candidates

- **A correct shared DLE sort form.** `Dle.sorted(listingUrl, Order, key)` producing
  `dlenewssortby=<field>&dledirection=<order.direction>&set_new_sort=dle_sort_<key>&set_direction_sort=dle_direction_<key>`
  (what `AknigXyz.sortedUrl` and `AudioknigiFun.sorted` build today), with `Dle.sortedListing` retired or delegating.
  Key discovery (`main` vs `cat_<id>`, learned from the page) stays in the adapter. ~20 lines; the two existing DLE
  adapters shrink by ~15 each and their `*MenuTest`/`*Test` prove equivalence. Needed by Audiokniga-online.ru and
  Slushat-knigi.
- **`Json` helper class** (`source/Json.java`, or `net/`): move `string/seconds/arrayOf/json/array/object` out of
  `Players` (keep deprecated forwards for a release, or just update the 2 callers). Add `Json.each(JsonArray, mapper)`
  with the same skip-one-bad-item semantics as `Html.each`. ~60 lines moved, ~20 new. Lis10book's catalog, search,
  detail and playlist are all JSON; without this it will copy helpers or misuse `Players`.
- **`JsonPage.of(String url, String json)`** public factory (or a test-only one in `BookPageFixtures`), so a JSON
  adapter's fixtures are `.json` files handed to the parse methods exactly as `SiteHttp.json` would. 5 lines; without
  it the first JSON adapter has no offline test.
- **Signed XSPlayer helper**: `Players.signedXsPlaylist(player, bookUrl, bookName)` extracted from
  `Izibuk.parseTracks` (`mp3_url_prefix` + per-item `sign`), for Biblus. ~25 lines moved.
- **`Periods` value object** implementing the four hooks for the common `?param=value` shape:
  `Periods.query("period", WEEK, "week", MONTH, "month")` returned from a single `periods()`-style accessor, with the
  adapter's four overrides becoming one-liners. ~50 lines; refactoring Knigoblud/Otrub/Knigavuhe onto it is optional and
  guarded by their `*MenuTest`.
- **One loop in `Html`:** `cards` and `rows` become `each` plus an `isNull` filter (−30 lines). `number(String, int)`,
  `names(Elements)` (joined authors), `labeled(scope, "Автор")` move from adapters into `Html` (the four newest adapters
  each have a private copy).
- **Duration convention:** `Html.duration(hours, minutes, seconds)` → `"H:MM:SS"` and `Html.durationText(Element)`
  that normalises the site's wording. New adapters emit one spelling; `ListingFilters.durationMinutes` keeps reading
  the old four. Rating convention written down once in `BookPOJO`: `reting` is the user rating or `"0"`, views never
  (already the behaviour of the newest adapters; make it a sentence in the Javadoc and the howto).

### 4.5 Tests: generic invariants plus shared fixture helpers

**What.**

- `SourceContractTest`, parametrised over `Provider.values()` (JUnit 4 `Parameterized`), no fixtures, no network:
  `pageUrl(index, 1)` is on the site's host and `pageUrl(index, 2) != pageUrl(index, 1)`; every `Sort` in
  `sorts(index)` has a non-null `sortUrl` on the site's host, and the sorted URL's own `sorts()` is either empty (a
  terminal destination such as AknigXyz's Top-100, `AknigXyz.java:120`) or still offers that sort; `searchUrl(BOOKS,
  "q", 1)` non-null and page 2 differs; for each `p` in `periods(u)`: `periodOf(withPeriod(u, p)) == p`; `hosts()`
  never contains the server itself; `timeoutMs() >= TIMEOUT_MS`. Genre round trips (`inGenre`/`genreOf`) stay out of
  it: for a DLE site they depend on a category id learned from a page. ~100 lines once; every future site gets it for
  free.
- Shared fixture helpers, not a base class: `BookPageFixtures.load` already exists and the four newest tests each
  re-declare a six-line loader; add `fixture(site, name)` and `jsonFixture(site, name)` there and use them. The
  remaining ~200 lines of a site test are distinct assertions (card fields, pager edge cases, removed books, search
  without results) that a base class could only remove by weakening coverage; keep them. The saving here is small
  and the honesty matters more: a new site's test should be *mostly assertions*.
- Keep `*MenuTest` as they are (they are the proof of the migration), but stop adding "HEAD line" provenance comments;
  new sites get menu coverage from the invariant test plus 5–10 explicit `sortUrl` expectations in their fixture test.
- A `live` source set or a JUnit category excluded from `testDebugUnitTest`, holding one `LiveSmokeTest` that runs the
  §9.2 checklist against real sites and prints a status line per provider (the useful half of the reference app's
  `AllSourcesCheckTest`, with assertions). Run by hand before enabling a site; never in CI. ~100 lines.

**Why.** The per-site test is the second largest line item after the adapter, and most of it is identical. Offline
invariants also catch the contract mistakes that fixtures cannot (a sort whose URL loses the menu).

### 4.6 Medium option: pages through OkHttp

**What.** `SiteHttp.connect/execute/postForm/json/body` built on the app's `OkHttpClient` (already configured with
proxy selector, authenticator, `MemoryCookieJar`, TLS anchors) with `Jsoup.parse(InputStream, null, url)` for charset
sniffing; `executePosted` becomes an OkHttp interceptor or a manual loop as today; the `JAR` map and `MemoryCookieJar`
collapse into one. Expose a `Call` so a disposed Rx chain cancels the request (T09 for free).

**Why.** One cookie store (session cookies reach media and covers without `mediaCookies`), one connection pool and
HTTP/2 multiplexing to hosts shared by pages and covers (a real win on slow mobile links), one proxy/challenge code
path, real cancellation. The reference app does this and it is the one place its design is leaner.

**Cost.** ~260 lines rewritten in `SiteHttp`; `SiteHttpTest`/`SiteHttpJsonTest` reworked (they mock Jsoup's
`Connection.Response` through a `Proxy`; with OkHttp a `MockWebServer` test is simpler and stronger); `Dle.fetch`
untouched. **Adapters are not untouched:** `SiteHttp.connect` returns a Jsoup `Connection`, and Akniga (7 uses),
Audioboo (4), BazaKnig (4), AudioknigiFun, Uknig and `Players.loadPlayerjs` configure it (POST data, headers, XML
parser, raw responses). Either a thin request builder keeps that surface, or those six call sites migrate. Cancellation
is not free either: `BookPages` shares one in-flight fetch between the description and the chapter list
(`BookPages.java:58`), so disposing one subscriber must not cancel the other's request. Acceptance is more than the
request counter of §12.9: cookies across redirects, the hand-followed POST redirects of audioboo, charset sniffing and
the proxy-suspect signal all need a `MockWebServer` test each. Three to five days.

**Recommendation.** Not before the first two candidates; do it before the registry doubles, and do it alone. It is
listed here so that nobody adds a third cookie path meanwhile.

### 4.7 Small fixes to residue and heuristics

- `BooksFragment.onCreateMenu`: replace `mUrl.contains("reader|author|...")` by `modelID` plus a boolean the
  presenter already has (it knows whether the listing is a drawer root); `Poleknig.sorts`: match path prefixes, not
  substrings. 10 lines.
- `SearchebleModel`: a default hook `searchChips(String query)` returning `SearcheblPOJO` (empty by default),
  implemented by Knigavuhe and Abmp3 (move `AutorsSearchABMP3` into `Abmp3`); the model loops over active providers.
  −7 site references in `model/`, and a new site with author suggestions gets them without touching the model. ~40
  lines moved.
- `OtherSourceModel.label`: five sites keep legacy labels through a `switch`; a `Provider.label` field or
  `parser.label()` default removes the last `switch` on a provider in `model/`. 10 lines.
- `Sort.menuId`: build the Order items with ids from a reserved range (`SORT_ITEM_BASE + ordinal`, as the fragment
  already does for sources with `SOURCE_ITEM_BASE` and for periods with `Menu.FIRST + ordinal`,
  `BooksFragment.java:676,775`) in their own group, dispatch by group in `BooksPresenter.onOptionItemSelected`
  (`BooksPresenter.java:532` dispatches by item id alone today), then drop the `ids.xml` entries. A new sort kind
  becomes enum row + string instead of three edits. ~40 lines; a collision test over all item id ranges is part of it.
- Optional: in the multi-site search only, start the sources whose last `SourceHealth` status is BLOCKED/TIMEOUT
  last (never remove them, never change the user's order elsewhere). 20 lines. Its value is small while
  `MAX_PARALLEL` equals the active count; it matters once §5 lowers the parallelism.

## 5. Decisions that need the owner, not code

1. `Sources.MAX_ACTIVE` beyond 13: raise, or make it "all"? (one constant, one string).
2. `SearchSession.MAX_PARALLEL` at 20+ sites: measure 6/8/13 on a device with a slow network before deciding; keep the
   case-variant retry pass or limit it.
3. Search checkboxes vs. the sources dialog: keep both on/off mechanisms, or retire `prefKey`?
4. "Enable the proxy for blocked sites" as a hint after `SourceHealth` (pending from §12.3).
5. The deferred stage E is three gates, not one, and they fall at different points in the candidate order:
   - **Signed, expiring media URLs** are not new: audioknigi.fun already serves them, and Biblus (signed MP3,
     candidate 4) and Golosom (signed MP3) come before the two "hard" sites. The refresh path exists
     (`MediaPlayerService.refreshChapters` → `BookPages.forget`), but it accepts any fresh playlist with the same
     chapter count (`MediaPlayerService.java:1362`): a reordered playlist resumes the wrong chapter. One focused test
     (same count, different order or URLs) and a match by chapter name or index-plus-name before Biblus. ~40 lines.
   - **One MP3 with many clips in downloads** (Golosknig): `DownloadIds` keys progressive files per ordinal; playback of
     clips already works (`AudioPOJO.timeStart/timeFinish` are honoured by `MediaPlayerService`). This is the real
     shared-resource work; sequence Golosknig after it.
   - **AES-128 HLS** (Audiokniga.one): the recorded candidate uses separate `.m3u8` per chapter with TS segments;
     playback and HLS download exist. What is open is export (`ChapterExtractor`, fMP4) and key/segment headers. A
     media probe on a device, not an engine change, decides how much of it is needed.
   Nothing in §4 depends on these.

## 6. Comparison with `smold22/AudioBook`

Read: `source-api/SourceApi.kt`, all 30 adapters of `source-extra`, `HttpUtil.kt`, `JsRuntime.kt`, the test
sources, and the app-side `SourceHealthTracker`, `SourceCooldown`, `SourcesSimilarProvider`, `BookCache`.

**Their adapters are 119–254 lines (median 196) against our 373–1202.** The difference is mostly scope, not design:
their contract has no sorts, periods, genre narrowing, series tabs on the book page, comments, other narrations,
cross-site lookup, Referer/cookie hooks or proxy routing; `Book` is a flat 12-field record built with named arguments;
lists have no `hasMore` (an empty page, or three filtered-empty pages, means the end: extra requests and false ends);
parsing failures become empty track lists in ~25 adapters; every adapter fetches for itself with its own `OkHttpClient`
(30 connection pools, `Proxy.NO_PROXY` hard-coded); tests are live network runs that print and always pass, except
one fixture test (IziBuk). Their health check pings `baseUrl` and counts any HTTP answer as alive. Capability flags
(`supportsNew`, `supportsTorrent`) are booleans separate from the methods. None of that should be borrowed.

**What is worth borrowing, adapted:**

| Idea | Theirs | Ours after adaptation | Cost |
|---|---|---|---|
| One generic runner over all sources with a status line per source | `AllSourcesCheckTest`, live, no assertions | §4.5: offline invariant test in CI + a manual live smoke with assertions | ~220 lines once |
| Static parsers over inline HTML fixtures | only IziBuk | already the rule; keep | 0 |
| Shared Playerjs helpers | `playerjsPlaylistUrl` + `parsePlayerjsJson` | `Players.loadPlayerjs` already covers folders, mirrors, obfuscated URLs | 0 |
| One balanced-brace JSON extractor | copied in 9 adapters | `JsonSlice` already shared | 0 |
| Cooldown after a block | 5 min after one 403/429, matched on message text | `SourceHealth` has the data; order `selectedFirst()` by it, never mute (§4.7) | 20 lines |
| Cross-source dedup of search hits by author + normalised title | `SourcesSimilarProvider` | **do not**: `plan.md` §10 is right that different narrations are different books; dedup by canonical URL as today | 0 |
| Referer carried inside the media URL (`?ref=`), stripped by the player | `withRefererRef` | ours is `mediaHeaders(bookUrl)`, typed and not visible in stored URLs; keep | 0 |
| Rhino for obfuscated player scripts | `JsRuntime` (Rhino, CryptoJS 14 KB, `decode.js` 27 KB, re-evaluated per call) | **do not** by default (`plan.md` §10); port the codec (they ported the same XOR/MD5 codec twice in Kotlin, 45 lines each: Audioknigi.Pro's codec is a known quantity) | 0 now |
| Retries with backoff | 3 attempts incl. 4xx | no; a retry on 4xx wastes the proxy budget. One retry on a connection reset for idempotent GETs could be considered inside `SiteHttp.executeOnce` only, measured | optional |
| `estimateCount` genre counts | cards × last page | we show the site's own counts where it prints them (`GenrePOJO.reting`); do not add a request per genre | 0 |

Their per-adapter structure does confirm one thing about ours: the ~45 % of an adapter that is "build the record twice
with named arguments" is the same boilerplate our setter chains are, so §4.1 attacks the common cost, and the remaining
~55 % (selectors and quirks) is irreducible per site in both designs.

## 7. Loose ends and unverified items

From `tasks/open-issues.md`, still true, plus what this review found:

- Unverified on a device: downloads of the new engine, playback on 7 of 13 sites, the daily `SourceHealth` run on an
  unmetered network, chapter refresh during playback, the per-site proxy route with a real proxy, `connectedDebugAndroidTest`.
- `JsonPage extends Document`: accepted compromise; the remaining hazard is an adapter calling `page.select(...)` on a
  JSON page and reading "no cards" (the other direction is guarded by `JsonPage.of`). Make `JsonPage.select` throw
  (3 lines) once the first JSON adapter exists, so a mixed-up fetch is loud in tests.
- `AknigXyz.CATEGORY_IDS` is process-wide adapter memory (learned DLE category ids). It is fine, but it is the one
  place where "adapters are stateless" is not literally true; if a second site needs the pattern, give it a bounded
  helper rather than a second static map.
- `SearchSession`'s case-variant retry and `MAX_PARALLEL` (§3.3) are the only places whose cost grows with the number
  of sites.
- Site-specific code outside the contract (`ActivityCookies`, `ImportModel`, `AutorsSearchABMP3`, `DefaultCover`,
  `ComentsBookFragment`) is listed in §12.6 as deliberate; §4.7 proposes moving only the chips, the rest can stay.
- Known behaviour differences that are the sites' own (Audioboo "by duration", audiobook-mp3 "Top-100", typos in
  durations) stay documented, not fixed.
- Legacy constants: `Consts.SOURCE_*` for nine sites, literals 9–12 in `Provider` for four; `Url.java` holds the
  nine old sites' URLs. Harmless; §4.3 gives the direction without demanding a migration.
- The MVP interface package (`interface_pacatge`, 1 070 lines) and Moxy are unrelated to sources and not worth touching
  for this goal.

## 8. What not to do

- No new `BookSource` contract, bridge or value-type layer "in advance" (§12.2 already decided this; the candidates do
  not need it, and a second contract would be two active implementations of one thing).
- No data-driven site descriptions (JSON/YAML of selectors). The first DLE POST sort, label-keyed row or shuffled
  mirror list breaks it, and the engine would grow an interpreter nobody can test.
- No JavaScript engine for players; port codecs (there are two known ones among the candidates, both small).
- No POJO redesign now (`time` as seconds, numeric rating): it touches Room, Gson persistence and backups; do it as
  T12 when a screen needs it, with a migration.
- No per-site threads, caches or clients; the shared ones are the point.
- No Kotlin, Compose, coroutines or modules; the leverage is in `Html`, not in the language.

## 9. Suggested order

1. The free wins: the `book()` rule and the `rawCards`/logging fix (§4.2, §3.1), shared fixture helpers, the DLE sort
   builder, `Json`, `JsonPage` factory, signed-XSPlayer helper (§4.4), the manifest guard test and the contract
   invariant test (§4.3, §4.5). Each a small diff under the existing tests. One or two days in all.
2. First candidate, Aknigi24 (plain HTML, inline chapter JSON), then Audiokniga-online.ru (DLE) and Lis10book (JSON):
   three slices that exercise `Html`, `Dle` and `Json` without new engine work. The acceptance of the preparation is
   not a line count (a ceiling only rewards omitted features) but: *every feature of the §9.2 checklist the site has is
   implemented, and no file outside `source/`, the `Provider` row, the manifest and the tests changed.*
3. With three fresh adapters as evidence, design and add the field mapper (§4.1); migrate Poleknig and AudioknigaLife
   under their fixture tests; make them the howto's examples and add a 40-line adapter skeleton to the howto.
4. Before Biblus: the refresh-by-count test and fix (§5.5). Before the 14th row: the two constants (§5.1–5.2).
5. §4.6 (OkHttp) alone, before the registry doubles; the shared-MP3 download work before Golosknig; the HLS export
   probe before Audiokniga.one.

Each step is a separate, reviewable diff; none changes a stored format, a preference key, a `sourceId` or a URL the
library has saved.

## 10. Second opinion

A draft of this document was reviewed by Codex (gpt-6.1-sol, read-only) against the code; its findings were checked
and the document changed where they held:

- Taken: the mapper (§4.1) must carry joined names and attribute fallbacks and must never return a null description;
  the sort round-trip invariant was wrong for terminal destinations (Top-100); the OkHttp port touches six adapters
  through Jsoup's `Connection` and must respect `BookPages` sharing; ordinal menu ids collide with the period items;
  reading identity through adapter methods inside the enum constructor is risk for little gain; the missing DLE
  builder, the signed-XSPlayer helper and the `JsonPage` factory; `Html.cards` is silent; the `parseBook` baseline is 9
  adapters, not 11; a fixture base class would mostly remove assertions; signed-URL refresh comes before the two
  deferred sites; line-count acceptance criteria reward omissions; health-based reordering overrides the user's order.
- Not followed: the suggestion to drop the mapper from the list. It stays, ranked third and sequenced after three
  fresh adapters exist, because the setter chains are measurably the largest repeated block per site (27–56 calls),
  and the reference app's adapters show the same ~45 % share of record-building boilerplate under a different language.
