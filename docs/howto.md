# AudioBooks — developer guide

How to set up a toolchain, build, test, sign, and release AudioBooks, and how to run, inspect, and debug it on an emulator or device. For what the app does and how to install it, see the [README](../readme.md).

Commands are written for a POSIX shell (Linux, macOS, Git Bash on Windows) and run from the repository root. On Windows use `gradlew.bat` instead of `./gradlew`; `adb`, `sdkmanager`, and `emulator` work the same in any shell, with one exception noted under [Screenshots, UI, and input](#screenshots-ui-and-input).

## Contents

1. [Project at a glance](#project-at-a-glance)
2. [Source layout](#source-layout)
3. [Adding an audiobook site](#adding-an-audiobook-site)
4. [Setting up the toolchain](#setting-up-the-toolchain)
5. [Building](#building)
6. [Testing and lint](#testing-and-lint)
7. [Release signing](#release-signing)
8. [Continuous integration and releases](#continuous-integration-and-releases)
9. [Running on an emulator or device](#running-on-an-emulator-or-device)
10. [Logs, crashes, and app data](#logs-crashes-and-app-data)
11. [Network, proxy, and playback diagnostics](#network-proxy-and-playback-diagnostics)
12. [Memory and leaks](#memory-and-leaks)
13. [Performance](#performance)
14. [Verifying an APK](#verifying-an-apk)
15. [Troubleshooting](#troubleshooting)

## Project at a glance

| Item | Value |
|---|---|
| Application ID | `com.fanok.books` |
| Namespace (Java packages) | `com.fanok.audiobooks` |
| Gradle module | `app` |
| Language | Java (source level 8 with core-library desugaring), XML layouts |
| Build | Gradle wrapper 8.13, Android Gradle Plugin 8.13.2, JDK 17 |
| SDK levels | min 24 (Android 7.0), target 34, compile 36 |
| Native ABIs packaged | `arm64-v8a`, `armeabi-v7a`, `x86_64` |
| Main libraries | Media3 (ExoPlayer) 1.9, OkHttp 5.3, Jsoup 1.21, Room 2.8, RxJava 2, Moxy-X, Glide, Picasso |
| Debug-only tooling | LeakCanary 2.14, StrictMode violation logging |
| Entry points | `.activity.MainActivity` (launcher and Android TV), `.activity.SearchableActivity` (`android.intent.action.SEARCH`) |

The authoritative values are in [`build.gradle`](../build.gradle), [`app/build.gradle`](../app/build.gradle), and [`gradle-wrapper.properties`](../gradle/wrapper/gradle-wrapper.properties).

## Source layout

Java sources are under `app/src/main/java/com/fanok/audiobooks/`.

| Location | Responsibility |
|---|---|
| `App.java` | Application setup: built-in proxy, shared OkHttp client, image loader, crash handler, RxJava error handler |
| `Consts.java`, `Url.java` | Constants, source site URLs, built-in proxy settings |
| `activity/`, `fragment/`, `adapter/` | Screens, lists, and settings UI |
| `presenter/`, `model/`, `interface_pacatge/` | MVP layer (Moxy): presenters, models that hand each page to the site's adapter, view contracts |
| `source/` | One adapter per audiobook site (`SourceParser`) and the toolkit they share: `Html`, `Players`, `Dle`, `Sort` |
| `search/` | `Provider` (the site registry), multi-source search sessions, paged listings, filters, `SourceProbe` (availability check) and `SourceHealth` (its kept results; a daily check on unmetered networks) |
| `net/` | `SiteHttp` (page and JSON fetching, cookies, challenge detection), `AppProxy` (proxy, per-site route), cookie jar |
| `service/` | `MediaPlayerService` (playback, media session, notification, sleep timer), `ExoDownloadService`, playback error policies |
| `download/`, `util/` | Download tracking, storage and SAF access, deletion, Media3 data sources |
| `room/` | Room database `audioBooksDB`, DAOs, migrations; exported schemas live in `app/schemas/` |
| `policy/`, `android_equalizer/` | Parental control, equalizer |
| `app/src/main/res/` | Layouts (including `layout-television` and tablet variants), strings, `xml/preferences.xml` (settings), `xml/network_security_config.xml` |
| `app/src/test/` | JVM unit tests; saved site pages under `resources/fixtures/<source>/` |
| `app/src/androidTest/` | Instrumentation tests: Room migrations, downloads, player service, export |

## Adding an audiobook site

A site is one row in `search/Provider.java` and one adapter, `source/<Site>.java implements SourceParser`. Everything
else — catalog and search screens, the Order and filter menus, the multi-site search, "on other sites", the book page,
player, downloads, parental control, Referer and proxy routing — works through that interface and never branches on a
site. `source/Uknig.java` (plain HTML, Playerjs by URL) and `source/AknigXyz.java` (DataLife Engine, POSTed sorts)
were written on this contract and are the examples to start from; `source/Lis10book.java` is a JSON-API site and
`source/Golosknig.java` an encrypted XHR playlist of one whole-book MP3.

**First release of a site:** catalog with real paging, book search, the genre list, the book page (description and
chapters) and the sorts the server really offers. People indexes and search, series, comments, recommendations, other
narrations and periods come later, one at a time with their own fixtures, only where the site has them; their defaults
hide them meanwhile.

1. **Fixtures.** Save the pages the adapter reads under `app/src/test/resources/fixtures/<site>/`: listing page 1 and
   a later page, a search with results and one without, the genre list, a book page, and the playlist (HTML, JSON or
   the XHR answer). Tests never contact real sites.
2. **Adapter.** Six methods are required: `listing`, `pageUrl`, `genres`, `searchUrl`, `description`, `tracks`.
   Keep parsing in pure static functions (`parseListing(Document, int)`, ...) that the tests call on the fixtures.
   Every other method has a default meaning "the site has no such feature", and the UI hides the feature. Two defaults
   are real implementations, override them only when the site differs: `searchResults` parses the search page as a
   listing, and `book()` (a deep-linked book's card) is made of `description`; never write a second parser of the book
   page for it, add only a field the card has and the page lacks (AknigXyz's rating).
3. **Register.** Add the `Provider` row: server, title, `prefValue`, `prefKey`, a new unique `sourceId` (never reuse or
   renumber one: users' settings store it), the download folder (a new CamelCase name; never rename one: saved books
   are found by it), the drawer URLs (`null` for a section the site lacks) and `new <Site>()`. Keep the site's
   `SERVER`, `INDEX`, `GENRES`, ... as constants of its adapter. Existing users get the new site inactive, at the end
   of their list (`search/Sources`); the user switches it on. Add the host to the deep-link intent filter in
   `AndroidManifest.xml`; a site that moved gets its old hosts in `Url.HOST_ALIASES`. Add the book-page fixture to
   `BookPageFixtures.BOOK_PAGES`.
4. **Test.** `source/SourceContractTest` already checks every row without fixtures: the deep-link host, page 2 and
   every Order item on the site's host, a sorted listing keeping its menu, period round trips, the book search, every host of
   the site on its own proxy route, timeout and folder. The site's own test asserts what its fixtures show (cards, next page and last page, search,
   description, chapters, sort URLs); a JSON site loads its fixtures with `BookPageFixtures.json`. Then
   `./gradlew :app:testDebugUnitTest`. `source/LiveDumpTest` is an opt-in check of the real site, never run in CI:
   `LIVE_DUMP_DIR=<dir> LIVE_DUMP_SITES=<ROW> ./gradlew :app:testDebugUnitTest --tests '*LiveDumpTest'` writes what
   every feature yields (listings, sorts, periods, genres, people, search, book page, chapters, comments) to
   `<dir>/out/<ROW>.txt`, parsing saved copies of the pages, so two runs on different code can be diffed. Finally a
   short live check on a device: catalog, search, a book, playback, a download.

What the shared toolkit already covers; all of it is optional: a site whose markup, DLE template or protocol
differs writes its own lines or overrides the default, never a branch in the shared helper:

| The site has | Use |
|---|---|
| Book cards, genre rows, a pager | `Html.cards`, `Html.rows`, `Html.each` (one broken item is skipped and logged, never the page), `Html.lastPage`, `Html.text`, `Html.href` (both take a missing scope), `Html.imgSrc` |
| "Автор: ..." label rows, icon counters | `Html.labelled(scope, rowsCss, "Автор")` with `Html.afterLabel(row)` and `Html.href(row, "a")`; `Html.iconText` |
| A JSON API | `fetch` returns `SiteHttp.getJson(url)` (a `JsonPage`); the parse methods read `JsonPage.of(page)` with `Json.string`, `Json.arrayOf`, `Json.seconds`; fixtures are `JsonPage.parse(url, text)`. The body decides, not the Content-Type; an HTML anti-bot page answered with 200 is an error, not "no books" |
| A POST form, extra headers or cookies | override `fetch`; `SiteHttp.postForm`, `SiteHttp.connect(...)` with `SiteHttp.execute` and `SiteHttp.parse` |
| DataLife Engine | `Dle`: sort forms (`Dle.Order`, `Dle.MENU`, `Dle.sorted(listing, order, key)` POSTed by `Dle.fetch`, the genre key from `Dle.categoryId(page)`), `/page/N/` paging, search URL, news ids. Which listings go out with an explicit sort (so the session's last sort does not leak into a plain one) is the adapter's policy, see AknigXyz and AudioknigiFun |
| A player in a page script | `Players.playerScript` with `Players.Chapters`; XSPlayer `Players.xsPlaylist`; Playerjs `Players.loadPlayerjs` or `inlinePlayerjs` (folders, mirrors, obfuscated URLs) |
| A playlist fetched by XHR | `SiteHttp.body(SiteHttp.connect(url).referrer(bookUrl).header(...))` |
| Sorts | `sorts(url)` returns the Order menu as data (`Sort.menu`, `Sort.submenu`); `sortUrl(Sort, url)` the listing. A new kind of sort is one row in `source/Sort.java` (menu id, title, subtitle label, back-stack tag) |
| Sorts inside a genre | `genreOf` with `inGenre` (the server ranks the genre) or `cardGenre` (the cards are narrowed on the device) |
| Ranking periods, a duration bound | `periods`, `periodOf`, `withPeriod`, `periodsAreDates`; `withDuration`; `listsDuration` false when cards carry no time |
| Referer, own CDN or API hosts, a slow server | `referer()`, `hosts()`, `timeoutMs()`; `mediaHeaders(bookUrl)` for audio requests. `timeoutMs()` also lengthens the site's limit in the multi-site search and "on other sites" (`Provider.requestSeconds`); raise it when `LiveDumpTest`'s `search-times.txt` marks the site SLOW (a two-word search above half its limit). A site set to go direct fetches its audio direct on every host, a CDN it shares with other sites included (`AppProxy.mediaClient`) |
| A card that is a list of books (a collection) | `opensAsListing(url)`: the catalog opens it as a listing, never as a book page |
| Chapters that are time ranges of one file (a whole-book MP3) | `AudioPOJO` `timeStart`/`timeFinish`, and `downloadsSharedFiles()` so the file downloads once (`DownloadIds`), not once per chapter |
| A session cookie, a wall that lets only POST pass | `cookies()` (sent to the site's own host only; `mediaCookies(bookUrl)` for audio), `postsPages()` |
| Lists read from other pages or built in | override `loadListing`, `loadGenres`, `loadPeople`, `loadSeries` (the defaults fetch the URL and parse it) |
| Optional sections | `people`, `searchPeople`, `series`, `comments`, `recommendations`, `otherReaders`, `otherSource*` ("on other sites") |
| Genres for parental control and "favorite genres" | nothing per site: a card's genre name maps onto the app's own genres (`search/Genre.java`). Check the site's genre names against `Genre.of`/`Genre.allOf` and add them to `app/src/test/resources/genres/names.tsv`; a name that maps wrongly is fixed with a stem in `Genre`'s data, never a site branch |

Rules that keep the sites independent: a challenge or an HTTP error is an exception (`SiteHttp` raises it), never an
empty page; a page without any card is `EMPTY`, while a page whose cards were all filtered keeps its next-page evidence
(`ProviderPage.of(items, hasMore, rawCards)`); undeclared features stay at their defaults instead of returning
something approximate. When a site needs something the interface cannot express, add a default method to
`SourceParser` that leaves the other adapters unchanged, never a branch on the site in shared code.

## Setting up the toolchain

You need **JDK 17** to run Gradle and the **Android SDK** with the **API 36 platform**. The Java *source level* of the app (8) is unrelated to the JDK that runs the build. The Gradle wrapper downloads Gradle itself; do not use a globally installed Gradle.

### With Android Studio

1. Install a current [Android Studio](https://developer.android.com/studio) and open the repository folder.
2. In **Settings → Build, Execution, Deployment → Build Tools → Gradle**, set **Gradle JDK** to a JDK 17 (the bundled JetBrains Runtime 17 or 21 also works).
3. In **SDK Manager**, install **Android SDK Platform 36**. Missing build tools are installed automatically during the first sync.
4. Select the `app` run configuration and a device, then **Run**.

### Command line only

1. Install a JDK 17, for example [Eclipse Temurin](https://adoptium.net/temurin/releases/?version=17), and set `JAVA_HOME`.
2. Download the [command-line tools](https://developer.android.com/studio#command-line-tools-only) and unpack them so that this path exists: `<sdk>/cmdline-tools/latest/bin/sdkmanager`. A common mistake is an extra `cmdline-tools` level between `latest` and `bin`.
3. Point the build at the SDK, either with `ANDROID_HOME=<sdk>` or with a git-ignored `local.properties` in the repository root containing `sdk.dir=<sdk>` (on Windows write the path with forward slashes, e.g. `sdk.dir=C:/Android/sdk`).
4. Accept the licenses and install the packages:

```bash
sdkmanager --licenses
sdkmanager "platform-tools" "platforms;android-36" "build-tools;35.0.0"
```

Add `"emulator"` and a system image (see [Create an emulator](#create-an-emulator)) if you want to run the app without a physical device.

Check the setup with `java -version` (17.x), `./gradlew --version`, and `adb version`.

## Building

| Command | Result |
|---|---|
| `./gradlew :app:assembleDebug` | Debuggable APK with LeakCanary |
| `./gradlew :app:installDebug` | Builds and installs the debug APK on the connected device |
| `./gradlew :app:assembleRelease` | Minified, resource-shrunk, signed release APK (needs [release signing](#release-signing)) |
| `./gradlew :app:bundleRelease` | Signed Android App Bundle (AAB) for store uploads |
| `./gradlew :app:dependencies --configuration releaseRuntimeClasspath` | Resolved dependency tree |
| `./gradlew --stop` | Stops Gradle daemons, e.g. after changing the JDK |

| Output | Path |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| App Bundle | `app/build/outputs/bundle/release/app-release.aab` |
| R8 mapping (to decode release stack traces) | `app/build/outputs/mapping/release/mapping.txt` |
| Merged manifest | `app/build/intermediates/merged_manifests/<variant>/process<Variant>Manifest/AndroidManifest.xml` |

Notes:

- **Debug signing:** debug builds use the per-machine key in `~/.android/debug.keystore`, created automatically. A debug build from another machine cannot update yours; uninstall first.
- **Database changes:** when you change a Room entity, increase the database version, add a migration, and commit the newly exported JSON from `app/schemas/`. The instrumentation `MigrationTest` validates migrations against those files.
- **Dependency problems:** retry with `--stacktrace` or `--info`, or use `dependencyInsight`, and check that Google Maven, Maven Central, and JitPack are reachable before changing versions.

## Testing and lint

| Command | What runs |
|---|---|
| `./gradlew :app:testDebugUnitTest` | JVM unit tests (parsers, networking helpers, search, policies, playback logic) |
| `./gradlew :app:testDebugUnitTest --tests "com.fanok.audiobooks.net.AppProxyTest"` | A single test class |
| `./gradlew :app:lintDebug` | Android lint; errors fail the build, warnings are only reported |
| `./gradlew :app:connectedDebugAndroidTest` | Instrumentation tests on a running emulator or device |

Reports are written to `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`.

The parser tests run against saved pages in `app/src/test/resources/fixtures/<source>/`. When a site changes its markup, save the new page as a fixture, update the parser, and update or extend the matching test (for example `BooksModelParsersTest` or `BookDescriptionModelTest`). Tests never contact the real sites.

## Release signing

Release builds are signed only with a key you provide; without one, `assembleRelease` and `bundleRelease` stop with *Release signing is not configured* instead of falling back to a debug key.

1. Create a key once (PKCS12, 4096-bit RSA, valid for about 27 years):

```bash
keytool -genkeypair -v -storetype PKCS12 -keystore audiobooks-release.jks -alias audiobook -keyalg RSA -keysize 4096 -validity 10000
```

2. Copy [`keystore.properties.example`](../keystore.properties.example) to `keystore.properties` in the repository root and fill in `storeFile` (absolute, or relative to the repository root), `storePassword`, `keyAlias`, and `keyPassword`. The file is git-ignored, as are `*.jks` and `*.keystore`.
3. Build with `./gradlew :app:assembleRelease :app:bundleRelease`.

Keep the keystore and its passwords **outside the repository** and back them up. Android only installs an update signed with the same key as the installed app: losing the key means users must uninstall to update, and anyone holding a leaked key can publish updates in your name.

## Continuous integration and releases

Two GitHub Actions workflows live in [`.github/workflows/`](../.github/workflows/):

| Workflow | Runs on | Steps | Output |
|---|---|---|---|
| [Build](../.github/workflows/build.yml) | Pushes to `master`, pull requests (documentation-only changes are skipped), manual runs | Unit tests, Android lint, debug APK | Debug APK and test/lint reports as workflow artifacts (14 days) |
| [Release](../.github/workflows/release.yml) | Tags `v*`, manual runs | Checks that the tag matches `versionName`, unit tests, signed APK and AAB, signature check, SHA-256 checksums | A GitHub release with `AudioBooks-<version>.apk`, `.aab`, and `SHA256SUMS.txt`; a workflow artifact (90 days) that also contains the R8 mapping |

Both use JDK 17 on `ubuntu-latest` and `gradle/actions/setup-gradle`, which caches dependencies and validates the Gradle wrapper. The Build workflow needs no secrets, so it also runs for pull requests from forks.

### One-time setup: repository secrets

The Release workflow reads the signing key from four secrets. Add them under **Settings → Secrets and variables → Actions → New repository secret**:

| Secret | Value |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | The keystore file, Base64-encoded |
| `ANDROID_KEYSTORE_PASSWORD` | Keystore password (`storePassword`) |
| `ANDROID_KEY_ALIAS` | Key alias (`keyAlias`) |
| `ANDROID_KEY_PASSWORD` | Key password (`keyPassword`) |

Encode the keystore with one of:

```bash
base64 -w 0 audiobooks-release.jks   # Linux
base64 -i audiobooks-release.jks     # macOS
```

```powershell
[Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\path\to\audiobooks-release.jks")) | Set-Clipboard
```

With the [GitHub CLI](https://cli.github.com/), `base64 -w 0 audiobooks-release.jks | gh secret set ANDROID_KEYSTORE_BASE64` sets a secret directly; it prompts for the value when run without input. If a secret is missing, the workflow stops early and names it. Secrets are not shown in logs, and the workflow deletes the decoded keystore when it finishes.

### Publishing a release

1. Increase `versionCode` and set the new `versionName` in [`app/build.gradle`](../app/build.gradle). Commit, push to `master`, and wait for the Build workflow to pass.
2. Tag the commit with `v` plus the exact `versionName` and push the tag:

```bash
git tag -a v7.4 -m "AudioBooks 7.4"
git push origin v7.4
```

3. The Release workflow builds and publishes the release with notes generated from the merged pull requests and commits; edit them on GitHub afterwards if needed. A tag containing a hyphen, such as `v7.4-beta1` (with `versionName "7.4-beta1"`), is published as a pre-release.

4. Installed apps find the release themselves ([`update/Updater`](../app/src/main/java/com/fanok/audiobooks/update/Updater.java), "Автообновление"): GitHub first (the tag the latest release redirects to, then its `SHA256SUMS.txt` and `AudioBooks-<versionName>.apk`), so step 3 is enough for them. Where GitHub is blocked they read the mirror: upload the APK to `www.abc-guitars.com/data/audiobook/` and update `update.txt` there (`version=<versionName>`, `file=AudioBooks-<versionName>.apk`, optionally `sha256=<hash from SHA256SUMS.txt>`). The last resort is the 4pda topic: the parser reads the header's list after "Скачать:" (`<strong>Версия: X</strong>` followed by the link to the post) and takes the attachment whose file name ends in `-X.apk`, so keep that form. Every downloaded APK must be signed with the release key and have a higher `versionCode`, or the app rejects it.

If the workflow fails, for example because the tag does not match `versionName`, delete the tag, fix the cause, and tag again:

```bash
git push --delete origin v7.4
git tag -d v7.4
```

**Dry run:** **Actions → Release → Run workflow** builds the signed APK and AAB on any branch and keeps them as a workflow artifact without publishing a release. Use it to check new secrets.

**Crash reports from release builds** are obfuscated by R8. Keep the mapping file of every published version (download it from the workflow artifact before it expires) and decode a stack trace with `retrace mapping.txt stacktrace.txt` (`retrace` is in `<sdk>/cmdline-tools/latest/bin`).

## Running on an emulator or device

### Create an emulator

```bash
sdkmanager "emulator" "system-images;android-34;google_apis;x86_64"
avdmanager create avd -n audiobooks_api34 -k "system-images;android-34;google_apis;x86_64" -d pixel_5
emulator -avd audiobooks_api34
```

On Apple Silicon use the `arm64-v8a` image instead of `x86_64`. To check the Android TV interface, create a second AVD from an `android-tv` system image (`sdkmanager --list | grep android-tv`) with the `tv_1080p` device profile.

The emulator needs CPU acceleration: Windows Hypervisor Platform (WHPX) on Windows, Hypervisor.Framework on macOS (built in), and KVM on Linux. `emulator -accel-check` reports which one is usable. Without acceleration the emulator is extremely slow or crashes at startup; see [Configure hardware acceleration](https://developer.android.com/studio/run/emulator-acceleration).

Useful options for automation: `-no-window -no-audio -no-boot-anim -no-snapshot -gpu swiftshader_indirect`. `-no-snapshot` does not erase the device; `-wipe-data` does. Wait until Android has finished booting before installing:

```bash
adb wait-for-device shell 'while [ "$(getprop sys.boot_completed)" != 1 ]; do sleep 2; done'
```

### Install and launch

```bash
./gradlew :app:installDebug
adb shell am start -W -n com.fanok.books/com.fanok.audiobooks.activity.MainActivity
adb shell am start -n com.fanok.books/com.fanok.audiobooks.activity.SearchableActivity -a android.intent.action.SEARCH --es query 1984
```

The last command opens search results directly, which avoids typing into the search field. With several devices connected, add `-s <serial>` (from `adb devices -l`) to every `adb` command.

### Screenshots, UI, and input

```bash
adb exec-out screencap -p > screen.png
adb shell uiautomator dump /sdcard/window.xml && adb pull /sdcard/window.xml
adb shell input tap 540 1200
adb shell input swipe 540 1900 540 700 450
adb shell input keyevent KEYCODE_BACK
adb shell screenrecord --time-limit 20 /sdcard/ui.mp4 && adb pull /sdcard/ui.mp4
```

Take coordinates from the `bounds` attributes in the UI dump. `adb shell input text` is reliable only for ASCII; use the UI or an input method for Cyrillic text.

**Windows PowerShell 5.1** re-encodes redirected output and corrupts binary files. There, save the screenshot on the device with `adb shell screencap -p /sdcard/screen.png` and copy it with `adb pull`.

## Logs, crashes, and app data

```bash
adb logcat -v threadtime --pid=$(adb shell pidof -s com.fanok.books)
adb logcat -b crash -d
adb logcat -v threadtime AndroidRuntime:E MediaPlayerService:D ExoPlayerImplInternal:E '*:S'
```

- **Crash reports:** the app writes uncaught exceptions to its private `files/crash.txt` before handing them to the system handler. With a debug build: `adb shell run-as com.fanok.books cat files/crash.txt`.
- **StrictMode:** debug builds log main-thread disk and network access and leaked closeables under the `StrictMode` tag.
- **App data** (debug builds only, through `run-as`): the Room database `databases/audioBooksDB` with its `-wal` and `-shm` files, settings in `shared_prefs/com.fanok.books_preferences.xml`, and playback state in `shared_prefs/STORAGE.xml`. Force-stop the app first for a consistent copy:

```bash
adb shell am force-stop com.fanok.books
adb exec-out run-as com.fanok.books tar -cf - databases shared_prefs files > app-data.tar
```

  Android Studio's **App Inspection → Database Inspector** and **Device Explorer** do the same interactively. `run-as` fails on release builds by design.
- **Debugging from startup:** `adb shell am set-debug-app -w com.fanok.books`, launch the app, attach the Android Studio debugger, and run `adb shell am clear-debug-app` when finished.
- **Bug reports:** `adb bugreport bugreport.zip` collects system-wide diagnostics. Logs and bug reports can contain personal data, cookies, and signed URLs; review them before sharing.

## Network, proxy, and playback diagnostics

### How the app talks to sites

- **Pages** go through `net/SiteHttp` (Jsoup over `HttpURLConnection`) with a per-host cookie jar. Cloudflare and DDoS-Guard challenge pages raise `CookesExeption` instead of being parsed. **Settings → site cookies** opens the site in a WebView (`ActivityCookies`) to pass such a challenge.
- **Audio streaming, downloads, and covers** use the shared OkHttp client from `App` (Media3 `OkHttpDataSource`, Picasso). Downloads use an HTTP/1.1 copy of it so that parallel segment requests get separate connections.
- **TLS** uses normal platform validation; `network_security_config.xml` adds the ISRG Root X1 anchor for sites whose certificates older Android versions do not trust.

### Built-in proxy

**Settings → Proxy** (`Настройки → Прокси`, preference `pref_proxy`) sends the app's HTTP and HTTPS traffic through a proxy for users in countries where some source sites are blocked: off, the built-in proxy, or an automatically found public one.

- `net/AppProxy` installs a process-wide `ProxySelector` in `App.onCreate`, before any HTTP client is created, so pages, audio, downloads, and covers all follow the switch. Toggling it takes effect immediately; with the switch off, the system proxy settings apply.
- **Settings → Свой прокси** (`fragment/CustomProxyDialogFragment`, `net/ProxyConfig`) sets the built-in proxy's server: SOCKS5 or HTTP (an HTTP proxy tunnels https sites with CONNECT; a proxy reached over TLS is not supported), host, port, and an optional login. A value the user never saved is the build's own (`PROXY_HOST`, `PROXY_PORT`, `PROXY_USER`, `PROXY_PASSWORD` in `Consts.java`); the build's password is never stored or shown (the eye appears only for a typed one). The dialog's check sends one request through exactly the typed server to the selected source's site (any answer but the proxy's 407 counts as working). The login is given only to that proxy's host and port: a SOCKS handshake through the `Authenticator`, an HTTP proxy's 407 through `AppProxy.proxyLogin` (OkHttp's proxy authenticator); never to a site or to a public proxy.
- OkHttp lets the proxy resolve host names, so audio and downloads also work where DNS is filtered.
- **Settings → Источники и сеть → Управление источниками** (`fragment/SourcesDialogFragment`) is one dialog for the
  sources: their order (drag a row by its handle, or the row's menu on TV and with TalkBack), the active ones (above
  the divider, 1–13, `search/Sources`), the route of each site (direct or through the proxy; the header sets all at
  once) and their availability. Only active sources exist for the source menu, the source setting, the searches and
  "on other sites"; links, the library and the routes still know every site (`Provider.ofUrl` and friends). The order
  and the active ones are one preference, `pref_sources` (`"abook,izibuc|otrub"`, active before `|`); a site added
  later is not in it and starts inactive. Switching the selected source off selects the first active one.
- The route is chosen per host: a site's server, its subdomains and its `SourceParser.hosts()` follow the site's
  choice; any other host (a CDN several sites share) follows the proxy mode. The choice is stored inverted, as the set
  of direct sites (`AppProxy.PREF_DIRECT`), so a new site uses the proxy until the user says otherwise. Changing it
  drops pooled connections and installs a new `ProxySelector` instance (the platform `HttpURLConnection` pool of site
  pages keys its connections by the selector); the automatic mode then verifies its proxy against a site that uses it.
- When a listing of a site fails, the catalog offers once per site while the app runs (`net/RouteOffer`) to try the
  other route; when that fails too the old route is restored. With the proxy off it offers the proxy settings.
- `SourceProbe.probe(provider)` checks whether a site works from here (its first catalog page through its own adapter,
  request and route; `OK` only when books are listed); `SourceHealth` keeps each site's last result and time. The
  dialog checks on its refresh button and after a change of one site; the automatic check runs at most daily on an
  unmetered network, active sites only.
- The WebView on the cookie screen does not use the proxy.
- `app/src/main/assets/*.ovpn` are OpenVPN profiles left over from an earlier VPN feature (removed upstream together with its `vpnLib` module); no code reads them.

To check the proxy server itself:

```bash
curl -sS -o /dev/null -w "%{http_code}\n" --socks5-hostname "USER:PASSWORD@HOST:PORT" https://akniga.org/
```

To test the app's proxy code without the real server, run any SOCKS5 server with username and password authentication on your computer, temporarily set `PROXY_HOST` to `10.0.2.2` (the host as seen from the emulator) and the matching port and credentials in a debug build, turn the switch on, and watch the server's connection log while browsing and playing. `AppProxyTest` covers the selector and the authenticator.

### Emulator networking

Inside the emulator, `10.0.2.2` is the host computer and `127.0.0.1` is the emulator itself. Alternatively, `adb reverse tcp:18765 tcp:18765` makes a host service on port 18765 reachable as `127.0.0.1:18765` on the device; remove it with `adb reverse --remove tcp:18765`. See [Emulator networking](https://developer.android.com/studio/run/emulator-networking).

### Playback

```bash
adb shell dumpsys media_session
adb shell dumpsys audio
```

Look at the session block of `com.fanok.books`; errors in other packages' sessions are not app errors. For detailed player events, temporarily attach Media3's `EventLogger` (`player.addAnalyticsListener(new EventLogger())`) in a debug build; see [Media3 debug logging](https://developer.android.com/media/media3/exoplayer/debug-logging).

Scenarios worth checking after changes to playback or downloads:

| Scenario | What to verify |
|---|---|
| Server errors (5xx) and recovery | Player error state, retries, and that Play recovers once the server is back |
| Expired links, 401/403 | Source URL refresh, bounded retries, chapter and position preserved |
| Pause while buffering | Playback stays paused when buffering completes |
| Interrupted download | Resume, partial-download state, and integrity of the exported file |
| Offline book | No network requests; chapters and position survive a process restart |
| Storage folder change or SAF permission loss | Running downloads and exports end cleanly with a clear error |
| Fast typing in search | Old queries' results do not overwrite newer ones; sources that fail are reported |

Use local fixture servers for error cases. A plain `python -m http.server` does not implement HTTP range requests correctly.

## Memory and leaks

1. Use a debug build. Open the screen or flow once, then return to a fixed idle state and take a baseline.
2. Repeat the flow 10–20 times (open and close a book, rotate, search and cancel, play and stop, start and remove a download), return to the same idle state, and compare.
3. Rising memory alone is not a leak; caches and pending work also grow. Confirm a retained object with LeakCanary or a heap dump.

```bash
adb shell dumpsys meminfo com.fanok.books
adb shell dumpsys activity services com.fanok.books
adb shell dumpsys jobscheduler | grep -A5 com.fanok.books
adb shell am dumpheap -g com.fanok.books /data/local/tmp/audiobooks.hprof
adb pull /data/local/tmp/audiobooks.hprof
```

LeakCanary is included in debug builds and reports retained activities, fragments, views, and services in a notification and in logcat (`adb logcat LeakCanary:D '*:S'`). It does not analyze heaps while a debugger is attached. Open heap dumps in the Android Studio Profiler and follow the paths to GC roots. Typical suspects in this app are presenters or Rx subscriptions holding destroyed views, timer callbacks, the media session, and the download executor.

## Performance

```bash
adb shell dumpsys gfxinfo com.fanok.books reset
# ...repeat the same scrolling or navigation...
adb shell dumpsys gfxinfo com.fanok.books framestats
adb shell perfetto -o /data/misc/perfetto-traces/audiobooks.perfetto-trace -t 20s sched freq idle am wm gfx view binder_driver dalvik input
adb pull /data/misc/perfetto-traces/audiobooks.perfetto-trace
```

Open traces in the [Perfetto UI](https://ui.perfetto.dev). Debug builds, LeakCanary, and emulator graphics add overhead, so confirm timing, battery, and audio behavior with a release build on a physical device.

## Verifying an APK

```bash
"$ANDROID_HOME"/build-tools/35.0.0/apksigner verify --print-certs app/build/outputs/apk/release/app-release.apk
"$ANDROID_HOME"/build-tools/35.0.0/zipalign -c -P 16 -v 4 app/build/outputs/apk/release/app-release.apk
sha256sum app/build/outputs/apk/release/app-release.apk
```

`apksigner` prints the signing certificate; compare its SHA-256 digest with the one in the Release workflow log. `zipalign -P 16` checks that native libraries are aligned for devices with 16 KB memory pages. Test those devices on a 16 KB system image (`adb shell getconf PAGE_SIZE` prints `16384`); see [Support 16 KB page sizes](https://developer.android.com/guide/practices/page-sizes).

## Troubleshooting

| Problem | Cause and fix |
|---|---|
| *SDK location not found* | Set `ANDROID_HOME` or `sdk.dir` in `local.properties` to the SDK root, not to its `cmdline-tools` folder |
| *Android Gradle plugin requires Java 17* or *Unsupported class file major version* | Gradle runs on the wrong JDK. Use JDK 17 (`JAVA_HOME` or the Gradle JDK setting in Android Studio), then `./gradlew --stop` |
| *Release signing is not configured* | Create `keystore.properties` ([Release signing](#release-signing)) |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed app is signed with another key. Back up in the app, uninstall, then install |
| `run-as: package not debuggable` | A release build is installed; install the debug build |
| Parser test fails after a site changed | Refresh the fixture and adapt the parser ([Testing and lint](#testing-and-lint)) |
| Lint fails in CI | Download the `reports` artifact of the run and open `lint-results-debug.html` |
| Release workflow: *Missing repository secrets* | Add the listed secrets ([One-time setup](#one-time-setup-repository-secrets)) |
| Release workflow: *Tag … does not match versionName* | Re-tag with `v` plus the exact `versionName` ([Publishing a release](#publishing-a-release)) |
| A catalog does not load in the app | Check logcat for the HTTP status or `CookesExeption`. The site may be down, blocked, or showing a challenge; try another source, the cookie screen, or the built-in proxy |
| Proxy switch is on but nothing loads | The proxy server is unreachable or rejects the credentials; test it with `curl` ([Built-in proxy](#built-in-proxy)) |
| Emulator crashes at start or is very slow | Hardware acceleration is missing; run `emulator -accel-check` |
| Screenshot or heap dump file is corrupt | It was redirected through PowerShell; use `adb pull` |
| `adb` commands go to the wrong device | Pass `-s <serial>` |
