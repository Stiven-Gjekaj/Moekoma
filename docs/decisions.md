# Decisions

Each choice of the owner is written here, with its reason. A new choice that
replaces an old one names the old one.

Moekoma is the Android form of Asabunko, the iOS reader of the owner. The
decisions of Asabunko are in its own `docs/decisions.md`. A choice of
Asabunko applies here when this file does not replace it. Read Asabunko for
the form of a function before you write it again here. Do not guess it.

## 2026-10-23: A universal reader for Android

- Moekoma reads manga, comics, light novels, and web novels on Android
  phones, tablets, and e-ink readers.
- It has each function of Asabunko, and the functions that only Android
  permits.
- Reason: the owner asked for the Android port of Asabunko, with the
  advantages of Android, such as the sources that iOS cannot run.

## 2026-10-23: The name and the brand

- The name of the app is Moekoma.
- The app keeps the logo of Asabunko: the girl who reads a manga, with the
  morning sun behind her. The red of the logo and of the app becomes green.
- The colours:

  | Name | Hex | Use |
  | --- | --- | --- |
  | Green | `#3DD68C` | The sun and the tag of the mark, and the accent of the dark form. |
  | Deep green | `#1A6B43` | The accent on paper. |
  | On green, dark | `#0A1E12` | The text on the accent in the dark form. |
  | On green, paper | `#FFF8EE` | The text on the accent on paper. |

- The contrast of the accent by WCAG 2, on the surfaces of Asabunko:
  - warm dark (`#17130F`, `#211C17`, `#2C251F`): 9.9, 9.0, and 8.1. The text
    on the accent: 9.3.
  - paper (`#F6F0E4`, `#FBF7EE`, `#EFE6D6`): 5.7, 6.1, and 5.3. The text on
    the accent: 6.2.
- The warm dark form, the paper form, the corners, and the Karla font are the
  same as in Asabunko.
- Reason: the owner asked for the logo of Asabunko with green in place of
  red. Each accent has a contrast of 4.5 or more, as in Asabunko.

## 2026-10-23: Native Kotlin, with a core module

- The app is Kotlin with Jetpack Compose and Material 3, with the theme of
  Asabunko in place of the theme of Material.
- Gradle builds the app, as in ZettaStream. The parts that do not need a
  screen are in a JVM module, `core`. Its tests run on the Mac with Gradle,
  with no emulator. This is the same form as `AsaCore`.
- The package is `io.github.stivengjekaj.moekoma`.
- Reason: ZettaStream is native Kotlin and Compose. The owner knows its
  build, its CI, and its updates. A JVM module tests in seconds.

## 2026-10-23: Phones, tablets, and e-ink

- The lowest version is Android 8.0, API level 26. The target is the newest
  API level, as in ZettaStream.
- The layout changes with the width of the window. A phone shows one pane. A
  tablet or an unfolded phone shows the list and the details side by side.
- The app does not support Android TV.
- An "E-ink" look is a third look, next to warm dark and paper:
  - Black text on white, with no grey for the text and no colour.
  - No animation, no shadow, no gradient, and no ripple.
  - The readers turn pages. They do not scroll smoothly. The novel reader
    moves one screen at each tap.
  - An optional full refresh: a black frame between pages, every 1 to 10
    pages, to clear the ghost image.
  - Large touch targets and thick outlines.
- The app proposes the E-ink look at the first start when the maker of the
  device is an e-ink maker, such as Onyx (Boox), Bigme, Meebook, or
  PocketBook. The user can change it at each time in Settings. Hisense is not
  in the list, because it also makes phones with a normal screen.
- Reason: API level 26 covers almost all phones that are in use, and Mihon
  extensions need it too. Onyx Boox readers run Android 11 or newer. An
  e-ink screen shows a smear with each animation, and a smooth scroll redraws
  many times.

## 2026-10-23: The license is the GPL

- Moekoma is under the GNU General Public License, version 3. Asabunko
  stays under the MIT License.
- Reason: the Kotatsu parsers are under the GPL 3. The owner accepted the GPL
  to have all sources.

