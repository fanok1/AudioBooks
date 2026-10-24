# AudioBooks

**English** · [Русский](readme.ru.md)

[![Build](https://github.com/korvin2000/AudioBooks/actions/workflows/build.yml/badge.svg?branch=master)](https://github.com/korvin2000/AudioBooks/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/korvin2000/AudioBooks?sort=semver&label=release)](https://github.com/korvin2000/AudioBooks/releases/latest)
[![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](#requirements)

![AudioBooks — your favorite audiobooks, always within reach](docs/assets/wide_logo.webp)

**Make room for books in your everyday life.** AudioBooks is a free Android app for discovering and listening to audiobooks from several online catalogs. Enjoy a story on your commute, download a book before a trip, or fall asleep listening with a sleep timer.

The idea is simple: bring book discovery, a personal library, and comfortable listening together in one place. Browse classics, science fiction, mysteries, memoirs, business and self-education titles, school curriculum books, and fairy tales and poems for children, then pick up where you left off.

[Download](#install) · [Discussion on 4PDA](https://4pda.to/forum/index.php?showtopic=978445) · [Watch on YouTube](https://www.youtube.com/watch?v=MlqR32Finf0) · [Report an issue](https://github.com/korvin2000/AudioBooks/issues)

<details>
<summary>Contents</summary>

- [What you can do](#what-you-can-do)
- [Screenshots](#screenshots)
- [Requirements](#requirements)
- [Install](#install)
- [Start listening](#start-listening)
- [Help & feedback](#help--feedback)
- [Contributing](#contributing)
- [Building from source](#building-from-source)
- [Credits](#credits)

</details>

## What you can do

- **Find your next book.** Browse by genre, author, or narrator; search across your chosen sources; explore series and recommendations where available.
- **Listen online or offline.** Stream a book or download chapters in advance. Manage downloads and see which books are fully or partly saved.
- **Keep your place.** The player remembers where you stopped and resumes from there; revisit your history and keep books in favorites.
- **Listen at your own pace.** Adjust playback speed, skip back 10 seconds or forward 30 seconds, and keep listening in the background.
- **Wind down comfortably.** Set a sleep timer — the book stops at the chosen moment and continues from there next time — and shape the sound with the equalizer.
- **Make it yours.** Choose a light, dark, or black theme, change the book list layout, set parental controls, and back up or restore app data.
- **Listen on a big screen.** Phones, tablets, and Android TV are supported.
- **Reach blocked catalogs.** If a source website is unavailable in your country, turn on the built-in proxy in **Settings → Proxy** (`Настройки → Прокси`); it covers pages, audio, downloads, and covers. It works only while its proxy server is online.

### Book sources

Choose from **Akniga, Книга в ухе, ИзиБук, AudioBook-mp3, База Книг, Книгоблуд, Audioboo, Отрубин, and Полекниг**. Select the browsing source in **Settings → Source** (`Настройки → Источник`) and choose which catalogs to search under **Search sources** (`Источники поиска`).

Books and catalog details come from the selected websites. Availability, recommendations, and other features can vary by source and network access.

## Screenshots

The app interface shown below is in Russian. Select an image to view it at full size.

<table>
  <tr>
    <th>Book catalog</th>
    <th>Book details & player</th>
    <th>Genres</th>
  </tr>
  <tr>
    <td><a href="docs/assets/screenshots/books_list.png"><img src="docs/assets/screenshots/books_list.png" width="220" alt="Audiobook catalog with covers, titles, authors, narrators, and durations"></a></td>
    <td><a href="docs/assets/screenshots/player.png"><img src="docs/assets/screenshots/player.png" width="220" alt="Book description, series, recommendations, and playback controls"></a></td>
    <td><a href="docs/assets/screenshots/categories.png"><img src="docs/assets/screenshots/categories.png" width="220" alt="Genre list including fantasy, mysteries, classics, and nonfiction"></a></td>
  </tr>
</table>

<details>
<summary>More screenshots: navigation, sources, and settings</summary>

<table>
  <tr>
    <th>Main menu</th>
    <th>Source selection</th>
    <th>Settings</th>
  </tr>
  <tr>
    <td valign="top"><a href="docs/assets/screenshots/main_menu.png"><img src="docs/assets/screenshots/main_menu.png" width="220" alt="Navigation to books, genres, authors, narrators, favorites, history, and downloads"></a></td>
    <td valign="top"><a href="docs/assets/screenshots/available_sources.png"><img src="docs/assets/screenshots/available_sources.png" width="220" alt="Selection of nine audiobook sources"></a></td>
    <td valign="top"><a href="docs/assets/screenshots/settings.png"><img src="docs/assets/screenshots/settings.png" width="220" alt="Settings for appearance, sources, offline listening, parental controls, and backups"></a></td>
  </tr>
</table>

</details>

## Requirements

| | |
|---|---|
| **Device** | Android phone, tablet, or TV running **Android 7.0 or later** |
| **Network** | Internet connection for browsing, streaming, and downloading books |
| **Storage** | Enough free space for the chapters you want to keep offline |
| **App language** | Russian (menu names in this guide are also given in Russian) |
| **Package name** | `com.fanok.audiobooks` |

## Install

1. Download the APK from the [latest GitHub release](https://github.com/korvin2000/AudioBooks/releases/latest) (`AudioBooks-<version>.apk`) or from the first post of the [4PDA thread](https://4pda.to/forum/index.php?showtopic=978445) (`Скачать`).
2. Open the APK on your Android device. If Android asks, allow installation from the browser or file manager you used, then complete the installation.
3. Launch **AudioBooks** and choose a source in **Settings → Source** (`Настройки → Источник`).

Each GitHub release lists SHA-256 checksums in `SHA256SUMS.txt`. Builds from different places (GitHub, 4PDA, the older [RuStore](https://apps.rustore.ru/app/com.fanok.audiobooks) listing) may be signed with different keys, and Android will not update one with the other. To switch, make a backup in **Settings → Backup** (`Настройки → Резервное копирование`), uninstall the app, install the new APK, and restore the backup.

## Start listening

1. Browse **Audiobooks** (`Аудиокниги`) or **Genres** (`Жанры`), or search for a title, author, or narrator.
2. Open a book to read its description and start playback. Tap the star to add it to **Favorites** (`Избранное`).
3. For offline listening, download the chapters you need while connected, wait for them to finish, and open the book from **Downloaded** (`Загруженные`). The **Offline** (`Офлайн`) setting hides chapters that have not been downloaded.
4. Return through **History** (`История`) to continue listening from your saved position.

## Help & feedback

- **Questions and community:** [4PDA discussion](https://4pda.to/forum/index.php?showtopic=978445).
- **Video introduction:** [YouTube](https://www.youtube.com/watch?v=MlqR32Finf0).
- **Bugs and ideas:** [GitHub Issues](https://github.com/korvin2000/AudioBooks/issues). For a playback or download problem, include your app version, Android version, source, book link, and steps to reproduce it.
- **Contact the app developer:** use **About → Write to the author** (`О программе → Написать автору`) in the app.

**Troubleshooting**

- **A catalog will not load:** check your connection and try another source. If the website is blocked in your country, try **Settings → Proxy** (`Настройки → Прокси`).
- **Playback stops when the screen turns off:** turn off battery optimization for the app.
- **Changing devices or reinstalling:** use **Settings → Backup** (`Настройки → Резервное копирование`) first; save downloaded audio separately.

## Contributing

Bug reports, clearer instructions, translation improvements, and focused pull requests are welcome. For larger changes, open an issue first to discuss the approach. Every pull request is checked by the [Build workflow](.github/workflows/build.yml) (unit tests, Android lint, debug APK). Please keep the English and Russian READMEs in sync.

## Building from source

**Requirements:** JDK 17 and the Android SDK with platform 36 (Android Studio provides both). The Gradle wrapper downloads Gradle 8.13 itself.

```bash
git clone https://github.com/korvin2000/AudioBooks.git
cd AudioBooks
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

On Windows, use `gradlew.bat`. The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`. The app lives in the `app/` module: Java code in `app/src/main/java`, screens and texts in `app/src/main/res`, tests in `app/src/test` and `app/src/androidTest`.

- **Release builds** are signed with a key described in a local, git-ignored `keystore.properties` (see the [example](keystore.properties.example)).
- **Releases** are published by GitHub Actions: pushing a tag `v<versionName>` (for example `v7.3`) builds, signs, and publishes the APK and App Bundle through the [Release workflow](.github/workflows/release.yml).
- **The [developer guide](docs/howto.md)** covers toolchain setup, testing, signing, CI secrets, the release process, the emulator, and debugging.

## Credits

**Original app developer:** fanok.inc ([fanok1](https://github.com/fanok1/AudioBooks)). A project license file has not yet been included in this repository.
