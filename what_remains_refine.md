# What remains and what to refine before adding new sources

Date: 31 October 2026. Basis: `master` at `e1eadf1` (after the source-engine work in [tasks/plan.md §12](tasks/plan.md),
[tasks/todo.md](tasks/todo.md), [tasks/open-issues.md](tasks/open-issues.md)), the candidate study
[docs/new-audiobook-sources.ru.md](docs/new-audiobook-sources.ru.md), and a comparison with
[smold22/AudioBook](https://github.com/smold22/AudioBook) (`source-api`, `source-extra`).

This is an analysis and a proposal, not a change: no code was modified. Every number below was measured on the
current tree (line counts, grep for call sites); where something is a judgment or an estimate it says so.

---

## 1. Verdict in brief

**The architecture is ready; the per-site cost is not yet low.** The preparation reached its structural goal: all
13 sites are `SourceParser` adapters, `Provider.parser` is mandatory, and the models, presenters, search, player and
downloader never branch on a site (verified: outside `source/` only the documented screen features and one
undocumented switch name sites — see W5). A site of a known kind can be added without touching shared flows.

What has not changed is the **volume of code per site**. An adapter written fresh on the new contract is still
~460–515 lines (Uknig, AknigXyz, AudioknigaLife, AudioknigiFun — including optional features such as people
indexes, multi-page series and comments), plus ~400–500 lines of hand-written tests and seven registration touch
points. The bottleneck moved from *architecture* to *repetition*: every adapter re-implements the
same field extraction three times (card, page, deep-link card), the same DataLife Engine (DLE) sort form, the same
"what kind of listing is this URL" regexes, and the same test assertions.

Ten candidates are planned. Half of them (Audiokniga.one, Audiokniga-online.ru, Slushat-knigi, Audioknigi.Pro,
Bookish) are DLE sites, like five of the existing thirteen. So the cheapest route to "many sources" is not another
engine layer but a **small toolkit that turns the repeated parts into data**:

| # | Refinement (detail in §5) | New shared code (est.) | Saved per new site (est.) |
|---|---|---|---|
| A0 | A minimal first release per site (core features first, optional ones later) | none (a rule) | the largest single lever |
| A1 | Shared contract assertions + golden files for adapter tests | ~250 lines test | ~200–300 test lines |
| A2 | DLE kit: one sort-form builder and category learner; delete the dead helper | ~+60 / −80 lines | ~40–80 lines per DLE site |
| A3 | Label/text/link helpers now; `Fields` (declarative extraction) grown in the first candidates | ~40 → ~150 lines | ~100–150 lines once proven |
| A4 | Registration loose ends (download folder, manifest check, guide) | ~40 lines | 1 silent bug, 2 touch points |
| A5 | "Unexpected page structure" signal instead of a silent empty page | ~40 lines | maintenance at 20+ sites |

Together a few hundred lines of shared code and tests. The savings column is a judgment, not a measurement: the
first two integrations (§7) must measure adapter, helper, fixture and test lines before the toolkit grows further.
Everything in tier A is additive or guarded by the existing 612 tests; nothing rewrites working adapters.

---

## 2. What the engine does well (keep it)

These are real strengths, several of them better than the comparison project; refinements below must not erode them.

1. **One contract, capabilities as defaults.** [SourceParser.java](app/src/main/java/com/fanok/audiobooks/source/SourceParser.java)
   has 7 required methods and ~35 defaults meaning "the site has no such feature"; the UI hides what an adapter does
   not declare. A new site capability is a new default method, not a branch.
2. **Fetch and parse are separate.** Models fetch through `parser.fetch`, adapters parse a `Document` in pure static
   functions, so every site is testable from saved pages. `JsonPage extends Document` lets JSON sites use the same
   path with zero model changes.
3. **Strict failure semantics.** A challenge or HTTP error is an exception, `EMPTY` differs from `ERROR`,
   `ProviderPage.of(items, hasMore, rawCards)` keeps paging when all cards were filtered. The comparison project
   turns every failure into an empty list (`catch (_: Exception) { emptyList() }` in most sources), so a blocked site
   looks like a site without books. This distinction is what makes `SourceProbe`, `RouteOffer` and per-site routing
   possible.
4. **Network policy by host, not by site code.** `hosts()`, `referer()`, `cookies()`, `postsPages()`,
   `mediaHeaders()` and `AppProxy`'s per-host route keep proxy/Referer/session concerns out of adapters' logic.
5. **One book page per opening** (`model/BookPages`), description shown before the other-narrations probe, request
   counts measured equal or lower than before (§12.9).
6. **Bounded fan-out by product decision.** `Sources.MAX_ACTIVE = 13` bounds each search, "on other sites" lookup
   and health check however many sites are registered, so the number of registered sites alone does not grow the
   fan-out. The bound is per operation, though: overlapping operations (a search, a probe, a book opening) add up,
   and the "on other sites" lookups block a thread each without a cancellation hook
   ([OtherSourceModel.java:87](app/src/main/java/com/fanok/audiobooks/model/OtherSourceModel.java:87)). T09/T10/T11
   can stay deferred, but overlapping load is worth one measurement (C-tier).
7. **Reusable players.** `Players` covers Playerjs (inline/URL, folders, mirrors, obfuscation), generic player-script
   JSON and XSPlayer. In the comparison project Playerjs alone appears in 11 of 30 sources and `playerInit` in 4,
   so this toolkit already covers most candidates' audio with one line in `tracks`.
8. **Sorts as data** (`source/Sort`, `sorts(url)`, `sortUrl`) with 111 parity tests.

---

## 3. Where it does not scale yet

### W1. Field extraction is imperative and triplicated

Each adapter extracts the same ~10 fields (title, URL, cover, author+link, reader+link, genre+link, series+link,
duration, description, rating) in three places: listing cards, the book page (`description`), and the deep-link card
(`book`). Java makes each field 4–8 lines of null checks.

- All 13 adapters override `book()`, although the default (`BookPOJO.of(description(...))`) is essentially what the
  four newest adapters do (AknigXyz adds one rating line). The nine ported adapters keep a separate `parseBook` of
  52–148 lines each: **827 lines** that largely re-parse what `parseDescription` already parsed. They are duplication
  *candidates*, not dead code: some carry fields the default drops (Akniga's card has views, while `BookPOJO.of`
  deliberately leaves the rating out), so each needs a field-by-field comparison before replacement.
- Parser code per adapter: 133–157 lines in the four adapters written on the contract, 202–601 lines in the ported
  ones.
- The guide names [Poleknig.java](app/src/main/java/com/fanok/audiobooks/source/Poleknig.java) as the "short complete
  example", but it contains both legacy duplicates (`parseBook` at line 286, a second card parser `parseSearch` at
  line 362 for other-source hits). The template a new site is copied from teaches the copy-paste the preparation
  was meant to remove.