## 2026-10-23: The files of the device

- The app imports the same files as Asabunko: CBZ, CBR, CB7, CBT, ZIP, RAR,
  RAR5, 7z, TAR, folders of images, EPUB, TXT, FB2, HTML, and PDF.
- It also reads MOBI and AZW3 books with no DRM. This is on the roadmap of
  Asabunko too. It comes after 1.0.0, see "Parity with Asabunko first".
- The archives open with libarchive, through the Android library of
  zhanghai, under the Apache License 2.0. Android has no libarchive of the
  system.
- A PDF opens with the PdfRenderer of Android.
- The import copies the pages as Asabunko does, with the same rules for the
  names and the paths.
- The user can add a folder of the device with the Storage Access Framework.
  The app watches the folder and imports each new book in it. A scan runs
  when the app opens and at intervals.
- Reason: the owner asked for each function of Asabunko and for each
  Android extra. libarchive reads RAR5, which no Java library reads.

## 2026-10-23: All sources

- Moekoma runs each source ecosystem that it can run on Android:
  - OPDS 1.2 and OPDS 2.0 catalogs, as in Asabunko.
  - Mihon extensions, on the device.
  - The Kotatsu parsers.
  - LNReader plugins.
  - Suwayomi servers.
  - Aidoku sources.
  - Paperback 0.9 extensions.
- The source list is the paste box of Asabunko: one URL on each line. The
  app finds the kind of each line from its URL or from its content.
- "index.min.json" is the name of a list of Mihon extensions and of a list
  of Aidoku sources. The app reads the content to tell them apart.
- The app has no source in it. The user adds the URL of a list.
- Reason: the owner asked for all sources that the app can support.

## 2026-10-23: The Mihon extensions on the device

- A Mihon extension is an APK. Moekoma downloads the APK and loads its code
  in a class loader of its own. The extension is not installed in the
  system, so Android does not ask the user.
- The app gives the interface of the Mihon extensions: the source classes,
  the network helper with OkHttp, the JSON library, and the preferences of a
  source. The interface comes from Mihon, under the Apache License 2.0.
- A list of extensions, such as the list of Keiyoushi, gives the APKs. The
  user installs the extensions that they want.
- The app checks the signature of each APK against the list.
- Suwayomi stays as a second way, for a server at home.
- Reason: this is the largest advantage of Android. iOS cannot run an APK, so
  Asabunko needs a Suwayomi server for these sources.

## 2026-10-23: The Kotatsu parsers as a plugin

- The Kotatsu parsers are a library of about 1000 sources. They are not in
  the APK of Moekoma.
- The CI of Moekoma builds the parsers into a plugin APK and publishes it in
  a list, with the form of a list of extensions. The user adds the URL of
  the list, as for each other source. The app loads the plugin as it loads a
  Mihon extension.
- A new version of the parsers is a new plugin. The app does not need a new
  release for it.
- Reason: the app has no source in it. A plugin keeps this rule, and the
  parsers change more often than the app.

## 2026-10-23: The JavaScript and WebAssembly sources

- LNReader plugins and Paperback extensions run in QuickJS, one context for
  each source, on its own thread. The app gives the same browser functions
  and the same JavaScript bundle as Asabunko.
- Aidoku sources run in Chicory, a WebAssembly runtime in Java, under the
  Apache License 2.0. The host follows the host of Asabunko. The html
  imports use Jsoup, which SwiftSoup copies.
- The canvas imports of Aidoku use the Bitmap and the Canvas of Android. The
  js imports use a web view. They come after 1.0.0, see "Parity with
  Asabunko first".
- Reason: JavaScriptCore is not on Android. QuickJS is small and fast, and
  it runs the same bundle. Jsoup is the library that the Aidoku sources
  expect.

## 2026-10-23: The Cloudflare challenge (back before 1.0.0 on 2026-10-23, see "Parity with Asabunko 2.2.0")

- When a site gives a Cloudflare challenge, the app opens it in a web view.
  The user passes the challenge, and the web view closes.
