<div align="center">

<img src="assets/brand/logo-full.png" alt="Moekoma" width="320">

### A calm reader for manga, comics, and light novels on Android

_Your files and the sources of the community, in one warm app, on a phone, a tablet, or an e-ink reader_

<p align="center">
  <img src="https://img.shields.io/badge/Android-8.0%2B-17130F?style=for-the-badge&logo=android&logoColor=white" alt="Android 8.0 or newer"/>
  <img src="https://img.shields.io/badge/Kotlin-Compose-17130F?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin and Jetpack Compose"/>
</p>

<p align="center">
  <a href="https://github.com/Stiven-Gjekaj/Moekoma/releases/latest"><img src="https://img.shields.io/github/v/release/Stiven-Gjekaj/Moekoma?style=for-the-badge&color=3DD68C&labelColor=17130F&label=Download" alt="Download the newest release"/></a>
  <a href="https://ko-fi.com/stivengjekaj"><img src="https://img.shields.io/badge/Ko--fi-Support_this_project-17130F?style=for-the-badge&logo=ko-fi&logoColor=white" alt="Support this project on Ko-fi"/></a>
</p>

<p align="center">
  <a href="https://github.com/Stiven-Gjekaj/Moekoma/actions/workflows/ci.yml"><img src="https://github.com/Stiven-Gjekaj/Moekoma/actions/workflows/ci.yml/badge.svg" alt="CI"/></a>
  <img src="https://img.shields.io/badge/license-GPL--3.0-17130F?style=flat-square" alt="GPL 3.0 License"/>
</p>

</div>

---

## Overview

**Moekoma** is the Android form of
[Asabunko](https://github.com/Stiven-Gjekaj/Asabunko). It reads manga,
comics, light novels, and web novels. It has a warm dark look, a paper look,
and an E-ink look for readers such as the Onyx Boox.

Moekoma has no sources in it. Your books stay on your device.

## Features

- Comics: CBZ, CBR, CB7, and CBT files, ZIP, RAR, RAR5, 7z, and TAR
  archives, and folders of images. The title, the writer, and the direction
  come from ComicInfo.xml
- Novels: EPUB, TXT, FB2, HTML, MOBI, AZW, and AZW3 files. The chapters and their titles come
  from the book, or from the headings of a TXT file
- A reader of novels with your font, size, spacing, and margins, and a list
  of the chapters
- PDF, page by page or in one column
- A reader of pages from the right to the left, from the left to the right,
  or in one vertical column, with zoom, two pages on a wide screen, the fit,
  the crop of the borders, and the taps at the edges
- A novel that reads itself aloud, the search of the words of a novel, and
  bookmarks and highlights
- Continue reading: the books that you read now, with the page and the
  progress
- The read list, in the order that you choose: date added, last read,
  title, or unread first
- A library file that does not load stays safe, and Moekoma saves nothing
  over it
- Warm dark, paper, E-ink, or the look of the device
- The E-ink look: black on white, no animation, pages that turn and do not
  scroll, and a black frame that clears the ghost image
- The volume keys and the page keys of an e-ink reader turn the page
- The screen stays on while a book is open
- "Open with" and "Share" from another app import the file
- Folders that Moekoma watches: each new book in them comes into the
  library, also when the app is closed
- Mihon extensions on the device: paste a repository, such as Keiyoushi,
  and Moekoma installs its English sources. Search asks all of them at the
  same time, and Discover shows their popular and new titles
- The Kotatsu parsers as a plugin: paste a repository of the parsers, such
  as Gekkoushi, and Moekoma adds its English sources
- LNReader plugins for novels, with their filters and their settings
- The sources of a Suwayomi server, with their filters and their settings
- Aidoku sources, with their filters, their settings forms, and their
  sign-ins
- Paperback 0.9 extensions, with their filters, their settings forms,
  OAuth, and the pages that they run in a web view
- A title page that compares the sources of a title and chooses the source
  with the most chapters, with the chapters by volume or in parts of 50
- The browser check of Cloudflare: Moekoma passes most checks with no
  window. A check that needs you shows a banner, and you pass it once in a
  web view
- OPDS catalogs, such as Komga, Kavita, and Calibre-web: browse, search,
  read a comic at once, or download a book. A password stays encrypted on
  the device
- Chapter downloads in the background, which open with no network
- A backup that Asabunko restores, the restore of a backup of Asabunko, and
  the import of a Mihon backup
- A check of all online books for new chapters, and the move of a book to
  another source
- New chapters in the background: a notification for each book, the
  Updates list, and the download of new chapters, on Wi-Fi only if you want
- Categories of the read list, the search and the filters of the library,
  the History, private reading, the Storage page, and the move of many books
- A layout for phones, and a side bar for tablets
- The app downloads and installs its own updates

The next functions are in [TODO.md](TODO.md).

## Install

Download `Moekoma.apk` from the
[newest release](https://github.com/Stiven-Gjekaj/Moekoma/releases/latest),
and open it. Android asks one time if your browser or file manager can
install apps.

The newest APK is always at this URL:

```
https://github.com/Stiven-Gjekaj/Moekoma/releases/latest/download/Moekoma.apk
```

After that, Settings shows each new version. Tap it, and the app downloads
and installs the update.

## Project structure

```
app/src/main/kotlin/io/github/stivengjekaj/moekoma/
  data/      the library, the import, libarchive, and the preferences
  ui/        the library, the reader, the settings, and the theme
  update/    the updates inside the app
core/        the parts that do not need a screen, with their tests
assets/brand/   the icon, the banner, and the scripts that make them from the logo of Asabunko
docs/        the reason behind each choice
scripts/     a test server for the sources
mihon/       the source API of Mihon, so that the Mihon extensions run
tsuki/       Tsuki, the API of the Kotatsu parsers
js/          the JavaScript libraries of the sources, which scripts/build-js.sh bundles
```

## Building

You need JDK 21 and the Android SDK.

```
git clone https://github.com/Stiven-Gjekaj/Moekoma
cd Moekoma
./gradlew :app:assembleDebug
```

The APK is in `app/build/outputs/apk/debug/`. A tag such as `v0.1.0` makes
the workflow build the signed APK and attach it to a release.

## Testing

```
./gradlew :core:test
./gradlew :app:connectedDebugAndroidTest
```

The core tests run on the JVM. The device tests need an emulator or a
device. They write ZIP, 7z, and TAR archives with libarchive, read them,
import a manga into an empty library, read it from the right to the left,
and change the look.

## Documentation

- [docs/decisions.md](docs/decisions.md): each choice and its reason
- [CHANGELOG.md](CHANGELOG.md): what changed, and when
- [TODO.md](TODO.md): the next releases
- [AGENTS.md](AGENTS.md): the rules for an agent that works here

## License

GPL 3.0. See [LICENSE](LICENSE). Karla is under the SIL Open Font License,
in [licenses/](licenses/Karla-OFL.txt). The archives open with libarchive,
under the BSD License, through the Android library of Hai Zhang, under the
Apache License 2.0. The Aidoku sources run in Chicory, under the Apache
License 2.0. The JavaScript sources run in QuickJS through quickjs-kt, under
the Apache License 2.0.
