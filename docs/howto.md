# AudioBooks — developer guide

How to set up a toolchain, build, test, sign, and release AudioBooks, and how to run, inspect, and debug it on an emulator or device. For what the app does and how to install it, see the [README](../readme.md).

Commands are written for a POSIX shell (Linux, macOS, Git Bash on Windows) and run from the repository root. On Windows use `gradlew.bat` instead of `./gradlew`; `adb`, `sdkmanager`, and `emulator` work the same in any shell, with one exception noted under [Screenshots, UI, and input](#screenshots-ui-and-input).

## Contents

1. [Project at a glance](#project-at-a-glance)
2. [Source layout](#source-layout)
3. [Setting up the toolchain](#setting-up-the-toolchain)
4. [Building](#building)
5. [Testing and lint](#testing-and-lint)
6. [Release signing](#release-signing)
7. [Continuous integration and releases](#continuous-integration-and-releases)
8. [Running on an emulator or device](#running-on-an-emulator-or-device)
9. [Logs, crashes, and app data](#logs-crashes-and-app-data)
10. [Network, proxy, and playback diagnostics](#network-proxy-and-playback-diagnostics)
11. [Memory and leaks](#memory-and-leaks)
12. [Performance](#performance)
13. [Verifying an APK](#verifying-an-apk)
14. [Troubleshooting](#troubleshooting)

## Project at a glance

| Item | Value |
|---|---|
| Application ID / namespace | `com.fanok.audiobooks` |
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
| `presenter/`, `model/`, `interface_pacatge/` | MVP layer (Moxy): presenters, per-site page parsers, view contracts |
| `search/` | Multi-source search sessions, paged listings, filters |
| `net/` | `SiteHttp` (page fetching, cookies, challenge detection), `AppProxy` (built-in proxy), cookie jar |
| `service/` | `MediaPlayerService` (playback, media session, notification, sleep timer), `ExoDownloadService`, playback error policies |
| `download/`, `util/` | Download tracking, storage and SAF access, deletion, Media3 data sources |
| `room/` | Room database `audioBooksDB`, DAOs, migrations; exported schemas live in `app/schemas/` |
| `policy/`, `android_equalizer/` | Parental control, equalizer |
| `app/src/main/res/` | Layouts (including `layout-television` and tablet variants), strings, `xml/preferences.xml` (settings), `xml/network_security_config.xml` |
| `app/src/test/` | JVM unit tests; saved site pages under `resources/fixtures/<source>/` |
| `app/src/androidTest/` | Instrumentation tests: Room migrations, downloads, player service, export |

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
adb shell am start -W -n com.fanok.audiobooks/.activity.MainActivity
adb shell am start -n com.fanok.audiobooks/.activity.SearchableActivity -a android.intent.action.SEARCH --es query 1984
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
adb logcat -v threadtime --pid=$(adb shell pidof -s com.fanok.audiobooks)
adb logcat -b crash -d
adb logcat -v threadtime AndroidRuntime:E MediaPlayerService:D ExoPlayerImplInternal:E '*:S'
```

- **Crash reports:** the app writes uncaught exceptions to its private `files/crash.txt` before handing them to the system handler. With a debug build: `adb shell run-as com.fanok.audiobooks cat files/crash.txt`.
- **StrictMode:** debug builds log main-thread disk and network access and leaked closeables under the `StrictMode` tag.
- **App data** (debug builds only, through `run-as`): the Room database `databases/audioBooksDB` with its `-wal` and `-shm` files, settings in `shared_prefs/com.fanok.audiobooks_preferences.xml`, and playback state in `shared_prefs/STORAGE.xml`. Force-stop the app first for a consistent copy:

```bash
adb shell am force-stop com.fanok.audiobooks
adb exec-out run-as com.fanok.audiobooks tar -cf - databases shared_prefs files > app-data.tar
```

  Android Studio's **App Inspection → Database Inspector** and **Device Explorer** do the same interactively. `run-as` fails on release builds by design.
- **Debugging from startup:** `adb shell am set-debug-app -w com.fanok.audiobooks`, launch the app, attach the Android Studio debugger, and run `adb shell am clear-debug-app` when finished.
- **Bug reports:** `adb bugreport bugreport.zip` collects system-wide diagnostics. Logs and bug reports can contain personal data, cookies, and signed URLs; review them before sharing.

## Network, proxy, and playback diagnostics

### How the app talks to sites

- **Pages** go through `net/SiteHttp` (Jsoup over `HttpURLConnection`) with a per-host cookie jar. Cloudflare and DDoS-Guard challenge pages raise `CookesExeption` instead of being parsed. **Settings → site cookies** opens the site in a WebView (`ActivityCookies`) to pass such a challenge.
- **Audio streaming, downloads, and covers** use the shared OkHttp client from `App` (Media3 `OkHttpDataSource`, Picasso). Downloads use an HTTP/1.1 copy of it so that parallel segment requests get separate connections.
- **TLS** uses normal platform validation; `network_security_config.xml` adds the ISRG Root X1 anchor for sites whose certificates older Android versions do not trust.

### Built-in proxy

**Settings → Proxy** (`Настройки → Прокси`, preference `pref_proxy`) sends the app's HTTP and HTTPS traffic through a SOCKS5 proxy for users in countries where some source sites are blocked.

- `net/AppProxy` installs a process-wide `ProxySelector` in `App.onCreate`, before any HTTP client is created, so pages, audio, downloads, and covers all follow the switch. Toggling it takes effect immediately; with the switch off, the system proxy settings apply.
- The server and credentials are `PROXY_HOST`, `PROXY_PORT`, `PROXY_USER`, and `PROXY_PASSWORD` in `Consts.java`. An empty `PROXY_HOST` disables the feature. The credentials are given only to that proxy host and port, never to a site that asks for authentication.
- OkHttp lets the proxy resolve host names, so audio and downloads also work where DNS is filtered.
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

Look at the session block of `com.fanok.audiobooks`; errors in other packages' sessions are not app errors. For detailed player events, temporarily attach Media3's `EventLogger` (`player.addAnalyticsListener(new EventLogger())`) in a debug build; see [Media3 debug logging](https://developer.android.com/media/media3/exoplayer/debug-logging).

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
adb shell dumpsys meminfo com.fanok.audiobooks
adb shell dumpsys activity services com.fanok.audiobooks
adb shell dumpsys jobscheduler | grep -A5 com.fanok.audiobooks
adb shell am dumpheap -g com.fanok.audiobooks /data/local/tmp/audiobooks.hprof
adb pull /data/local/tmp/audiobooks.hprof
```

LeakCanary is included in debug builds and reports retained activities, fragments, views, and services in a notification and in logcat (`adb logcat LeakCanary:D '*:S'`). It does not analyze heaps while a debugger is attached. Open heap dumps in the Android Studio Profiler and follow the paths to GC roots. Typical suspects in this app are presenters or Rx subscriptions holding destroyed views, timer callbacks, the media session, and the download executor.

## Performance

```bash
adb shell dumpsys gfxinfo com.fanok.audiobooks reset
# ...repeat the same scrolling or navigation...
adb shell dumpsys gfxinfo com.fanok.audiobooks framestats
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