- The cookies of the web view go to the network client of each source. Each
  ecosystem uses the same web view: Mihon, Kotatsu, LNReader, Aidoku, and
  Paperback, with its executeInWebView.
- The user agent of the requests is the user agent of the web view.
- Reason: the owner asked for each Android extra. Android has a full web
  view with its cookie store, and the same solution serves each source.

## 2026-10-23: Downloads in the background (back before 1.0.0 on 2026-10-23, see "Parity with Asabunko 2.2.0")

- The user downloads the chapters of an online book to read with no network.
- The downloads run in WorkManager, with a notification that shows the
  progress. They continue when the app is closed.
- The user can choose to download only on Wi-Fi.
- Reason: the owner asked for each Android extra. Android permits long work
  in the background, which iOS stops.

## 2026-10-23: The volume keys

- In the readers, the volume keys turn the page. The user can turn this off
  and can change the direction of the keys.
- Reason: the owner asked for each Android extra. Many e-ink readers have
  page keys that send the same key codes.

## 2026-10-23: The backup

- The backup has the form of the backup of Asabunko. Moekoma restores a
  backup of Asabunko, and Asabunko restores a backup of Moekoma.
- A source that the other app cannot run, such as a Mihon extension in
  Asabunko, stays in the backup. The app names the books of that source.
- Moekoma also imports a backup of Mihon, a `.tachibk` file: the library,
  the categories as a note, the chapters that are read, and the extensions.
  The books of an extension come back when the user installs that extension.
- Reason: the owner asked for a backup that moves between the two apps, and
  for the import of a Mihon backup.

## 2026-10-23: The releases and the updates

- The versions start at 0.1.0. Each release before 1.0.0 adds one large
  function. The order is in [TODO.md](../TODO.md).
- From 1.0.0, a fix raises the patch number, a new function raises the
  minor number, and a change that an older version cannot read raises the
  major number.
- A tag such as `v0.1.0` makes GitHub Actions build a signed APK and attach
  it to a release as `Moekoma.apk`.
- The signing key is a secret of the GitHub repository. It is not in the
  repository.
- The app updates itself from 0.1.0, as ZettaStream does. When a newer
  release is out, the app downloads its APK and gives it to the package
  installer of Android, which asks the user to confirm.
- Reason: the owner asked for one large function in each release and for
  updates inside the app. Android installs an update only when its signature
  is the same, so the key must never change.

## 2026-10-23: The repository

- The repository is `Stiven-Gjekaj/Moekoma` on GitHub.
- It has the rules for agents and the form of the documents of Asabunko:
  AGENTS.md, docs/decisions.md, TODO.md, CHANGELOG.md, and README.md.
- Reason: the owner asked for the same form.

## 2026-10-23: Parity with Asabunko first (replaced on 2026-10-23, see "Parity with Asabunko 2.2.0")

- 1.0.0 has each function of Asabunko 1.0.1 and the functions that only
  Android permits:
  - the Mihon extensions on the device, and the Kotatsu parsers as a plugin.
  - the E-ink look, the volume keys, and the folders that the app watches.
  - the updates inside the app, and the import of a Mihon backup.
- These functions come after 1.0.0. Each one comes when Asabunko has it,
  with the form of Asabunko:
  - the Cloudflare challenge in a web view, with executeInWebView of
    Paperback.
  - MOBI and AZW3 books.
  - the downloads of chapters in the background.
  - the filters and the settings of the sources.
  - the canvas and js imports of Aidoku.
- This replaces the order of the releases in "The releases and the
  updates". The new order is in [TODO.md](../TODO.md).
- Until the Cloudflare web view comes, a Mihon source behind a challenge
  does not work on the device. A Suwayomi server can run it.
- Reason: the owner builds these functions in Asabunko now. One design in
  each of the two apps keeps them the same and does not do the work two
  times.

## 2026-10-23: The core module and the archives

- `core` is a JVM module with no Android code, as `AsaCore` has no screen.
- libarchive runs only on Android, so the core reads the files of a comic
  through `ComicFiles`. The app gives `ArchiveFiles`, with libarchive, and
  `DocumentTreeFiles`, with the Storage Access Framework. The tests of the
  core use the ZIP reader of Java.