- Helpers are re-written privately: `AknigXyz` has its own `cards`, `lastPage`, `text` (duplicates of `Html`), plus
  `value(row)`/`href(row)` for "Label: value" rows; `AudioknigaLife`, `AudioknigiFun`, `Uknig` each have a private
  `text`. "Label: value" metadata rows are the single most common pattern in the candidates (Aknigi24 `.book-meta p`,
  Lis10book `.bmeta__row`; 14 of 30 sources in the comparison project have such a helper), and the toolkit has none.

### W2. DLE knowledge is scattered, and one shared helper is a trap

DLE is the dominant engine: 5 existing sites (Audioboo, BazaKnig, AknigXyz, AudioknigiFun, AudioknigaLife) and 5 of
the 10 candidates. Yet:

- The sort form with session keys (`dlenewssortby`, `dledirection`, `set_new_sort=dle_sort_<key>`,
  `set_direction_sort=dle_direction_<key>`) is implemented twice, near-identically:
  [AknigXyz.sortedUrl](app/src/main/java/com/fanok/audiobooks/source/AknigXyz.java:217) and
  [AudioknigiFun.sorted](app/src/main/java/com/fanok/audiobooks/source/AudioknigiFun.java:190). Both emit the same
  four form fields; they differ around them (AknigXyz resets the page to 1, AudioknigiFun keeps it and also knows a
  `selection` key besides `main`/`xfsearch`), which is site policy and should stay in the adapters.
- The "a plain listing must still be sent with an explicit sort, or the session's last sort leaks in" rule is also
  implemented twice (`AknigXyz.fetch`, `AudioknigiFun.fetchUrl`), and it is exactly the trap the candidate study
  describes for Audiokniga-online.ru and Bookish.
- Category ids are learnt from the genre page's sort form twice
  ([AknigXyz](app/src/main/java/com/fanok/audiobooks/source/AknigXyz.java:67),
  [BazaKnig](app/src/main/java/com/fanok/audiobooks/source/BazaKnig.java:110)).
- The one helper in `Dle` meant for this, [Dle.sortedListing](app/src/main/java/com/fanok/audiobooks/source/Dle.java:88),
  is **dead code** (no production caller) and hard-codes `dledirection=desc` and `set_direction_sort=desc` — wrong
  for the current DLE keys (`dle_direction_main`) and for the A–Я title sort. The next DLE adapter will reach for it
  first.

### W3. The URL is the listing's whole state — clever, but every site re-derives it

