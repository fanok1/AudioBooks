# Audiobooks Android — build, emulator, test and debugging guide

**Verified on October 22, 2026, in `C:\work\audiobook`.** This is the operational handoff for this machine and future analysis sessions. The detailed application review, findings, reproduction evidence and remediation priorities are in [analyze.md](analyze.md).

The application is Java/XML, package **`com.fanok.audiobooks`**, version **7.3 / 95**. Both debug and minified release APKs build. An Android 14 emulator was recovered and used for UI, playback and instrumentation checks. This guide distinguishes those verified results from additional diagnostic recipes that have not yet been exercised.

## Contents

1. [Quick start on this machine](#quick-start-on-this-machine)
2. [Project and verification baseline](#project-and-verification-baseline)
3. [Installed tools and exact locations](#installed-tools-and-exact-locations)
4. [Reconstructing the toolchain](#reconstructing-the-toolchain)
5. [Running and managing the emulator](#running-and-managing-the-emulator)
6. [Why the emulator crashed and how it was fixed](#why-the-emulator-crashed-and-how-it-was-fixed)
7. [Build, signing, lint and normal test commands](#build-signing-lint-and-normal-test-commands)
8. [Standalone Android audit probes](#standalone-android-audit-probes)
9. [Observe and interact with the app](#observe-and-interact-with-the-app)
10. [Crash, ANR, debugger and data inspection](#crash-anr-debugger-and-data-inspection)
11. [Memory and resource leak investigation](#memory-and-resource-leak-investigation)
12. [Playback, streaming, downloads and search diagnostics](#playback-streaming-downloads-and-search-diagnostics)
13. [Rendering and performance traces](#rendering-and-performance-traces)
14. [APK and native-library verification](#apk-and-native-library-verification)
15. [Saved scripts and evidence](#saved-scripts-and-evidence)
16. [FAQ and lessons learned](#faq-and-lessons-learned)
17. [Checklist for the next session](#checklist-for-the-next-session)

## Quick start on this machine

Open PowerShell in the repository. The first block establishes the variables used by later examples and creates a new evidence directory so the original audit logs are preserved.

```powershell
Set-Location -LiteralPath 'C:/work/audiobook'
$auditRoot = 'C:/Users/user/AppData/Local/audiobook-audit-tools'
. "$auditRoot/environment.ps1"
$adb = Join-Path $env:ANDROID_HOME 'platform-tools/adb.exe'
$auditSerial = 'emulator-5554'
$appPackage = 'com.fanok.audiobooks'
$evidenceDir = Join-Path $auditRoot ('sessions/' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

java -version
./gradlew.bat --version
& "$env:ANDROID_HOME/emulator/emulator.exe" -accel-check
& $adb devices -l
```

The dot before the environment script is necessary: **dot-sourcing applies its environment variables to the current PowerShell session**. Each new terminal or independent agent command needs to load them again. Global PATH/JAVA_HOME changes were not made.

Build, start or reuse the emulator, install the debug app, and launch it:

```powershell
./gradlew.bat :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Debug build failed' }

& "$auditRoot/start-audit-emulator.ps1"
$auditBootDeadline = (Get-Date).AddMinutes(3)
$auditBootComplete = $false
do {
    $auditBootState = & $adb -s $auditSerial shell getprop sys.boot_completed 2>$null
    if ($LASTEXITCODE -eq 0 -and (($auditBootState -join '').Trim() -eq '1')) {
        $auditBootComplete = $true
        break
    }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $auditBootDeadline)
if (-not $auditBootComplete) { throw 'Android did not finish booting within three minutes; inspect emulator logs' }

& $adb -s $auditSerial install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -s $auditSerial shell am start -W -n 'com.fanok.audiobooks/.activity.MainActivity'
```

**State at handoff:** `emulator-5554` was online, `sys.boot_completed=1`, AEHD was running, and the **minified release APK** was installed. The quick start deliberately replaces it with debug for LeakCanary, `run-as`, debugger access and the audit harness. Both APKs currently use the same local debug signing key, so this replacement succeeded in the original workflow. Recheck current state rather than assuming it persists across host restarts.

The launcher defaults to **headless and host audio disabled**. Android UI can still be viewed and controlled through screenshots and ADB. See [emulator modes](#running-and-managing-the-emulator) for audio/window options and their verification limits.

## Project and verification baseline

| Item | Supplied configuration or observed result |
|---|---|
| Module | `app` |
| Application/namespace | `com.fanok.audiobooks` |
| Entry activity | `.activity.MainActivity` |
| Java source/target level | Java 8, with core-library desugaring |
| JDK used to build | Temurin 17.0.20.1+1 |
| Gradle wrapper | 8.13, pinned in `gradle/wrapper/gradle-wrapper.properties` |
| Android Gradle Plugin | 8.13.2, pinned in root `build.gradle` |
| Minimum / target / compile SDK | 23 / 34 / 36 |
| Build-Tools used | 35.0.0, selected by the build; not explicitly pinned in app configuration |
| Main libraries | Media3 1.9.0, Room 2.8.4, RxJava 2.2.21, Moxy-X 1.7.0, Jsoup 1.21.2, OkHttp 5.3.2 |
| Debug leak detector | LeakCanary 2.14, already a `debugImplementation` dependency |
| Debug / release assembly | Both completed successfully after a missing debug keystore was generated |
| `testDebugUnitTest` | `NO-SOURCE`; no repository unit tests executed |
| Repository Android tests | No `app/src/androidTest` source tests supplied at audit time |
| Full debug lint | 84 errors, 386 warnings, 1 hint; 79 errors were Media3 unstable-API opt-in diagnostics |
| Combined build/lint command | Exit 1 due to lint; `--continue` still produced both APKs |
| Runtime | Android 14 / API 34 / x86_64, with generated audio and isolated test data |
| Native compatibility | ZIP alignment passed; packaged Conscrypt ELF LOAD alignment failed the 16 KB check |

Useful source locations:

| Location | Responsibility |
|---|---|
| `app/src/main/java/com/fanok/audiobooks/App.java` | Application setup, shared network client, cache/download manager, crash/ANR files |
| `service/MediaPlayerService.java` under that Java root | Playback, media session, notifications, timer and progress persistence |
| `service/ExoDownloadService.java` | Downloads and custom file/HLS export |
| `util/DownloadUtil.java`, `util/DownloadStorageHelper.java` | Media request resolution, filesystem/SAF storage, scanning and deletion |
| `model/`, `presenter/` | Provider parsing, search aggregation and application orchestration |
| `activity/`, `fragment/`, `adapter/`, `app/src/main/res/` | Screens, lifecycle and layouts |
| `room/`, `app/schemas/com.fanok.audiobooks.room.AppDatabase/17.json` | Database and exported schema |
| `app/src/main/AndroidManifest.xml` | Source declarations; inspect the merged manifest too |

Use `rg` and `rg --files` for targeted searches. The repository was initially entirely untracked; inspect `git status --short` before interpreting any diff or creating commits. An audit document is a snapshot, not evidence that its findings have subsequently been repaired.

## Installed tools and exact locations

The common root is **`C:\Users\user\AppData\Local\audiobook-audit-tools`**, abbreviated as `$auditRoot` in examples. It is outside the Git repository. A fresh clone alone will not contain these tools, the harness or the evidence.

| Tool/artifact | Version | Location relative to `$auditRoot` unless absolute | Purpose/status |
|---|---|---|---|
| Temurin JDK | 17.0.20.1+1 | `java/jdk-17.0.20.1+1` | Installed portable JDK; includes java, javac, jar, keytool, jdb |
| Android SDK root | — | `android-sdk` | `ANDROID_HOME` |
| Command-line tools | 22.0, build package 15859902 | `android-sdk/cmdline-tools/latest` | sdkmanager, avdmanager, apkanalyzer and Android CLI |
| Platform-tools / ADB | 37.0.1-15733141; ADB protocol 1.0.41 | `android-sdk/platform-tools` | Device/emulator management |
| Build-Tools | 35.0.0 | `android-sdk/build-tools/35.0.0` | aapt2, d8, apksigner, zipalign |
| Compile platform | API 36, revision 2 | `android-sdk/platforms/android-36` | Includes `android.jar` |
| Android Emulator | 37.2.12.0, build 16428233 | `android-sdk/emulator` | Working with acceleration |
| API 34 system image | revision 2, default x86_64 | `android-sdk/system-images/android-34/default/x86_64` | Android 14 image; no Play Store |
| AEHD installer/package | 2.2 | `android-sdk/extras/google/Android_Emulator_Hypervisor_Driver` | Signed driver package |
| Installed hypervisor driver | AEHD 2.2 | `C:/Windows/System32/drivers/aehd.sys`; service `aehd` | System-level installation, SYSTEM_START, currently running; verify with `sc.exe query aehd` / `sc.exe qc aehd` |
| Main AVD | `audiobook_audit_api34` | `avd/audiobook_audit_api34.avd` and sibling `.ini` | Port 5554, current audit device |
| Audio-startup AVD | `audiobook_audit_audio_api34` | `avd/audiobook_audit_audio_api34.avd` | Tested on port 5556; stopped afterward |
| Archived fallback emulator | 35.6.11 | `emulator-35/emulator` | Diagnostic comparison only; also crashed without acceleration |
| Gradle distribution | 8.13 | `C:/Users/user/.gradle/wrapper/dists/gradle-8.13-bin/5xuhj0ry160q40clulazy9h7d/gradle-8.13` | Downloaded by wrapper; no global Gradle installation needed |
| Gradle dependency/transform cache | — | `C:/Users/user/.gradle/caches` | Needed by existing machine-specific harness arg files |
| Debug keystore | local generated identity | `C:/Users/user/.android/debug.keystore` | Used by app and harness; release configuration also currently uses it |
| Session environment | — | `environment.ps1` | Sets JAVA_HOME, ANDROID_HOME, ANDROID_AVD_HOME and session PATH |
| Emulator launcher | — | `start-audit-emulator.ps1` | Checks acceleration, reuses serial 5554, launches hidden process with logs |
| Test harness | custom audit artifact | `runtime-harness/` | Separate signed instrumentation APK and source |

Existing tools reused, not newly installed: **Python 3.10.9** at `C:\ProgramData\miniconda3\python.exe`, PowerShell **5.1.19041.1682**, `curl.exe` at `C:\Windows\System32\curl.exe`, `rg.exe` at `C:\Windows\rg.exe`, and Git at `C:\Program Files\Git\cmd\git.exe`. Python audit scripts use the standard library; no pip environment is required.

Android Studio and the Android NDK were **not installed for the audit**. A pre-existing PyCharm-bundled JBR 21 was briefly used during bootstrap; the repeatable build environment uses the dedicated Temurin JDK above. If installing Android Studio later, configure its SDK and Gradle JDK to these explicit paths. The IDE runtime, Gradle runtime JDK and app Java language level are separate choices. [Android JDK configuration](https://developer.android.com/build/jdks)

Duplicate directories `platform-tools-2`, `platforms/android-36-2`, and `build-tools/35.0.0-2` were created when SDK Manager and Gradle auto-installation overlapped. Use the canonical paths without `-2`. Optional recursive duplicate cleanup was blocked by automatic approval review and left intact. These warnings did not prevent builds or emulator recovery. Avoid concurrent SDK mutations during future setup.

## Reconstructing the toolchain

### Preserve or obtain the downloads

The following archives remain in `$auditRoot`. Their hashes were verified. These are exact audit versions, not promises about whichever version a future “latest” endpoint returns.

| Local archive | Official download/source | SHA-256 |
|---|---|---|
| `jdk.zip` | [Temurin 17.0.20.1+1 Windows x64 ZIP](https://github.com/adoptium/temurin17-binaries/releases/download/jdk-17.0.20.1%2B1/OpenJDK17U-jdk_x64_windows_hotspot_17.0.20.1_1.zip) | `e53a79c3c3d86865bd7e787903884331068e71321714ffd44f145785affc7cb0` |
| `android-commandline.zip` | [Google command-line tools 15859902](https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip) | `90ae805d20434428bffcb699c290860f19bb5f66a67e6b330067e3de801fb04a` |
| `emulator-35.zip` | [Google Emulator 35.6.11 archive](https://dl.google.com/android/repository/emulator-windows_x64-13610412.zip) | `0865071aecf3d90a5b967cb6cb0cd48bea0a58a26ef4953b32037671c129b7e9` |

`temurin-package.json` preserves the Adoptium API response's package URL, checksum and size. Official discovery pages: [Temurin releases](https://adoptium.net/temurin/releases/?version=17), [Android downloads](https://developer.android.com/studio), [emulator archives](https://developer.android.com/studio/emulator_archive). The project wrapper downloads [Gradle 8.13](https://services.gradle.org/distributions/gradle-8.13-bin.zip) itself.

On a new machine, download the selected versions from those sources, verify their published checksums, and extract into a new tools directory. The required layout is:

```text
audiobook-audit-tools/
  java/jdk-17.0.20.1+1/bin/java.exe
  android-sdk/cmdline-tools/latest/bin/sdkmanager.bat
  android-sdk/cmdline-tools/latest/bin/avdmanager.bat
  android-sdk/cmdline-tools/latest/bin/android.exe
  android-sdk/cmdline-tools/latest/lib/...
  avd/
```

A common extraction error is an extra `cmdline-tools` directory between `latest` and `bin`. `latest/bin/sdkmanager.bat` must exist. The directory name `latest` here contains installed command-line-tools **22.0**; it does not automatically update its contents. [SDK manager setup](https://developer.android.com/tools/sdkmanager)

For example, with the verified archives already present, use a **new destination** rather than extracting over a running SDK:

```powershell
$newTools = 'C:/Android/audiobook-tools'
New-Item -ItemType Directory -Force -Path $newTools | Out-Null
Expand-Archive -LiteralPath "$auditRoot/jdk.zip" -DestinationPath "$newTools/java"
Expand-Archive -LiteralPath "$auditRoot/android-commandline.zip" -DestinationPath "$newTools/unpacked-commandline"
New-Item -ItemType Directory -Force -Path "$newTools/android-sdk/cmdline-tools/latest" | Out-Null
Copy-Item -Path "$newTools/unpacked-commandline/cmdline-tools/*" -Destination "$newTools/android-sdk/cmdline-tools/latest" -Recurse

$env:JAVA_HOME = "$newTools/java/jdk-17.0.20.1+1"
$env:ANDROID_HOME = "$newTools/android-sdk"
$env:ANDROID_AVD_HOME = "$newTools/avd"
$env:Path = "$env:JAVA_HOME/bin;$env:ANDROID_HOME/platform-tools;$env:ANDROID_HOME/cmdline-tools/latest/bin;$env:Path"
```

Update `environment.ps1`, the launcher, the installer wrapper and harness paths if moving directories/users. They contain absolute paths to this machine. Copying the SDK does not install its Windows hypervisor driver, and copying an AVD requires its sibling `.ini` path to be updated.

### Install platform packages and licenses

These are the package IDs used during the audit:

```powershell
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" --licenses
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" 'platform-tools' 'platforms;android-36' 'build-tools;35.0.0' 'emulator' 'system-images;android-34;default;x86_64'
```

Review/accept the SDK licenses in that setup context. Run package installation to completion **before** starting Gradle. `platform-tools` and `emulator` resolve to versions currently available on the selected channel; on another date, inventory the actual installed version rather than assuming the audit versions. `source.properties` or `package.xml` records installed package metadata.

The installed command-line tools still provide `sdkmanager` and `avdmanager`, and those commands worked here. Current Google documentation deprecates them in favor of **`android sdk`** and **`android emulator`**. The guide keeps the tested commands for reproducibility; consult the installed CLI help/current docs when moving to the new interface. [SDK manager](https://developer.android.com/tools/sdkmanager), [AVD manager](https://developer.android.com/tools/avdmanager)

List before changing:

```powershell
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" --list_installed
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" list avd
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/android.exe" --help
```

The build recognizes `ANDROID_HOME`; a `local.properties` file was not required. If Android Studio needs one, use this local setting and keep it untracked:

```properties
sdk.dir=C:/Users/user/AppData/Local/audiobook-audit-tools/android-sdk
```

### Create a fresh AVD

Choose a new name when creating another test device; avoid `--force` over an existing device containing evidence.

```powershell
New-Item -ItemType Directory -Force -Path $env:ANDROID_AVD_HOME | Out-Null
'no' | & "$env:ANDROID_HOME/cmdline-tools/latest/bin/avdmanager.bat" create avd -n audiobook_fresh_api34 -k 'system-images;android-34;default;x86_64' -d pixel_5
```

The original creation reported missing optional system-image `devices.xml` metadata but created a usable AVD. Verify the `.ini`, `config.ini`, emulator listing and actual boot; do not assume every warning is fatal. The image is an AOSP/default image without the Play Store. Billing, Google Play review flows and other Google-service integration need a suitable image/device and separate tests.

## Running and managing the emulator

### Working configuration

| Setting | Verified value |
|---|---|
| Emulator / AVD | 37.2.12 / `audiobook_audit_api34` |
| Image / ABI | Android 14, API 34, default x86_64 |
| CPU acceleration | AEHD 2.2, `-accel on` |
| Rendering | `-gpu swiftshader -feature -Vulkan` |
| Memory / CPUs | `-memory 2048 -cores 2` |
| Window / audio | Primary run: `-no-window -no-audio` |
| Startup | `-no-boot-anim -no-snapshot` |
| Serial | `emulator-5554` |

The AVD config contains some different defaults, including four CPUs and automatic graphics. **The launcher arguments override those defaults.** Preserve the complete command when reproducing a run.

```powershell
& "$auditRoot/start-audit-emulator.ps1"
```

The launcher checks `-accel-check`, reuses an existing `emulator-5554` marked `device` **or `offline`**, and starts a hidden process with output redirected to `emulator-accelerated.stdout.log` / `.stderr.log`. It does not wait for Android's full boot. A stuck offline instance is not repaired merely by invoking the launcher again. Those log filenames are overwritten on a new launch, so archive them first when investigating a failure.

Raw equivalent, which occupies the current terminal until stopped:

```powershell
& "$env:ANDROID_HOME/emulator/emulator.exe" -avd audiobook_audit_api34 -port 5554 -no-window -no-audio -no-boot-anim -no-snapshot -accel on -gpu swiftshader -feature -Vulkan -memory 2048 -cores 2
```

Check readiness:

```powershell
& $adb devices -l
& $adb -s $auditSerial wait-for-device
& $adb -s $auditSerial shell getprop sys.boot_completed
& $adb -s $auditSerial shell getprop ro.build.version.sdk
& $adb -s $auditSerial shell getprop ro.product.cpu.abi
```

`wait-for-device` can wait indefinitely when no device boots. Use bounded polling in automation; an online ADB connection is not proof that package manager/launcher finished starting.

### Audio and desktop window

The helper accepts:

```powershell
& "$auditRoot/start-audit-emulator.ps1" -EnableAudio
& "$auditRoot/start-audit-emulator.ps1" -ShowWindow -EnableAudio
```

An existing instance is reused, so those switches **do not transform an already-running headless/no-audio instance**. To change launch mode, first finish the current tests, stop that specific emulator, and launch again:

```powershell
& $adb -s $auditSerial emu kill
# Wait until that serial disappears from adb devices -l before relaunching.
& "$auditRoot/start-audit-emulator.ps1" -ShowWindow -EnableAudio
```

Headless startup with audio enabled was verified on the separate `audiobook_audit_audio_api34` / port 5556 device. Its microphone input was set to `no`; speaker output was reported by Android audio policy. **Audible sound quality and the desktop Qt window were not verified.** The helper also launches with `-WindowStyle Hidden`; if validating a desktop window later, use the raw emulator command in an interactive terminal, omitting `-no-window` and `-no-audio`, rather than assuming the helper makes an existing window visible. Inspect `hw.audioInput` before enabling audio if microphone input is unwanted.

Avoid two emulator processes writing the same AVD. A second concurrent test needs a separately created AVD and another even console port, e.g. 5556. Use explicit ADB serials throughout so screenshots, installs, force-stops and test state go to the intended device.

`-no-snapshot` disables snapshot load/save; it does not erase the AVD's userdata. Do not add `-wipe-data`, uninstall the app or clear its package data merely to resolve a transient connection problem. Those operations remove test/library state.

## Why the emulator crashed and how it was fixed

### Verified diagnosis and recovery

The host is Windows 10 Enterprise LTSC 10.0.19044, 64-bit, with an AMD Ryzen 5 5500U and firmware virtualization/SLAT already enabled. Initial launches had no usable CPU acceleration. Both current Emulator 37.2.12 and archived 35.6.11 crashed with **Windows `0xC0000005`**, before Android booted.

The older crash dump showed an invalid native read at address `0x2` inside `qemu-system-x86_64-headless.exe`, offset `0x1c8d60`. That was a host emulator crash, not a defect in the Android application. AVX/F16C warnings from software emulation were observed but did not identify the exact faulting native function. Symbols were unavailable.

Installing Google's signed **AEHD 2.2** driver through normal Windows UAC and using `-accel on` made the same current emulator/system image boot successfully. No reboot, BIOS change or Windows security-feature change was needed. SwiftShader remains the graphics backend; software rendering is different from disabling CPU virtualization. Google documents the unaccelerated CPU path as unsupported/very slow. [Emulator command-line guidance](https://developer.android.com/studio/run/emulator-commandline)

Read-only checks:

```powershell
Get-CimInstance Win32_Processor | Select-Object Name,VirtualizationFirmwareEnabled,SecondLevelAddressTranslationExtensions
Get-CimInstance Win32_ComputerSystem | Select-Object HypervisorPresent
& "$env:ANDROID_HOME/emulator/emulator.exe" -accel-check
sc.exe query aehd
```

Expected working backend output includes `AEHD (version 2.2) is installed and usable`, exit 0; the driver service should report `RUNNING`. `HypervisorPresent=False` by itself is not a post-fix failure verdict for AEHD; use the emulator backend check and driver state.

### Reinstalling acceleration only if needed

The signed package is available through:

```powershell
& "$env:ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager.bat" "--sdk_root=$env:ANDROID_HOME" 'extras;google;Android_Emulator_Hypervisor_Driver'
$driverDir = Join-Path $env:ANDROID_HOME 'extras/google/Android_Emulator_Hypervisor_Driver'
Get-AuthenticodeSignature -LiteralPath "$driverDir/aehd.Sys","$driverDir/aehd.cat" | Select-Object Path,Status
```

The audit retained `install-aehd.ps1` and a local `silent_install_safe.bat` that runs the installation steps without the original script's self-elevation wrapper. They are audit helper files, not a separately signed Google utility. The driver/catalog signatures verified as Valid. The wrapper was run from an elevated PowerShell process using normal UAC:

```powershell
Start-Process -FilePath powershell.exe -Verb RunAs -WindowStyle Hidden -Wait -ArgumentList @('-NoProfile','-File','C:/Users/user/AppData/Local/audiobook-audit-tools/install-aehd.ps1')
```

This is a repair/setup operation, not a prerequisite for every test run. The underlying installer can stop/delete/recreate the driver service; finish emulator sessions before reinstalling it. On a fresh machine, use the official package's installer and instructions if the audit helpers were not transferred. Logs: `aehd-package-install.log` and `aehd-driver-install.log`.

**Future compatibility:** Google schedules AEHD sunset for **December 31, 2026** and recommends Windows Hypervisor Platform (WHPX). A future session should follow the current Windows-feature/WHPX instructions and coordinate the required reboot. Do not disable Core Isolation or other Windows security features merely to preserve the old backend. WHPX migration was not performed in this audit. [Android acceleration setup](https://developer.android.com/studio/run/emulator-acceleration), [official AEHD project](https://github.com/google/android-emulator-hypervisor-driver)

### If a new launch fails

1. Preserve current emulator stdout/stderr and any minidump before another launch overwrites the logs.
2. Separate a host process crash, ADB offline state, incomplete Android boot, app crash and no-audio output; they are different failures.
3. Check acceleration first. Repeating `-accel off` or switching to an ARM image on this AMD host does not restore VM acceleration.
4. Reproduce with the known complete flags and existing working AVD. Change one variable at a time; host GPU/Vulkan were not independently proved faulty.
5. If the current SDK/image changed, compare against archived versions only as a controlled diagnostic. Emulator 35.6.11 did not fix the original absent-acceleration problem.
6. Use `adb devices -l`, `sys.boot_completed`, process state and fresh logs to establish which layer recovered.

Crash evidence includes `emulator-run.log`, `emulator-35-run.log`, and `C:/Users/user/AppData/Local/Temp/AndroidEmulator/emu-crash-35.6.11.db/reports/26e11622-35c7-48df-ab08-47fa775c8d4b.dmp`. Temporary crash dumps may later be removed by OS cleanup. [Google emulator troubleshooting](https://developer.android.com/studio/run/emulator-troubleshooting)

## Build, signing, lint and normal test commands

Run from the repository with `environment.ps1` loaded. Use the supplied project wrapper scripts rather than a globally installed Gradle version.

| Command | Purpose |
|---|---|
| `./gradlew.bat :app:assembleDebug --console=plain` | Debug APK, LeakCanary and debuggable app |
| `./gradlew.bat :app:assembleRelease --console=plain` | Minified/resource-shrunk release APK |
| `./gradlew.bat :app:bundleRelease --console=plain` | AAB task for future bundle validation; not run in the original audit |
| `./gradlew.bat :app:lintDebug --console=plain` | Full debug lint; expected to fail on the audited snapshot |
| `./gradlew.bat :app:lintRelease --console=plain` | Full release lint, separate from the narrower vital checks |
| `./gradlew.bat :app:testDebugUnitTest --console=plain` | Unit tests; currently NO-SOURCE |
| `./gradlew.bat :app:connectedDebugAndroidTest --console=plain` | Repository instrumentation tests once they exist; not the standalone audit harness |
| `./gradlew.bat :app:dependencies --configuration debugRuntimeClasspath` | Resolve/inspect the actual runtime dependency graph |
| `./gradlew.bat --stop` | Stop Gradle daemons when diagnosing a toolchain change |

To reproduce the combined audit gate and retain its output:

```powershell
./gradlew.bat :app:assembleDebug :app:testDebugUnitTest :app:lintDebug :app:assembleRelease --continue --no-daemon --console=plain *> "$evidenceDir/gradle-audit.log"
$auditBuildExit = $LASTEXITCODE
Get-Content -LiteralPath "$evidenceDir/gradle-audit.log" -Tail 60
Write-Output "Gradle exit: $auditBuildExit"
```

`--continue` permits independent tasks to complete; it does not hide failures. The original command exited 1 because lint failed even though both APKs were produced. `NO-SOURCE` is not a passing test suite. Do not disable lint or introduce a blanket baseline simply to call the audit green. Most opt-in errors require reviewed API-boundary annotations; some other lint findings need manual triage. [Lint evidence and false-positive analysis](analyze.md#android-lint-results-and-manual-triage)

Outputs:

| Artifact | Repository path |
|---|---|
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` |
| Release APK | `app/build/outputs/apk/release/app-release.apk` |
| Release mapping | `app/build/outputs/mapping/release/mapping.txt` |
| Lint HTML / XML / text | `app/build/reports/lint-results-debug.html`, `.xml`, `.txt` |
| Detailed lint text | `app/build/intermediates/lint_intermediate_text_report/debug/lintReportDebug/lint-results-debug.txt` |
| Debug merged manifest | `app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml` |
| Release merged manifest | `app/build/intermediates/merged_manifests/release/processReleaseManifest/AndroidManifest.xml` |
| Generated/compiled debug code | `app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes` |
| Room schemas | `app/schemas/com.fanok.audiobooks.room.AppDatabase/` |
| Gradle problem report | `build/reports/problems/problems-report.html` |

Preserve mapping files alongside the exact release APK if investigating obfuscated crashes. Intermediate paths can change with AGP; search current build outputs rather than assuming a path from another version. `clean` removes useful build evidence and compiled classes used by the standalone harness, so it should not be the first troubleshooting step.

### Signing and the initial build failure

The first compile reached signing and failed because `C:\Users\user\.android\debug.keystore` was absent. `app/build.gradle` explicitly points both debug and release at that file through `signingConfigs.release`. A standard debug key was generated; release verification identified `CN=Android Debug, O=Android, C=US`.

For a new machine, generate this **only if absent**:

```powershell
$auditKeystore = Join-Path $env:USERPROFILE '.android/debug.keystore'
if (-not (Test-Path -LiteralPath $auditKeystore)) {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $auditKeystore) | Out-Null
    & "$env:JAVA_HOME/bin/keytool.exe" -genkeypair -keystore $auditKeystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname 'CN=Android Debug,O=Android,C=US'
}
```

These are the conventional local debug credentials, not production release credentials. A fresh machine generates a different signing identity; a matching alias/password does not make its certificate equal. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` requires resolving the signer mismatch or deliberately removing the old installation and its data. Do not overwrite an existing keystore as a routine fix. Production signing must be repaired before distribution; see C15 in [analyze.md](analyze.md).

For dependency/build errors, use `--stacktrace`, then targeted `--info` or `dependencyInsight`; verify Google Maven, Maven Central and JitPack reachability before changing versions. Keep certificate validation enabled. The audit needed no NDK installation despite packaging prebuilt Conscrypt `.so` files.

## Standalone Android audit probes

The external harness is at **`$auditRoot/runtime-harness`**. Its Java source is `src/com/fanok/audit/harness/ProbeRunner.java`; package `com.fanok.audit.harness`; target package `com.fanok.audiobooks`. It was compiled manually using javac, jar, d8, aapt2, zipalign and apksigner. It executes actual target-app classes rather than copies of their algorithms.

**These are defect-reproduction probes. PASS means the expected defective outcome was reproduced.** After fixing a bug, change its assertions into a normal regression test; a failure of the old defect assertion may be the desired result.

Reserve the emulator before running instrumentation: it restarts the target process and must not overlap interactive tests, playback experiments or another agent's UI automation. Use this disposable AVD rather than an installation with a real user's library.

```powershell
./gradlew.bat :app:assembleDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build the target debug app first' }
& "$auditRoot/runtime-harness/build.ps1"

& $adb -s $auditSerial install -r .\app\build\outputs\apk\debug\app-debug.apk
& $adb -s $auditSerial install -r "$auditRoot/runtime-harness/audiobook-audit-probes.apk"

# Six isolated production-method probes.
& $adb -s $auditSerial shell am instrument -w -r 'com.fanok.audit.harness/com.fanok.audit.harness.ProbeRunner' |
    Set-Content -Encoding UTF8 "$evidenceDir/instrumentation-default.txt"

# One combined real-service HTTP error-recovery/buffering probe.
& $adb -s $auditSerial shell am instrument -w -r -e mode http 'com.fanok.audit.harness/com.fanok.audit.harness.ProbeRunner' |
    Set-Content -Encoding UTF8 "$evidenceDir/instrumentation-http.txt"
```

Inspect the full output, `probe_failures` and instrumentation completion; do not rely only on the shell exit code. Original final runs had `probe_failures=0` and `INSTRUMENTATION_CODE: -1`.

| Mode | Verified original result |
|---|---|
| Default: actual cache exporter | Requesting 32-byte chapter 2 exported 63 bytes containing both sibling resources |
| Default: actual migrations | Saved's unique-index count went 1 → 0 |
| Default: actual Room open | Rejected missing unique index; transaction rolled back to fixture version 15 with sentinel row retained |
| Default: actual restore method | `{}` returned success and emptied four isolated seeded tables |
| Default: actual player initialization | Accepted buffer values `1`, `00`, `000` threw load-control validation errors |
| Default: actual Hourglass timer | 1,109 ms baseline became 102 ms after duplicate resume calls |
| HTTP: actual bound service | Four 503 requests led to terminal IDLE; app/session Play made no new request; explicit prepare caused request five and recovered |
| HTTP: pause while buffering | Pause left play intent true; releasing the response started playback without another Play |

The HTTP server runs **inside the emulator at `127.0.0.1`**, serves generated silent WAV data and supports ranges. The probe holds both local-service and MediaBrowser bindings to prevent unrelated unbind teardown from invalidating the experiment. It explicitly releases the player before stopping the service to avoid the known teardown crash; that cleanup is not evidence C01 has been fixed.

Default probes use disposable `audit_*` databases/cache/output, temporarily substitute and restore Room/cache objects, and avoid the broad deletion helper. HTTP mode snapshots/restores playback preferences and static state. A crash/aborted process can interrupt restoration, which is another reason to use an isolated device. Migration fixtures are controlled shapes derived from schema 17, not recovered historical version-15 files. Export fixtures contain recognizable byte strings, not decoded audiobook chapters. Full limits are in [instrumentation evidence](analyze.md#android-instrumentation-evidence).

### Portability and maintenance of the harness

- The current release is minified and not suitable for its private-name reflection. Reinstall the unminified debug app first; app and helper must be signed with the same key.
- `build.ps1`, `javac.args`, `d8.args` and `classpath.txt` contain absolute paths, including hashed Gradle cache paths. The compile classpath was an audit expedient and contains more cached jars than necessary.
- `build.ps1` does **not** regenerate its arg files, `target-classes-compile-only.jar`, or `assets/schema17.json`. After moving machines, cleaning caches, changing dependencies/classes/schema, refresh those inputs and review the probe assumptions before rebuilding.
- `assets/schema17.json` matched the source exported schema at the audit snapshot, SHA-256 `12dcfa7a898d08488919cc3963e88002df8f9ce01b67794ef662d2c7d2dc0630`. Matching that old hash is not a requirement after an intentional schema change.
- Compiler configuration: Android API 36 jar, Build-Tools 35.0.0, Java source/target 8, D8 minimum API 23; helper manifest minimum 23 / target 34.
- Move durable regression tests into a normal Gradle `androidTest` source set/module with explicit dependencies. The external manual build is useful evidence, not a finished portable CI test system.

Preserved logs: `instrumentation-final-run.txt` and `instrumentation-http-run.txt`. `instrumentation-first-run.txt` contains two **harness fixture-parser** failures concerning an optional `indices` array; they were corrected before the final run and are not application defects.

## Observe and interact with the app

Use the bootstrap variables from Quick Start. The commands below are suitable for headless operation; they do not require a desktop emulator window.

### Screenshots and UI hierarchy

```powershell
& $adb -s $auditSerial shell screencap -p /sdcard/audiobook-screen.png
& $adb -s $auditSerial pull /sdcard/audiobook-screen.png "$evidenceDir/screen.png"
& $adb -s $auditSerial shell uiautomator dump /sdcard/audiobook-window.xml
& $adb -s $auditSerial pull /sdcard/audiobook-window.xml "$evidenceDir/window.xml"

[xml]$window = Get-Content -Raw -LiteralPath "$evidenceDir/window.xml"
$window.SelectNodes('//node') |
    Where-Object { $_.text -or $_.'content-desc' } |
    Select-Object text,resource-id,content-desc,bounds,clickable
```

Use the current hierarchy's bounds to choose coordinates. Tool-rendered previews can be scaled; do not assume preview pixels equal device coordinates. Retain screenshots with the matching XML and action sequence. The default audit image was 1080×2340 at 440 dpi.

```powershell
& $adb -s $auditSerial shell input tap 540 1200
& $adb -s $auditSerial shell input swipe 540 1900 540 700 450
& $adb -s $auditSerial shell input keyevent KEYCODE_BACK
```

The coordinates above illustrate the syntax, not a permanent button location. Wait for focus/layout after opening a search field. One early automation attempt typed before focus settled and produced `984` rather than `1984`; that was not treated as proof of an app text-entry defect. ASCII `input text` is useful for simple fixtures; use a proper input method/UI automation for Unicode rather than assuming it handles Cyrillic correctly.

The tested search entry contract avoids typing races:

```powershell
& $adb -s $auditSerial shell am start -W -n 'com.fanok.audiobooks/.activity.SearchableActivity' -a android.intent.action.SEARCH --es query 1984
```

A default-size Akniga catalog and multi-provider search were observed successfully. That does not certify pagination, rapidly changing queries, source failures, TV/large-screen layouts or accessibility. For future large-font/rotation work, record original settings and restore them afterward; do not leave a shared AVD unexpectedly altered.

### Logs and screen recordings

```powershell
& $adb -s $auditSerial logcat -d -v threadtime |
    Set-Content -Encoding UTF8 "$evidenceDir/logcat.txt"
& $adb -s $auditSerial logcat -b crash -d -v threadtime |
    Set-Content -Encoding UTF8 "$evidenceDir/crash-buffer.txt"
& $adb -s $auditSerial logcat -v threadtime 'AndroidRuntime:E' 'MediaPlayerService:D' 'ExoPlayerImpl:I' 'ExoPlayerImplInternal:E' '*:S'
```

The last command streams until stopped. Use `-d` for a finite snapshot. Capture existing logs before `logcat -c`; several old audit scripts clear the buffer themselves. After a crash/restart, a PID-filtered log stream may still be following the old PID—recheck it.

For a bounded video, run recording in one terminal and interact from another:

```powershell
& $adb -s $auditSerial shell screenrecord --time-limit 20 /sdcard/audiobook-ui.mp4
& $adb -s $auditSerial pull /sdcard/audiobook-ui.mp4 "$evidenceDir/ui.mp4"
```

Screen recording is a future diagnostic recipe; it was not used as evidence in the original audit. For a fuller diagnostic bundle:

```powershell
& $adb -s $auditSerial bugreport "$evidenceDir/bugreport.zip"
```

Bug reports/logs can include device and application data. Inspect/redact sensitive values before sharing them. [ADB tools](https://developer.android.com/tools/adb), [Android bug reports](https://developer.android.com/studio/debug/bug-report)

### PowerShell binary-output rule

On this machine, PowerShell is **5.1**. Do not save binary `adb exec-out` output with `>` or `Out-File`; text decoding can corrupt PNG, HPROF, APK or tar files. Use `adb pull` for accessible device paths. For app-private data, stream through Python to a file opened in binary mode as shown below. Text logs can safely use `Set-Content -Encoding UTF8`.

## Crash, ANR, debugger and data inspection

### Application-owned crash reports

The application writes private files through `App.getCrashFile()` and `App.getAnrFile()`:

- `files/crash.txt`: uncaught exception report.
- `files/anr.txt`: custom heartbeat-watcher report.
- Room database name: `audioBooksDB`, under `databases/`.
- Playback preferences: `shared_prefs/STORAGE.xml`.
- Default preferences normally use `shared_prefs/com.fanok.audiobooks_preferences.xml`; list the directory to confirm.

With the **debug** APK installed:

```powershell
& $adb -s $auditSerial shell run-as $appPackage ls files
& $adb -s $auditSerial shell run-as $appPackage cat files/crash.txt |
    Set-Content -Encoding UTF8 "$evidenceDir/app-crash.txt"
& $adb -s $auditSerial shell run-as $appPackage cat files/anr.txt |
    Set-Content -Encoding UTF8 "$evidenceDir/app-anr.txt"
& $adb -s $auditSerial shell dumpsys activity lastanr |
    Set-Content -Encoding UTF8 "$evidenceDir/system-lastanr.txt"
```

A missing file simply means that report has not been written in the current installation. The custom watcher is not equivalent to Android's ANR detector. Correlate timestamps and system logs. The app's custom uncaught-exception handler does not delegate normal termination; the audit observed a fatal main-thread exception while the same process remained alive. Therefore **a PID still existing is not proof the application is healthy**. Preserve evidence, then restart deliberately for the next test. See C01/C02 in [analyze.md](analyze.md).

### Debugger attachment

Install debug, launch the app, then attach Android Studio's debugger to `com.fanok.audiobooks`. If Studio is installed later, open this repository, select the existing SDK root and Gradle JDK 17, and select the debug variant. Full IDE installation is optional for the command-line workflows.

To break before normal startup in a dedicated debug session:

```powershell
& $adb -s $auditSerial shell am set-debug-app -w $appPackage
& $adb -s $auditSerial shell am force-stop $appPackage
& $adb -s $auditSerial shell am start -n 'com.fanok.audiobooks/.activity.MainActivity'
# Attach the debugger, then remove the wait setting when finished.
& $adb -s $auditSerial shell am clear-debug-app
```

If the app appears stuck on “waiting for debugger,” check that setting. Do not combine debugger pauses with timing measurements. LeakCanary's default automatic dumping is also affected by an attached debugger. [Android debugging](https://developer.android.com/studio/debug)

Useful breakpoints include player state/error callbacks, service `onDestroy`, search generation/completion, cache reinitialization, download export completion and restore validation. A temporary debug-only StrictMode policy can help find main-thread I/O, but it was not added during the audit; keep such a change intentional and separately reviewable.

### Private files and database snapshots

`run-as` normally requires a debuggable app. Failure with the currently installed release APK is expected, not a broken SDK. Android Studio Device Explorer/Database Inspector are convenient for debug data and Room queries. [Device Explorer](https://developer.android.com/studio/debug/device-file-explorer), [Database Inspector](https://developer.android.com/studio/inspect/database)

For a consistent file-level snapshot, finish the scenario and stop the test app first. This is **not** a leak-measurement step: force-stopping discards the heap being investigated. Copy the database plus its WAL/SHM sidecars when present; copying only an open SQLite `.db` file can produce an incomplete view.

```powershell
& $adb -s $auditSerial shell am force-stop $appPackage
& $adb -s $auditSerial shell run-as $appPackage ls databases
& $adb -s $auditSerial shell run-as $appPackage ls shared_prefs
```

Binary-safe archive extraction, after the bootstrap variables are set:

```powershell
@'
import pathlib, subprocess, sys
adb, serial, output = sys.argv[1:]
with pathlib.Path(output).open('wb') as stream:
    subprocess.run([
        adb, '-s', serial, 'exec-out', 'run-as', 'com.fanok.audiobooks',
        'tar', '-cf', '-', 'databases', 'shared_prefs', 'files'
    ], stdout=stream, check=True)
'@ | python - $adb $auditSerial "$evidenceDir/app-private.tar"
```

The example archives only this app's private directories and may include sensitive preferences. Adapt only after listing what exists. To extract a single discovered private HPROF or database file, replace the `tar` arguments with `cat` and the exact private path, keeping Python's binary file handle. Avoid arbitrary mutations of the live database or manual preference injection on a user's installation. The old generated-audio probes intentionally used a fresh emulator and are not general-purpose import tools.

## Memory and resource leak investigation

### Reproducible observation protocol

1. Use a debug build for Java leak investigation. Record the APK/version, AVD, input, relevant preferences and initial state.
2. Warm the app once, return to a defined screen and allow initial requests to settle. Capture a baseline.
3. Repeat the same scenario 10–20 times: open/close book details, rotate, navigate away/back, search/cancel, start/pause/stop audio, toggle timer, or complete/remove a fixture download.
4. Return to the **same** idle state without force-stopping. Allow callbacks/network work and leak analysis to settle, then compare resources.
5. Establish a retained-reference/resource ownership path. Rising memory alone can reflect caches, allocator behavior or pending work; it is not proof of a leak.
6. Repeat with one variable changed. Validate performance, thermal, battery and audio-routing consequences on physical hardware separately.

Avoid clearing app state, restarting the process or running memory-altering diagnostics between the two samples unless that is the explicit experiment.

### Memory, processes, services, jobs and wake locks

```powershell
& $adb -s $auditSerial shell dumpsys meminfo -d $appPackage |
    Set-Content -Encoding UTF8 "$evidenceDir/meminfo.txt"
& $adb -s $auditSerial shell dumpsys procstats --hours 3 |
    Set-Content -Encoding UTF8 "$evidenceDir/procstats.txt"
& $adb -s $auditSerial shell dumpsys activity activities |
    Set-Content -Encoding UTF8 "$evidenceDir/activities.txt"
& $adb -s $auditSerial shell dumpsys activity services $appPackage |
    Set-Content -Encoding UTF8 "$evidenceDir/services.txt"
& $adb -s $auditSerial shell dumpsys jobscheduler |
    Set-Content -Encoding UTF8 "$evidenceDir/jobs.txt"
& $adb -s $auditSerial shell dumpsys power |
    Set-Content -Encoding UTF8 "$evidenceDir/power.txt"
```

Compare Java/native heap, PSS, activity/view counts, lingering services, active jobs and wake-lock ownership at equivalent idle points. `meminfo` is a snapshot; `procstats` aggregates over time. Fields and visibility differ by Android version. [Android dumpsys reference](https://developer.android.com/tools/dumpsys)

For debug-only thread/descriptor evidence:

```powershell
$appPid = (& $adb -s $auditSerial shell pidof -s $appPackage).Trim()
if (-not $appPid) { throw 'Start the debug app before process inspection' }
& $adb -s $auditSerial shell run-as $appPackage cat "/proc/$appPid/status" |
    Set-Content -Encoding UTF8 "$evidenceDir/process-status.txt"
& $adb -s $auditSerial shell run-as $appPackage ls "/proc/$appPid/fd" |
    Set-Content -Encoding UTF8 "$evidenceDir/file-descriptors.txt"
& $adb -s $auditSerial shell run-as $appPackage ls "/proc/$appPid/task" |
    Set-Content -Encoding UTF8 "$evidenceDir/threads.txt"
```

Use **`$appPid`, not `$PID`**: PowerShell's automatic `$PID` variable is read-only and names are case-insensitive. Some images restrict `/proc` inspection even for debug apps; permission denial is not evidence resources were released. The release build's `run-as` rejection is intentional platform behavior. [AOSP run-as implementation](https://android.googlesource.com/platform/system/core/+/refs/heads/master/run-as/run-as.cpp)

### LeakCanary

LeakCanary 2.14 is already automatically installed in debug builds through `app/build.gradle`; adding another initializer/dependency is unnecessary. It is absent from the release build used for the final smoke check. [LeakCanary setup](https://square.github.io/leakcanary/getting_started/)

```powershell
& $adb -s $auditSerial logcat -d -v threadtime 'LeakCanary:D' '*:S' |
    Set-Content -Encoding UTF8 "$evidenceDir/leakcanary.txt"
& $adb -s $auditSerial shell input keyevent KEYCODE_HOME
```

After the lifecycle scenario, backgrounding can allow analysis to trigger. Defaults watch for retention after roughly five seconds and use different dump thresholds when visible versus backgrounded. An attached debugger suppresses automatic dumps by default in 2.14; test environments can also change dump behavior. Detach the debugger for a normal manual leak run. A missing notification is not proof of leak freedom. [Detection workflow](https://square.github.io/leakcanary/fundamentals-how-leakcanary-works/), [pinned 2.14 configuration](https://raw.githubusercontent.com/square/leakcanary/v2.14/leakcanary-android-core/src/main/java/leakcanary/LeakCanary.kt)

The audit observed LeakCanary reporting a retained service object but did not capture a heap path establishing every proposed leak. Preserve that distinction. LeakCanary does not replace native allocation, descriptor, socket, disk-cache, wake-lock or executor analysis.

Project-specific objects worth following: destroyed Activities/fragment views held by presenters or Rx observers; Handler/timer callbacks; MediaSession and audio effects; caller-owned download executor pools; manually opened image streams; old SimpleCache instances during folder changes. Check allocation/retention roots before labeling all process-lifetime singletons as leaks.

### Heap dump

With the debug app running:

```powershell
& $adb -s $auditSerial shell am dumpheap -g $appPackage /data/local/tmp/audiobooks.hprof
# Wait for dump completion, then pull the finished binary file.
& $adb -s $auditSerial pull /data/local/tmp/audiobooks.hprof "$evidenceDir/audiobooks.hprof"
```

`-g` requests GC before the dump; this changes the measured state. Heap capture can pause the app and temporarily increase memory use. Keep it separate from timing/audio-glitch measurements. In Android Studio Profiler, inspect retained size, dominators and paths to GC roots for objects that should have died. Save the capture before ending the profiler session. [Heap dump analysis](https://developer.android.com/studio/profile/capture-heap-dump), [Android Profiler](https://developer.android.com/studio/profile)

Find LeakCanary-generated dumps rather than assuming its private path:

```powershell
& $adb -s $auditSerial shell run-as $appPackage find . -name '*.hprof'
```

Use Device Explorer or the binary-safe private extraction technique above for an exact discovered path. Normal `adb pull /data/user/0/...` generally cannot read private files. Optional third-party heap viewers were not installed during the audit; Android Studio's Java heap analysis and existing LeakCanary are the first available paths once Studio is installed.

## Playback, streaming, downloads and search diagnostics

### Inspect the right media session

```powershell
& $adb -s $auditSerial shell dumpsys media_session |
    Set-Content -Encoding UTF8 "$evidenceDir/media-session.txt"
& $adb -s $auditSerial shell dumpsys audio |
    Set-Content -Encoding UTF8 "$evidenceDir/audio.txt"
& $adb -s $auditSerial shell dumpsys media.audio_flinger |
    Set-Content -Encoding UTF8 "$evidenceDir/audio-flinger.txt"
& $adb -s $auditSerial shell dumpsys media.audio_policy |
    Set-Content -Encoding UTF8 "$evidenceDir/audio-policy.txt"
```

Inspect the block whose package is `com.fanok.audiobooks`. The device dump also contains Bluetooth/media-system sessions; disconnected errors in those blocks are not automatically app errors. Correlate session state, actual player state, service lifetime, audio focus/routing and request timestamps. No-audio playback state is not a listening test. Advanced AudioFlinger audio capture requires additional platform configuration; these dumps do not record audible output. [AOSP audio debugging](https://source.android.com/docs/core/audio/debugging)

`MediaPlayerService.java` currently has its Media3 `EventLogger` attachment commented out. Filtering an EventLogger tag cannot create events that are not being recorded. A future **debug-only code change** can attach an analytics listener and record error causes, buffering, loads and cache/upstream bytes; it was not added by this documentation work. [Media3 debug logging](https://developer.android.com/media/media3/exoplayer/debug-logging), [analytics](https://developer.android.com/media/media3/exoplayer/analytics)

Useful test records include stable book/chapter/resource identity, current state, play intent, position, error code/cause, retry count, upstream bytes versus cache bytes, range requests and export outcome. Avoid recording passwords, cookies, reusable tokens or complete signed URLs into shared logs.

### Controlled HTTP and offline cases

Prefer generated audio/local fixtures for fault injection. A server running on the **Windows host** is reachable from the Android emulator using `10.0.2.2`; `127.0.0.1` inside Android refers to Android itself. The successful HTTP instrumentation mode uses an in-emulator loopback server, not the host alias. [Emulator networking](https://developer.android.com/studio/run/emulator-networking)

For a host-side test server listening on port 18765, an alternative is:

```powershell
& $adb -s $auditSerial reverse tcp:18765 tcp:18765
# The device can then reach that forwarded host service at 127.0.0.1:18765.
# Remove only this mapping when finished.
& $adb -s $auditSerial reverse --remove tcp:18765
```

The forwarding recipe was not needed by the successful harness. Generic `python -m http.server` does not establish correct HTTP Range, partial-failure, token-expiry or HLS behavior. Use a fixture server implementing the exact response/headers being tested.

Important scenarios and what to measure:

| Scenario | Required evidence |
|---|---|
| Terminal 503 then server recovery | Actual terminal player state, request counts before/after normal Play and explicit positive-control prepare |
| Pause while buffering | Pause command, `playWhenReady`, held/released HTTP response and later READY state |
| Expired URL / 401 / 403 | Source refresh policy, bounded retry and preserved chapter/position; do not conflate every failure with a timeout |
| Partial/ranged transfer | Range header and returned Content-Range, interruption point, resume bytes and output integrity |
| Offline completed item | No upstream requests while replaying; complete asset, chapter mapping and correct clipping survive process restart |
| Partial book download | Completed/selected/total chapters and explicit failures; a saved row alone is not complete-book proof |
| HLS | Master/media playlist, segments, initialization data, keys, byte ranges, changing credentials and correct per-request context |
| Cache/storage change | Existing readers/exports finish or transition safely; no use-after-release or stale manager generation |
| File/SAF failure | Readability, persisted permission, free space, missing file/span and honest retry/error state |
| Fast search | Time to first usable result, per-provider completion, late old-query callbacks, requests still running after leaving |

The current player reads the explicit-download cache without writing ordinary streamed bytes into it. Do not interpret every repeated stream HTTP request as cache corruption. Decide whether bounded stream caching is actually a product requirement and measure its benefit separately. Media3 cache bytes are not standalone complete media files; use exact cache keys/ranges and the proper playback/export path. [Media3 downloads](https://developer.android.com/media/media3/exoplayer/downloading-media), [network stacks/caching](https://developer.android.com/media/media3/exoplayer/network-stacks)

The audit's bounded `provider_probe.py` is useful for root reachability only. It makes one unauthenticated GET per source, normal TLS validation, at most three workers, 15-second socket timeout and a 128-KiB body prefix; no redirects, login or media download. Four roots failed certificate-expiry validation and one challenged the client at the recorded time. Re-run results are a new network/time/client snapshot, not a provider-wide verdict. The app uses different headers and unsafe TLS overrides, so its result may differ. Do not disable certificate validation in test tools to make those failures disappear.

### Restart and recovery semantics

```powershell
# Explicit cold-start reset; terminates the package and changes stopped state.
& $adb -s $auditSerial shell am force-stop $appPackage

# Ordinary kill request only affects processes Android considers safe to kill.
& $adb -s $auditSerial shell am kill $appPackage
```

These are **different experiments**, not commands to run sequentially as a general recovery recipe. A foreground playback service may survive `am kill`; verify the PID and service state. Force-stop is not equivalent to low-memory process death, reboot, normal unbind, or `onDestroy()` cleanup. It can suppress scheduled restart behavior until the user starts the app again. Test each condition separately and document which actually occurred.

An early HTTP probe without a retained service binding released the player while buffering and never reached terminal error. It was inconclusive. The later harness held both local and media-browser bindings and used real request-count/state assertions. Preserve this distinction when reproducing error-handling failures.

## Rendering and performance traces

These are additional diagnostic recipes; the original audit did not publish benchmark or battery claims.

Reset frame stats, execute a bounded interaction, then capture:

```powershell
& $adb -s $auditSerial shell dumpsys gfxinfo $appPackage reset
# Perform the same scrolling/navigation/search scenario each time.
& $adb -s $auditSerial shell dumpsys gfxinfo $appPackage framestats |
    Set-Content -Encoding UTF8 "$evidenceDir/framestats.txt"
```

For a 20-second system trace, start recording in one terminal and perform the workload from another:

```powershell
& $adb -s $auditSerial shell perfetto -o /data/misc/perfetto-traces/audiobooks.perfetto-trace -t 20s sched freq idle am wm gfx view binder_driver dalvik input
& $adb -s $auditSerial pull /data/misc/perfetto-traces/audiobooks.perfetto-trace "$evidenceDir/audiobooks.perfetto-trace"
```

The installed Android 14 image exposes `perfetto`; its help was inspected. Trace categories vary by image. Use `adb shell perfetto --help` if a category/configuration is unsupported. Open the local trace in [Perfetto UI](https://ui.perfetto.dev) and examine main-thread work, blocking I/O, repeated callbacks and scheduler activity. Use a bounded trace duration because interrupting a noninteractive `adb shell` does not always stop its device-side process. [Perfetto Android recording guide](https://perfetto.dev/docs/getting-started/system-tracing)

Debug builds, LeakCanary, instrumentation, emulator graphics and the host's CPU load add overhead. Use functional emulator tests to find/control causes, then validate latency, battery, heat, codecs and headset behavior on representative physical devices with an appropriate release-like/profileable configuration. A single skipped-frame log or large PSS value is not a performance diagnosis.

## APK and native-library verification

Verify signing and APK ZIP alignment independently:

```powershell
& "$env:ANDROID_HOME/build-tools/35.0.0/apksigner.bat" verify --print-certs .\app\build\outputs\apk\release\app-release.apk
& "$env:ANDROID_HOME/build-tools/35.0.0/zipalign.exe" -v -c -P 16 4 .\app\build\outputs\apk\release\app-release.apk
Get-FileHash -LiteralPath .\app\build\outputs\apk\debug\app-debug.apk,.\app\build\outputs\apk\release\app-release.apk -Algorithm SHA256
& $adb -s $auditSerial shell getconf PAGE_SIZE
```

Audit artifacts:

| APK | Bytes | SHA-256 |
|---|---:|---|
| Debug | 23,889,229 | `71e5c5fcd9f9393f779b051fe3e6420b427a732ca464d7a890adf48d4ca213e5` |
| Release | 12,451,669 | `131bf986f9967fb4639bbff68f48ffedb8ffaf8571dee9317242d102dc0a8253` |

Rebuilds can legitimately produce different hashes. Record the new artifact and mapping rather than treating these old values as timeless acceptance criteria.

Both audit APKs passed 16 KB ZIP entry alignment. Their Conscrypt `libconscrypt_jni.so` binaries still had 4 KB ELF LOAD alignment, including arm64-v8a and x86_64. **Passing zipalign does not validate ELF layout or runtime page-size compatibility.** [Native inspection, exact output and reproducible Python](analyze.md#built-apk-native-compatibility-and-merged-manifest-verification)

No NDK was needed for the original binary check: it used Python `zipfile`/`struct` to inspect ELF program headers. If an NDK is installed for later investigation, its `llvm-objdump.exe -p` can inspect extracted libraries. Test every relevant ABI and APKs generated from the final AAB. A device must report `16384` from `getconf PAGE_SIZE` to validate native 16 KB operation; the API-34 audit AVD is not that device. [Android 16 KB guidance](https://developer.android.com/guide/practices/page-sizes)

Inspect **merged** manifests when diagnosing permissions, exported components and schedulers. The audit found boot/wake/network permissions supplied through manifest merging, but the chosen download scheduler's required service/action integration was absent. Source-manifest searching alone can produce both false positives and missed integration failures.

## Saved scripts and evidence

Everything in this table is under `$auditRoot` unless noted. Preserve original evidence; use timestamped copies for new experiments. These files are outside the repository and are not restored by cloning it.

| Location | Purpose and cautions |
|---|---|
| `environment.ps1` | Session-local Java/SDK/AVD/PATH setup; absolute paths |
| `start-audit-emulator.ps1` | Known launch configuration; reuses 5554, overwrites fixed launch logs on a new process |
| `install-aehd.ps1` | Elevated driver-install wrapper; not a daily test command |
| `temurin-package.json`, three downloaded ZIPs | Exact download provenance and retained installers |
| `sdk-licenses.log`, `sdk-install.log`, `avd-create.log` | SDK/AVD setup records, including duplicate-package/metadata warnings |
| `gradle-debug-audit.log` | First build's missing-keystore signing failure |
| `gradle-full-audit.log` | Both assemblies, NO-SOURCE unit task and failing lint gate |
| `runtime-harness/` | Source, manifest, arg files, copied schema, APK and build script |
| `runtime-harness/instrumentation-final-run.txt` | Six successful assertions of defective production behavior |
| `runtime-harness/instrumentation-http-run.txt` | Successful bound-service HTTP failure/recovery and buffering assertions |
| `runtime-harness/instrumentation-first-run.txt` | Superseded harness-fixture parsing mistakes; not app failures |
| `runtime-startup.png`, `runtime-catalog.png`, `runtime-search-final.png` | Captured debug UI evidence |
| `runtime-release.png`, `runtime-release-window.xml` | Final minified-release catalog smoke check |
| `runtime-pause-*.txt`, `runtime-pause-events.json` | Real media-session double-pause evidence |
| `runtime-stopservice-crash.txt`, `runtime-playing-before-stop.txt` | Actual service-stop rejection and preceding playback state |
| `provider_probe.py`, `provider-probe.json` | Bounded public homepage probes; script overwrites its JSON |
| `playback_probe.py` | Generated WAV/preferences experiment; modifies STORAGE and does not restore original user preferences |
| `pause_probe.py` | Depends on generated fixture/prior crash; overwrites event/state logs and force-stops the app |
| `stream_error_probe.py` | Earlier inconclusive unbound HTTP experiment; superseded by harness HTTP mode |
| `runtime-http-error-*` | Evidence from that inconclusive old script, not proof of C04 |
| `audit-tone.wav`, `fixture-STORAGE.xml`, `fixture-error-STORAGE.xml` | Synthetic fixture assets; not a user's media library |
| `emulator-run.log`, `emulator-35-run.log` | Native failures before CPU acceleration was installed |
| `aehd-package-install.log`, `aehd-driver-install.log` | Driver provisioning and verification |
| `emulator-accelerated.stdout.log`, `.stderr.log` | Working primary emulator; may be overwritten on relaunch |
| `emulator-audio*`, `avd-audio-create.log` | Separate audio-enabled boot evidence, not a sound-quality test |
| `review-fragments/` | All 14 original `audit_*.tmp.md` area/model/runtime drafts, moved out of repo |
| `assemble_report.py`, `report-intro.md`, `report-end.md` | Snapshot-authoring tools; **do not run unchanged** as a current maintenance workflow |
| `validate_report.py` | Snapshot Markdown/redaction/lint-count checker; fixed count assertions need revision after app fixes |

The old playback/pause/HTTP Python scripts hardcode `emulator-5554`, the tools path and debug `run-as` usage; some clear logcat and write application preferences directly. The old HTTP script restores **fixture** preferences, not the original user's preferences. Prefer the isolated, state-restoring harness on a disposable AVD.

`assemble_report.py` still reads mandatory `audit_*.tmp.md` files from the repository root, but those files were moved to `review-fragments/`. Optional sections can also be omitted if their former paths are absent. It is not safe to regenerate the integrated [analyze.md](analyze.md) blindly: later corrections and evidence may be lost. Edit the maintained document deliberately or first repair/verify the assembly inputs. Similarly, `validate_report.py` hardcodes 84 lint errors and 386 warnings; a changed count after a fix is not inherently a regression.

For future portability, preserve the external harness's **source, build script, arg/classpath inputs and fixture assets**, not merely its APK. Do not treat the huge cached classpath as a portable dependency lock. The best long-term home for maintained regression coverage is the repository's normal Gradle test configuration.

## FAQ and lessons learned

| Symptom/question | Explanation and action |
|---|---|
| Java/ADB/sdkmanager is not found | Dot-source `environment.ps1` in this terminal. Verify `Get-Command java,adb` and explicit SDK paths. Child processes inherit only the environment of their launching session. |
| PowerShell refuses to run a helper script | Inspect `Get-ExecutionPolicy -List` and the script's origin. This host's CurrentUser policy was RemoteSigned; it was not changed for this README. Review downloaded scripts and follow local policy rather than changing machine-wide execution policy as a routine workaround. |
| Java source level is 8—why install JDK 17? | Source language/desugaring and the JVM running Gradle are different. JDK 17 is the verified build runtime. Do not select an arbitrary bundled JBR solely because an IDE is installed. |
| SDK location not found | Load ANDROID_HOME or provide an untracked `local.properties`; do not point to the command-line-tools subdirectory as the SDK root. |
| Missing debug.keystore for config named release | The app explicitly uses the local debug key for both variants. Generate only if absent; production signing remains a separate defect. |
| Both APKs exist but Gradle returned 1 | Full lint failed. Check task results and the captured native exit code; output existence is not a green quality gate. |
| Unit tests say NO-SOURCE | There are no repository test source files for that task. Use the documented external probes and build real regression tests; do not claim tests passed. |
| Probe says PASS—was the bug fixed? | No. These probes assert the previously discovered bad outcome. Read its result and update the assertion after repairs. |
| Harness fails after dependency/cache/schema changes | Regenerate its machine-specific classpath/arg files and compile-only target jar, review fixture assumptions, and build/install the matching debug app. |
| Instrumentation cannot load/reflect a class | Confirm the unminified debug target, same signing identity, helper install, classpath and source version. Release reflection is not its supported setup. |
| `run-as: package not debuggable` | Release is installed. Install debug without casually uninstalling/clearing its data. |
| Emulator crashes before Android/app starts | Check native logs, acceleration and driver state. The original fix was AEHD, not changing application code. |
| ADB shows offline or boot property is empty | Wait/check boot logs. The launcher reuses offline 5554 rather than healing it. Preserve evidence before stopping only the affected emulator. |
| No emulator window | Primary run is intentionally headless. ADB screenshots work. Changing to a window requires a new launch; helper window behavior remains unverified. |
| No audible playback | Primary emulator used `-no-audio`. State/decoding success is not a speaker/headset test. Audio-enabled startup was separately verified. |
| SDK reports duplicate/inconsistent package locations | Canonical packages and `-2` duplicates coexist from concurrent installers. Use canonical paths and serialize future SDK changes. This did not cause the native crash. |
| SDK tools print deprecation warnings | Version 22 still provides tested legacy commands; current docs route new setups to Android CLI. Use installed help rather than mixing syntaxes. |
| `devices.xml` warning during AVD creation | This specific warning did not prevent creating/booting the audit AVD. Verify resulting configuration and boot rather than suppressing all warnings. |
| PowerShell reports NativeCommandError on a compiler note | Windows PowerShell can wrap stderr as an error record. Use the native exit code and task result to distinguish warnings/notes from failure. Do not assume every stderr line is fatal. |
| Screenshot/HPROF/archive looks corrupt | Check whether PowerShell text redirection processed binary output. Use adb pull or Python binary streaming. |
| Search input loses a character during automation | Wait for focus before typing or use the tested SEARCH intent for ASCII fixtures. Distinguish automation races from app defects. |
| Player disappeared before error recovery could be tested | Unbinding while not playing can release it. Hold the relevant service/browser bindings and inspect actual player identity/state, as HTTP harness mode does. |
| App process remains after a fatal exception | The custom crash handler can suppress normal termination. Check crash.txt/logcat and UI responsiveness; PID existence is insufficient. |
| Media dump contains Bluetooth ERROR states | Inspect the application package's session block. System sessions are not automatically app failures. |
| No LeakCanary notification | Check debug build, debugger attachment, pending work, threshold/background state and test environment. Absence of a dump is not proof of no leak. |
| Memory keeps rising | Compare equal idle states, distinguish intentional caches from retained owners, and inspect heap/resource paths. Do not force-stop away the evidence. |
| TLS homepage probe fails while app search returns books | Clients/headers/session differ and this app bypasses validation. Preserve strict TLS in diagnostics and classify the failure; do not infer the source is universally down. |
| `zipalign -P 16` passes—are native libraries compatible? | No. Inspect ELF LOAD alignment and run on a 16 KB image/device. Conscrypt failed ELF checks despite aligned APK entries. |
| Can the current AVD certify Play billing, TV, Android Auto, API 23 or 16 KB? | No. It is one API-34 default x86_64 image. Create/obtain the relevant supported images/devices and preserve separate results. |

## Checklist for the next session

1. Read this guide and the relevant [analyze.md](analyze.md) findings. Check current source changes before trusting old line numbers or assertions.
2. Inspect Git status; preserve user edits. Do not assume initially untracked application files were created by the current task.
3. Load the environment and confirm Java, wrapper, SDK, backend and actual ADB serial. Check which APK variant is installed.
4. Reserve the emulator with any parallel workers; do not run instrumentation and interactive UI work at the same time on one device.
5. Create a timestamped evidence directory; retain APK hash, build/lint output, device/API/ABI, script version, settings and scenario.
6. Reproduce narrowly before changing code. Keep observed app defects separate from fixture, host-emulator, network and tool failures.
7. Run the relevant tests once after the change, then broaden only where new failures/risks justify it. Convert successful bug demonstrations into regression tests that assert the repaired behavior.
8. Use stable book/chapter/resource IDs and isolated test files when investigating storage. Never exercise known broad deletion or malformed restore against a real library.
9. Review debug and minified release behavior separately. Preserve mapping/signer information; full lint still matters when assembly succeeds.
10. Record confirmed results and remaining limits in the maintained documents. Do not overwrite the old audit evidence or regenerate its report from stale fragments.

This README documents a working **local** environment plus reproducible evidence, not a production-release approval. Remaining application defects and the prioritized repair/test plan are maintained in [analyze.md](analyze.md).