- The tests of libarchive run on the emulator. They write ZIP, 7z, and TAR
  archives with libarchive. No tool writes a RAR, as in Asabunko.
- libarchive opens the file descriptor of the document. A file that another
  app shares as a stream goes to the cache first, because libarchive must
  seek in a 7z or a RAR.
- libarchive stays at 1.1.6. Version 1.1.7 needs compileSdk 37.2.
- Reason: the core tests run in seconds with no emulator, and the import
  rules are the same for an archive and a folder.

## 2026-10-23: The library file

- The library is one JSON file, `library.json`, in the files folder of the
  app. The pages are in `Books/<ID>/`, as in Asabunko.
- The file has the field names and the form number of Asabunko. A UUID is in
  capital letters, and a date is ISO 8601 with no part of a second, because
  Swift writes and reads only these forms.
- A field that Moekoma does not know yet, such as the sources of Asabunko, is
  ignored when the app reads the file.
- No file of the app goes to the backup of Android. The backup of the app
  comes in 1.0.0.
- Reason: one form lets the backup move between the two apps.

## 2026-10-23: The comic reader

- The paged modes use the pager of Compose. A right to left book reverses the
  pager, so that its first page is at the right.
- A pinch or a double tap zooms a page up to four times. While a page is
  zoomed, a drag moves the page and not the pager.
- A page loads at two times the long side of the screen, for the zoom. A
  page of the vertical mode loads at one time.
- The bars of the system hide with the bars of the reader.
- The volume keys and the page keys turn the page in the paged modes, and
  move one screen in the vertical mode.
- On an e-ink screen, a swipe turns the page with no animation, and the
  vertical mode moves one screen at each tap at the top or the bottom.
- Reason: the same reader as Asabunko, with the Android extras of the owner.

## 2026-10-23: The new library and search of Asabunko (replaced on 2026-10-23, see "Parity with Asabunko 2.2.0")

- The owner changes Asabunko now: the library holds the books on a reading
  list and the books to continue. Browse becomes a search, as in
  ZettaStream, that chooses the best source or shows a list of sources. The
  sources move to Settings and install with no tap. A Discover page comes
  after the library.
- Moekoma waits for this work, and ports it from Asabunko. It does not
  design it here.
- Reason: the owner asked to see how the agent of Asabunko does it first.

## 2026-10-23: Parity with Asabunko 2.2.0

- Asabunko 2.2.0 has the functions that "Parity with Asabunko first" kept
  for later: the Cloudflare check, MOBI and AZW3, the chapter downloads, the
  filters and the settings of the sources, the canvas and js modules of
  Aidoku, and the settings forms of Paperback. It also has the new design:
  the read list, Search, Discover, and the sources in Settings.
- Moekoma 1.0.0 has each function and the design of Asabunko 2.2.0, and the
  functions that only Android permits. This replaces "Parity with Asabunko
  first" and "The new library and search of Asabunko".
- Each release ports the form of Asabunko, its screens, its words, and its
  tests. A newer release of Asabunko goes into the roadmap when it is out.
- The order of the releases is in [TODO.md](../TODO.md).
- Reason: the owner asked to port Asabunko with its new design, and to add
  the Android functions over it.

## 2026-10-23: The banner

- `assets/brand/banner.swift` makes `logo-full.png` from the logo of
  Asabunko. The mark stays. The word "moekoma" is made from the letters of
  "asabunko", so that it has the same style: the m is two arches of the n,
  the e is the o with a bar and an opening, and the second o keeps the star.
- The red becomes the green of Moekoma, as in `render.swift`.
- Reason: the owner asked for the banner of Asabunko with the new name.

## 2026-10-23: The read list

- The library has the `listed` mark of Asabunko. A book of an older file is
  listed. A book from a file is listed when it imports.
- The order of the read list is a preference of the device, not a part of
  the library, as in Asabunko.
- The library saves 0.4 seconds after the last change, and at once in
  `onStop`, because Android can stop the app in the background.
- Reason: the same library as Asabunko 2.