A listing URL carries the sort, period, genre, page, duration bound and even "POST this" (a `dlenewssortby` query
makes `Dle.fetch` POST; audioboo's `xs_value` is stripped before the request). This is a good trade: one string is
the back-stack key, the saved state, the history entry and the deep link, with no new value types.

The cost is that each adapter answers "what kind of listing is this?" with its own regexes and substring tests in
up to 11 methods (`sorts`, `sortUrl`, `genreOf`, `inGenre`, `pageUrl`, `periods`, `periodOf`, `withPeriod`,
`cardGenre`, `withDuration`, `listsDuration`) and needs a 150–330-line `*MenuTest` to pin them. Substring tests are
brittle (`Poleknig.sorts` treats any URL containing `serie`, `author` or `reader` as a people page). Rewriting the
state into typed query objects would touch every adapter, presenter and back-stack tag — too expensive. Two small
helpers for the two shapes the candidates use cover most of it (B2).

### W4. Tests grow linearly with hand-written golden values

Per site: an adapter test of 230–300 lines (mostly `assertEquals("Боталова Мария", first.getAutor())`) and a menu
test of 150–330 lines, plus a row in `BookPageFixtures`. Ten new sites mean ~4,000–5,000 more lines of assertions
that encode the same invariants differently each time. There is no shared check that every adapter honours the
contract (absolute URLs on the site's host, non-empty playable tracks, `genreOf(inGenre(x, g)) == g`, sorts that stay
on the host, a "last page" fixture with `hasMore == false`).

### W5. Registration has seven touch points; one is undocumented and fails silently

Adding a site today means: the adapter; a `Provider` row with 10 positional arguments; constants either in
`Url.java` (old sites) or in the adapter (new sites) — two conventions; the deep-link host in `AndroidManifest.xml`
(no test checks it); former hosts in the global table [Url.HOST_ALIASES](app/src/main/java/com/fanok/audiobooks/Url.java:105);
a fixture row in `BookPageFixtures`; and a case in
[DownloadStorageHelper.getSourceName](app/src/main/java/com/fanok/audiobooks/util/DownloadStorageHelper.java:832).

The last one is not in the guide, and forgetting it is silent: the new site's downloads land in the generic
`Audiobooks` folder (§12.8 already found this bug once for eight sites). It is one of two site switches left in
shared code; the other, [OtherSourceModel.label](app/src/main/java/com/fanok/audiobooks/model/OtherSourceModel.java:40),
keeps the five oldest sites' historical labels and falls back to `Provider.title` — harmless for new sites. The genre
vocabulary check (`Genre`, `names.tsv`) is a further, documented step. Former-host aliases are optional and rare.

### W6. A changed layout looks like "no books"

When a site changes its markup, the card selector matches nothing and the page is `EMPTY`. In search that is
indistinguishable from "nothing found"; `SourceProbe` reports it as `EMPTY` too. At 13 sites a developer notices; at
20+ sites markup drift becomes the main maintenance cost, and the app has no signal that separates "site changed" from
"no results" or "blocked". (The izibuk/otrub selector work, §12.10, is the same problem handled by hand.)

### W7. Media gates for some candidates

- **Signed MP3 URLs and downloads.** Biblus (`md5`+`expires`), Golosom, Audiokniga.one (signed HLS key) and possibly
  Audioknigi.Pro serve expiring URLs. Playback refreshes chapters on 401/403/410 (`refreshChapters`, `BookPages`
  TTL 60 s), but the download path has no re-resolve: a download queued or resumed after expiry fails. This is the
  only media change the first seven candidates need.
- **One MP3 with many clip chapters** (Golosknig: 64 chapters, one 593 MB file) needs T14–T17 (shared resource
  identity, download, deletion, export). That is a large, data-migrating change for one candidate.
- HLS itself is fine: `DownloadIds.isHls` and the player infer HLS from `.m3u8`, which Audiokniga.one's manifests have.

### W8. Small debts that a newcomer trips over

- `listsDuration` defaults to `true` ([SourceParser.java:117](app/src/main/java/com/fanok/audiobooks/source/SourceParser.java:117)):
  a site whose cards have no duration and forgets the override gets a duration filter that hides every card.
- Toolkit pieces live in `model/`: adapters import `OtherSourceModel.Hit/query/sameBook`, `ComentsModel.Parsed/nest`,
  `OtherArtistModel.otherReaders`; `SourceParser` itself imports `model.OtherSourceModel`. A new-site author has to
  know to look there.
- A new kind of sort needs an `R.id` in `res/values/ids.xml` although the menu is built in code
  (`BooksFragment` adds items by `sort.menuId`); the id could be derived from the enum.
- `SearchSession.MAX_PARALLEL = 13` repeats `Sources.MAX_ACTIVE = 13` instead of referring to it.
- Dead code: `Dle.sortedListing`, `AudioModel.PLAYERJS_FILE` ([AudioModel.java:35](app/src/main/java/com/fanok/audiobooks/model/AudioModel.java:35)).
- `Sort` carries site-named rows (`knigoblud_today`, `abmp3_top100` strings and ids); harmless, but new rows should
  get neutral names.

---

## 4. Comparison with smold22/AudioBook

**Shape.** A pure-JVM Gradle module `source-api` (one 87-line file: `Book`, `AudioTrack`, `BookDetails`, `Genre`,
an 8-method `AudiobookSource`, a `SourceRegistry`) and `source-extra` with 30 sources of 119–254 Kotlin lines each,
plus `HttpUtil` (OkHttp client, retries, `postForm`, Playerjs JSON, `estimateCount`) and `JsRuntime` (Rhino, for two
obfuscated sites). The app registers sources in a list in `AudioBookApplication`.

**Why their sources are shorter** — mostly not better abstraction:

| Factor | smold22 | This app |
|---|---|---|
| Feature surface | home, search, details, genres, series | + sort menus, periods, duration filter, genre-scoped rankings, people indexes and search, comments, other narrations, other sites, media headers/cookies, per-site proxy route |
| Failure handling | errors become empty lists | challenge/HTTP/timeout are distinct statuses |
| Language | Kotlin (`?.`, `mapNotNull`, data classes) | Java 8 null checks |
| Paging | `List<Book>`, no `hasMore` | `ProviderPage` with real `hasMore` and raw card counts |
| Details | one `getBookDetails` = book + description + tracks | description and tracks share one fetch via `BookPages` |

So the comparison confirms that the extra code here buys real features; the part worth cutting is the Java
verbosity of field extraction (A3) and the repetition (A2, A1), not the contract.

**Worth adopting**

- **Label-row and schema.org extraction as shared tools.** `metaP(item, "Автор")` / `bmetaValue(doc, "Автор")`
  appear in 14 of 30 sources; JSON-LD or `itemprop` in 11 (Lis10book reads `application/ld+json` `Audiobook` with
  `author.name`, `readBy.name`, `duration`, `image`). Here: `Fields` label rows (A3) and an optional schema.org preset
  (B5).
- **A live check over all sources** (`AllSourcesCheckTest`: home → first book → tracks, genres, search, cover probe,
  4 in parallel, results printed). The owner did exactly this by hand for §12.8; it should be a reusable, opt-in tool
  (B6).
- **Protocol notes for candidates.** Their `Aknigi24Source` reads `#player-data` (`chapterBase` + `chapters[{n,d}]`);
  `Lis10bookSource` says the site moved to Next.js and uses HTML `.mcard` cards, `/api/search?q=` and
  `/api/p/{slug}` — the study's `/api/books?…` routes may already be stale. Re-verify each candidate's routes when
  its fixtures are captured, not from the study alone.

**Not worth adopting**

- Swallowing errors into empty lists; the `?ref=host` URL suffix as a way to carry a Referer to a CDN (here `hosts()`
  plus `mediaHeaders()` do it properly); a JavaScript engine (the plan rightly rules it out: Audioknigi.Pro's codec
  and Golosknig's AES are small pure-Java decoders); splitting sources into a separate Gradle module now (`Sort`
  uses `R`, `SiteHttp` sits on `AppProxy` — a big move for little gain at this size).

---

## 5. Proposals

Ordered by leverage. Costs are estimates of changed production+test lines; "risk" is the chance of breaking existing
behaviour.

### Tier A — before the first new source

#### A0. A minimal first release per site

*Why:* the cheapest code is the code not written yet. The ~460–515 lines of the newest adapters include optional
capabilities (people indexes and people search, multi-page series, comments, recommendations, other narrations,
other-source tuning, genre-scoped rankings with periods). Every one of them already has a default that hides the
feature, so a site can ship without them and gain them later, one capability at a time, each with its own fixture.

*How:* define the first release of a new site as: catalog with real paging, book search, genre list, book page
(description + card), tracks, and the sorts the site's server really offers site-wide. Everything else is a
follow-up only when the site has it and users ask. Put this list in the guide and in each candidate's task.

*Cost:* none. *Risk:* none — the UI already hides undeclared features. *Saves:* by judgment a third to a half of a
first adapter's lines, and the matching tests and fixtures.

#### A1. Shared contract assertions and golden files for adapter tests

*Why:* W4; it also guards A2/A3 and any later legacy clean-up.

*How:* one parameterized JVM test, `source/SourceContractTest`, driven by a small per-site case table next to the
fixtures. Start with the new sites and the two adapters A2 touches; convert the other existing sites only
opportunistically.

```
# app/src/test/resources/fixtures/aknig-xyz/cases.tsv
# kind     file                    url                                   page
listing    listing-index-p1.html   https://s2.aknig.xyz/                 1
listing    listing-author-last.html https://s2.aknig.xyz/xfsearch/...    3
search     search-p2.html          https://s2.aknig.xyz/index.php?...    2
genres     genres.html             https://s2.aknig.xyz/genres.html      1
book       book.html               https://s2.aknig.xyz/14731-...html     -
```

For each case it (1) checks **universal** invariants — items present unless the case says `empty`; every book URL
is absolute and `Provider.ofUrl(url) == provider`; names non-blank; a case marked `last` has `hasMore == false`; a
book page has a title; inline tracks are non-empty http(s) URLs — and **per-case expectations** written in the table
where behaviour is legitimately conditional: whether a listing `hasSort` (a TOP-100 page has no menu), the expected
`genreOf` of a genre case, and genre round-trips only for sites whose genre sorts need no learnt state (AknigXyz
stays site-wide until it has learnt the category id — that needs explicit setup, as its own test does); and (2)
renders the result as stable text (one line per card: `name | url | photo | author <url> | reader <url> | genre |
series | time`) and compares it with `<file>.golden.txt`. A missing golden file is written and the test fails with
"review and commit". Generated goldens record behaviour *including bugs*, so they must be reviewed against the page,
not rubber-stamped.

Limits: the test exercises the pure `listing`/`searchResults`/`description` path, not production overrides of
`loadListing` that fetch (ABMP3's random book, Izibuk's PDA listing); `tracks` that fetch a playlist (Playerjs by URL)
run only through the adapter's pure parse function on a saved playlist, or are skipped.

*Cost:* ~250 lines once. *Risk:* none (tests only). *Saves:* most of the hand-typed golden values of a new site's
adapter test; site-specific tests remain for real quirks (DLE category learning, a codec, paging rules).

#### A2. One DLE kit instead of three partial ones

*Why:* W2; five DLE candidates.

*How:* move only what is truly the engine's into `source/Dle` and delete the dead helper:

```java
/** The DLE sort form as a query that fetch POSTs: dlenewssortby, dledirection, set_new_sort=dle_sort_<key>,
 *  set_direction_sort=dle_direction_<key>; key "main", "cat_<id>", "xfsearch", ... The caller decides the page. */
public static String sortForm(String base, Order order, String key)   // the shared core of both builders
/** Genre root → DLE category id, learnt from the genre page's sort form (#set_new_sort). Thread-safe. */
public static final class Categories { String id(String root); Document learn(String root, Document page); }
```

AknigXyz and AudioknigiFun then call `sortForm` from their own builders and keep their own page normalization,
session policy (which listings go out with an explicit date sort) and search termination (AknigXyz's capped
"найдено N" rule); their 111 menu tests pin the result. BazaKnig's learner can move to `Categories` when it is next
touched. BazaKnig and AudioknigaLife use the `/f/` filter variant and stay as they are. Pager and search-end helpers
join `Dle` only when a second site needs the same rule.

*Cost:* ~+60 / −80 lines. *Risk:* low (two adapters, fully pinned by tests). *Saves:* the form, key and category
code (~40–80 lines) in each DLE candidate and, more important, the wrong first guess — the dead helper's hard-coded
keys. Audiokniga-online.ru's lower-case `desc` and `dle_direction_main`, and Bookish's unproven sort, then become
parameters of one function instead of new implementations.

#### A3. Extraction helpers now, `Fields` grown in the first candidates

*Why:* W1. Declarative extraction is the largest potential cut in per-site code, but real pages need transformations
beyond selectors: AknigXyz keeps *all* authors' text after "Автор:" while linking the first author
([AknigXyz.java:477](app/src/main/java/com/fanok/audiobooks/source/AknigXyz.java:477)); AudioknigaLife computes the
duration from the playlist JSON and strips promotional paragraphs from the description
([AudioknigaLife.java:434](app/src/main/java/com/fanok/audiobooks/source/AudioknigaLife.java:434)). So build it in
two steps.

*Step 1 (now, ~40 lines):* add to `Html` the helpers every adapter re-writes — `labelled(scope, rowsCss, label)`
returning the row, `afterColon(row)` (all text after `Label:`), `firstHref(row)`, `iconText(scope, iconCss)` — and
replace the private copies in AknigXyz/AudioknigaLife/AudioknigiFun/Uknig when they are next touched.

*Step 2 (inside the first two candidates):* grow a `Fields` value from what Aknigi24 and Audiokniga-online.ru actually
need, and promote it to the toolkit only if both fit. Sketch: one immutable value per layout, built fluently once as
a `static final`, that fills `BookPOJO` from a card or a page, the page variant reusing the existing
`BookPOJO.getDescriptionPOJO()`; anything a selector cannot express is a few lines of Java applied to the returned
`BookPOJO`:

```java
/** Where one layout keeps a book's fields; null for a field the layout lacks. A "@Label" selector reads the row
 *  under rows() whose text starts with Label: name = all text after ':', link = the row's first link. */
public final class Fields {
    public static Fields title(String css);
    public Fields link(String css), cover(String css), author(String css), reader(String css), genre(String css),
                  series(String css), time(String css), about(String css), rating(String css), rows(String css);
    @Nullable public BookPOJO book(Element scope);                       // null without a title: Html.cards skips it
    public ProviderPage<BookPOJO> cards(Document doc, String cardCss, boolean hasMore);
    public DescriptionPOJO page(Document doc);
}

// Poleknig's 70-line card parser would become:
static final Fields CARD = Fields.title("a.book-title").cover("img").author(".book-author a")
        .reader(".book-reader a").genre(".book-genres a").series("a[href*=/series/], .book-series a")
        .time(".book-duration");
// AknigXyz's label rows:
static final Fields CARD = Fields.title(".main-news-title a").cover(".main-news-image img")
        .rows(".main-news-info > div").author("@Автор").reader("@Читает").genre("@Жанр")
        .series(".main-news-ck a").about(".main-news-text");
```

Covers go through `Html.imgSrc` (lazy `data-src`/`data-original`, placeholders skipped), links through `absUrl`, so
relative URLs and lazy images — two recurring candidate notes — are handled once.

*Cost:* step 1 ~40 lines; step 2 ~150 lines + a ~60-line test, paid inside the first candidates. *Risk:* none for
existing sites (additive; legacy adapters are not migrated). *Saves (if step 2 is promoted):* ~100–150 lines per HTML
candidate, and the next markup change becomes a data edit.

#### A4. Close the registration loose ends

*Why:* W5. Small, mechanical, prevents a silent bug.

- **Download folder** becomes data: a `folder` value on the `Provider` row (existing names kept verbatim, so existing
  downloads stay where they are), or a default derived from the adapter's class name with the nine legacy names as
  explicit values. Removes the only site `switch` in shared code that fails silently.
- **Former hosts** (optional, low immediate value): could move from `Url.HOST_ALIASES` to the site, with
  `Url.canonical` building its map from the registry lazily (mind the rule that an adapter must not touch `Provider`
  while the enum initialises). New sites rarely have aliases; do it only when the table is next edited.
- **Manifest check:** a JVM test that reads `AndroidManifest.xml` and asserts every `Provider` host (and former host)
  has a deep-link `<data android:host>`.
- **One convention for constants:** new sites keep `SERVER`/`INDEX`/… in the adapter (as Uknig and later do); the old
  `Url.*` constants can migrate opportunistically.
- **Guide:** in [docs/howto.md](docs/howto.md) "Adding an audiobook site", point to Uknig (plain HTML, written on
  the contract) and the post-A2 AknigXyz (DLE, POSTed sorts) as templates instead of Poleknig, list every touch point, and say that `book()`,
  `otherSource*` and `searchResults` usually need no override.

*Cost:* ~40 lines + guide edits. *Risk:* low (folder names pinned by a test).

#### A5. "Unexpected page structure" instead of a silent empty page

*Why:* W6. No single check can prove "the layout changed" (a kept wrapper with renamed cards still looks empty; a
missing wrapper may be an unknown challenge or an empty-result template), but two cheap signals catch most drift:

1. **Extraction failures are counted, not swallowed.** [Html.cards](app/src/main/java/com/fanok/audiobooks/source/Html.java:67)
   drops a card that throws without a trace (unlike `Html.each`, which counts and logs). When cards matched but none
   survived (`rawCards > 0`, no items, and no site filter explains it), report `ERROR "unexpected page structure"`.
2. **Positive and empty markers.** A site may declare a marker every valid page of a kind has (DLE: `#dle-content`)
   and, for search, the site's "nothing found" marker. Marker missing, or no cards and no empty marker → the same
   `ERROR` instead of `EMPTY`.

Pilot it where the cost of a false alarm is lowest — `SourceProbe` (first catalog page) and the daily `SourceHealth`
run — and only then in listings and search. No new UI status is needed; the sources dialog shows the message.

*Cost:* ~40 lines + markers declared adapter by adapter. *Risk:* low on the probe; check each marker against all of
the site's fixtures before using it in listings.

### Tier B — together with the first candidate that needs it

#### B1. Re-resolve signed media URLs for downloads

Gate for Biblus, Golosom, Audiokniga.one (and Audioknigi.Pro if its links expire). Smallest useful version:
re-run the adapter's `tracks` right before enqueuing a download and once on a 401/403/410 download failure, matching
chapters by the same rules `refreshChapters`/`syncChapters` already use. For progressive MP3 the download id
(`v1/<book>/chapter/<n>`) does not depend on the URL, so the Media3 index stays valid. HLS is different: its id
includes the stream's host and path ([DownloadIds.java:75](app/src/main/java/com/fanok/audiobooks/download/DownloadIds.java:75)),
so a renewed manifest on another CDN host is a new resource, and an expired AES key inside a still-valid manifest is
not fixed by a fresh top-level URL. Treat "MP3 URL refresh" (Biblus, Golosom) as T15-lite and "HLS manifest/key
renewal" (Audiokniga.one) as its own task with a device test; a stable chapter id alone does not prove that resuming
a partial download is safe. The full T14–T18 stays deferred.

#### B2. Two sort-scheme helpers for the URL-state

Candidates use two shapes: a **query parameter** (Aknigi24 `?sort=rating&dir=asc` site-wide and inside a genre;
Lis10book `sort=popular|rating`; Biblus `?period=week|month`) and a **path suffix on a genre root** (Poleknig
`/genres/<id>/best`, Audiokniga.one `/<genre>/popular/`, Golosknig `/genre/<g>/views/`). A helper per shape,
configured with the genre-root pattern and a `Sort → value` map, implements `sortUrl`, `genreOf` and `inGenre`
consistently and leaves the adapter to declare data. *Cost:* ~80 lines. *Saves:* ~30–60 adapter lines and most of
the per-site menu test (A1 checks the round-trip generically).

#### B3. Safer defaults in the contract

- Give `searchResults` a default of `listing(page, page.location(), pageNo)`: most sites' search page is a listing.
- Make the duration filter robust to a forgotten `listsDuration` override — for example, hide the duration chip
  for a listing whose loaded cards have no readable duration at all — or document the override as mandatory in the
  guide and A1 (assert: if `listsDuration(url)` then some card of that fixture has a duration). The test assertion is
  the cheapest version.

#### B4. JSON helpers out of `Players`

`Players.string/arrayOf/seconds/object` are general JSON accessors living in the player class. A tiny `net/Json`
(same functions, plus a dotted path `Json.at(o, "books.items")`) keeps a JSON adapter like Lis10book short and
readable. Move, don't grow.

#### B5. schema.org preset for `Fields`

When a candidate exposes JSON-LD or microdata (Lis10book, Golosom/Audiopolka by the comparison project; Izibuk and
Poleknig already use `itemprop`), a `Fields.schemaOrg()` preset reads `name`, `author.name`, `readBy.name`, `image`,
`genre`, `duration` (ISO-8601 `PT7H…`), `description`. ~50 lines; build it with the first candidate that has it, not
before.

#### B6. An opt-in live check and fixture recorder

A JVM test, skipped unless `-Dlive=1` (never in CI, no emulator, no `adb`), that for each provider (or one named
provider) fetches listing pages 1–2, a search, the genre list, the first book and its tracks, and sends a 1 KB
`Range` request to the first media URL — the comparison project's `AllSourcesCheckTest`, but through the app's own
`SiteHttp`, adapters and routes, printing a status table like `SourceProbe`. With `-Drecord=1` it also saves every
page it fetched under `fixtures/<site>/` and appends `cases.tsv` rows, which removes most of the manual work of
capturing fixtures for a new site (A1 then generates the golden files); recording and asserting stay separate runs.
Feasibility: `SiteHttpTest` already does real local HTTP in the JVM. Limits: without an Android context `AppProxy`
stays off, so the check runs on the direct route only (a proxy route needs an explicit JVM configuration), and a 1 KB
range proves neither HLS keys, seeking, downloads nor Android's TLS — those remain device checks. The property must
be passed through `systemProperty` in `app/build.gradle`. *Cost:* ~150 lines.

### Tier C — only when the related screen or candidate is touched

- **Legacy duplicates** (−827 lines of `parseBook`, the `parseSearch`/`otherSourceHits` copies): delete per adapter
  once A1's golden files prove the default `book()` gives the same deep-link card, or keep the difference as an
  explicit two-line override. Not a prerequisite for new sources.
- **Toolkit out of `model/`:** move `OtherSourceModel.Hit/query/sameBook/samePerson/withoutAuthor` and
  `ComentsModel.Parsed/nest` to `source/` (mechanical, IDE refactor).
- **`Sort` without `R.id`:** derive `menuId` from the ordinal (e.g. `Menu.FIRST + 1000 + ordinal()`), drop the ids
  from `ids.xml`; a new sort kind is then one enum row plus its strings. Neutral names for new rows.
- **`SearchSession.MAX_PARALLEL = Sources.MAX_ACTIVE`;** delete `AudioModel.PLAYERJS_FILE`.
- **T11** ("on other sites" publishes partial results instead of `Single.zip`): worthwhile UX, independent of new
  sources because fan-out is capped at 13.
- **T12** (metric semantics): before Lis10book at least keep its 10-point `livelibRating` out of `reting` or label
  its scale; views are not ratings.
- **T14–T17** (shared MP3 resource): only if Golosknig is chosen. The study's own suggestion — replace it with a
  candidate that needs no download-model change — is the cheaper path for now.
- **T09** (cancellable transport): not needed while fan-out is capped per operation. Measure once whether
  overlapping operations (search + "on other sites" + probe + book opening) pile up blocked threads; if they do, a
  shared concurrency limit in `SiteHttp` is the small fix, the transport rewrite still is not.

### Deliberately not proposed

- A new `BookSource` interface, a bridge, or value types for every concept — §12.2 rejected them for good reasons,
  and nothing found here changes that.
- Config-only sources (a whole site described in JSON/selectors). Players, codecs, DLE sessions and sort quirks
  differ too much; `Fields` + the DLE kit capture the repetitive 70% while Java keeps the rest honest.
- Replacing URL-as-state with typed query objects, a plugin/module split, a JS engine, Kotlin.

---

## 6. What a new source costs after the refinements (estimates)

"Now" is a hypothetical adapter on today's toolkit with the feature scope of the four newest adapters; "after"
assumes A0 (minimal first release), A2, A3 step 2 and the B items named in the row. Both are judgments to be replaced
by measurements from the first two integrations (§7).

| Candidate | Kind | Uses | Adapter lines now → after (est.) | Engine gate |
|---|---|---|---|---|
| Aknigi24 | HTML, GET sorts | `Fields` (label rows), B2 query sorts, `Players.playerScript` on `#player-data` | ~450 → ~160 | none |
| Audiokniga-online.ru | DLE | DLE kit, `Fields`, `Players.loadPlayerjs` (nested folders already handled) | ~480 → ~170 | none |
| Slushat-knigi | DLE | DLE kit, `Fields` (`data-src`), Playerjs with Referer (already default), `Sort.TOP_100` + one new row for TOP30 | ~450 → ~170 | none |
| Lis10book | JSON (re-verify routes) | `JsonPage`, B4 | ~400 → ~220 | T12-lite (rating scale) |
| Audiokniga.one | DLE + HLS | DLE kit, `Fields`, `Players.playerScript` | ~480 → ~200 | B1 (signed key), HLS device test |
| Biblus | HTML + XSPlayer | `Fields`, B2 query periods, `Players` XSPlayer-like tuple parse | ~450 → ~200 | B1 |
| Audioknigi.Pro | DLE + codec | DLE kit (POST search), `Fields`, small codec over `Players.decodeBase64` | ~520 → ~260 | B1 if links expire |
| Golosom | HTML + `App.playerInit` | `Fields`, `Players.playerScript` | ~430 → ~180 | B1 |
| Bookish | DLE | DLE kit; sorts hidden until the POST is proven | ~450 → ~170 | sort protocol unresolved |
| Golosknig | LiveStreet + AES | `Fields`, codec | ~500 → ~250 | T14–T17 (or replace) |

Tests per site after A1/B6: a `cases.tsv`, reviewed generated golden files, and only quirk-specific test methods.

---

## 7. Suggested order of work

1. **A0** written into the guide and the candidate tasks; **A4** (download folder as data, manifest check, guide
   templates and touch points).
2. **A1** harness with the shared assertions, first applied to AknigXyz and AudioknigiFun; then **A2** (`sortForm`,
   `Categories`, dead helper deleted) under those tests and the existing menu tests.
3. **A3 step 1** helpers; **A5** pilot in `SourceProbe`/`SourceHealth`.
4. **Two measured integrations:** Aknigi24 (HTML, query sorts → grows `Fields` and B2) and Audiokniga-online.ru
   (DLE → uses `sortForm`/`Categories`, a second user of `Fields`). Each is one adapter + `Provider` row + manifest
   line + fixtures/cases, in the A0 scope. Record for each: adapter lines, new helper lines, fixture count, test lines,
   files touched outside `source/`, request count per screen versus a similar existing site. **Gate:** promote
   `Fields`/B2 to the toolkit only if both fit without special cases; no file outside the adapter, its `Provider` row,
   the manifest and its fixtures changed except deliberate toolkit additions.
5. Then Slushat-knigi, Lis10book (B4, T12-lite), **B1** (MP3 refresh) with Biblus and Golosom, Audiokniga.one with
   its HLS renewal task and a device test, Audioknigi.Pro. Bookish after its sort is proven or with sorts hidden;
   Golosknig only with T14–T17 or replaced.

Each step is verified with `g.ps1 … :app:testDebugUnitTest`, one Gradle build at a time; device checks only in the
owner's single end phase, per the emulator rules in `CLAUDE.md`.

---

## 8. Independent review and what changed

The first draft of this document was reviewed against the code by Codex (gpt-6.1-sol, high reasoning, read-only).
Its findings were checked in the code before being accepted:

- **Accepted:** the per-site savings were unmeasured and partly assumed B-tier work (now §6 says so, and §7 gates the
  toolkit on two measured integrations); narrowing the first release (A0) is the biggest missed lever; a selector
  DSL needs transformations (AknigXyz's all-authors text, AudioknigaLife's duration and description clean-up), so
  `Fields` is grown in real adapters first (A3); `BookPOJO.getDescriptionPOJO()` already exists; universal
  contract invariants would reject legitimate behaviour (TOP-100 without a menu, AknigXyz's learnt category id), so
  A1 uses per-case expectations and reviewed goldens; the two DLE builders share the form but not the page/session
  policy, so A2 extracts only the form and the category learner; a wrapper check cannot prove a layout change, so
  A5 counts extraction failures, uses positive and empty markers and starts in the probe; the 827 `parseBook` lines
  are candidates, not removable as-is; `OtherSourceModel.label` is a second site switch (harmless default); the
  active-site cap is per operation; HLS ids include the host, so HLS renewal is separate from MP3 URL refresh; the
  JVM live check runs on the direct route only.
- **Kept, with a different emphasis than the reviewer:** declarative extraction stays a tier-A lever (Java's
  verbosity in field extraction is the main measurable difference from the comparison project), but it is built
  inside the first two candidates rather than ahead of them; A1 still covers existing sites, but only as they are
  touched, starting with the two adapters A2 refactors.
- **Open questions for the owner:** which optional features a new site's first release needs (A0 proposes a
  default); whether signed-media sites must support reliable offline downloads from day one (decides whether B1
  precedes Biblus/Golosom/Audiokniga.one); what cost and correctness numbers the first two integrations must meet
  before the toolkit grows further (§7 proposes what to record).

---

## 9. Loose ends carried over from the preparation

From [open-issues.md](tasks/open-issues.md), the ones that matter for new sources:

- **Downloads and chapter refresh were not verified on a device** — before the signed-URL candidates, verify a
  download and a `refreshChapters` on an existing site (owner's device phase, per the emulator rules).
- **Per-site proxy route with a real proxy** was verified only by JVM tests and UI. A new site starts on the proxy
  route (`PREF_DIRECT` stores the direct ones), so for a user whose proxy is on, the new site's first impression
  depends on that proxy working (the built-in server does not answer, §12.11; `RouteOffer` then offers the direct
  route). Worth one device check with "Свой прокси" and one site set to direct before announcing new sites.
- **`SourceHealth` daily run** was never observed (emulator reported a metered network). With 20+ sites it becomes the
  early-warning system for A5's "unexpected page structure"; worth one observed run.
- **Instrumented tests** (`connectedDebugAndroidTest`) were not run; they matter once B1 or T14+ touch downloads.
- The other known problems (permission dialogs on recreate, rewind label 10/30 s, mini-player after a removed book,
  site data typos) are independent of source integration.
