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

## 2026-10-23: The Mihon extensions on the device (details on 2026-10-23, see "Mihon extensions, Search, and Discover")

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

## 2026-10-23: The Kotatsu parsers as a plugin (replaced on 2026-10-24, see "The Kotatsu parsers of Gekkoushi")

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

## 2026-10-23: The Cloudflare challenge (back before 1.0.0 on 2026-10-23, see "Parity with Asabunko 2.2.0"; details on 2026-10-23, see "The browser check")

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

## 2026-10-23: Parity with Asabunko 2.2.0 (extended on 2026-10-24, see "Parity with Asabunko 3.0.0")

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

## 2026-10-23: The novels

- The importers of EPUB, TXT, FB2, and HTML are the importers of Asabunko,
  in the core module. They write the same `novel.json` and the same
  chapters. The XML tree uses the DOM parser of Java, with no external
  entity and no external DTD.
- A no-break space counts as a space in the text of the XML, as in Swift.
- The core gives the image of the cover. The app makes `cover.jpg` of it,
  because the thumbnail needs Android.

## 2026-10-23: The reader of novels

- A chapter shows in a web view, as in Asabunko. The web view gets each
  file of the book from the app, at a host that is not real. A path that
  points out of the folder of the book gets no file.
- The scripts of the book do not run. Each file comes with the Content
  Security Policy `script-src 'none'`. The scripts of the app run with
  `evaluateJavascript`, which the policy does not stop. Android has no
  second world for scripts, as iOS has.
- The style, the place from 0 to 1, the taps, and the links work as in
  Asabunko. The fonts are Noto Serif, Roboto, and Karla, the fonts of
  Android and of the app.
- The volume keys move one screen. At the end of a chapter, they go to the
  next chapter.
- On an e-ink screen, a tap at the bottom or at the top moves one screen with
  no animation, and a tap in the middle shows the bars.
- Reason: the same reader as Asabunko, safe from the scripts of a book.

## 2026-10-23: The PDF

- The import copies the PDF into the folder of the book as `book.pdf`. The
  cover is its first page, drawn on white, as in Asabunko.
- The PdfRenderer of Android opens the PDF. A PDF with a password fails with
  a message, and so does a file that is not a PDF.
- PdfRenderer gives no title and no author. The core reads them from the
  Info dictionary of the bytes: a literal string or a hex string, in
  PDFDocEncoding or UTF-16. The last Info of the file wins, because an
  update adds a new one at the end. A PDF that keeps its Info in a
  compressed object stream gives nothing, and the title comes from the name
  of the file.
- A PDF opens in the reader of comics. Coil draws each page with a fetcher
  of the app, at the size that the reader asks for. The renderer draws one
  page at a time. The mode of a PDF is left to right at the start.
- Reason: the same function as Asabunko, with no second reader. PDFKit of
  iOS has its own view, and Android has none.

## 2026-10-23: Kindle books

- The importer is the importer of Asabunko, in the core module: the PalmDB
  records, PalmDOC, the trailing entries, the EXTH facts, and the skeletons
  and the fragments of KF8. A joint file opens its KF8 part.
- An `<img>` that points to an SVG flow of KF8 becomes the SVG in the page.
  A browser loads no image that an SVG in an `<img>` points to, so the cover
  page of a Gutenberg AZW3 was empty. Asabunko has the same `<img>`, and
  this fix can go back to it.
- The checks: the unit tests of Asabunko, a test of the SVG, and a live
  test with the AZW3 and the MOBI of Project Gutenberg with
  `MOEKOMA_LIVE=1`.

## 2026-10-23: The folders that the app watches

- Asabunko has no such function, so this is the design of Moekoma.
- The user adds a folder in Settings, Folders. The app keeps the permission
  of the Storage Access Framework to read it after a restart.
- A scan imports each book file of the folder and of its subfolders, with
  the kinds of file that the app imports. A folder with images and with no
  book file and no subfolder imports as one comic. A name with a dot first
  is hidden. The images at the top of the folder are not a book.
- `folders.json` keeps each document that a scan saw, also when its import
  failed. So a book that the user deletes does not come back, and a damaged
  file does not fail at each scan. The user can import it by hand.
- A scan runs when the folder is added, when the app opens, at most one time
  in ten minutes, and in WorkManager two times a day. The first scan of the
  worker waits 12 hours too, because the app scans when it opens.
- A scan that the user did not start shows its result only when it found
  something, so that it does not hide the result of the user.
- The rules of the scan are in the core, with tests on a folder of the disk.
- Reason: the owner asked for each Android extra. Android lets an app keep
  the access to a folder, and iOS does not let an app watch a folder.

## 2026-10-23: The sources and the OPDS catalogs

- The sources are in Settings, Sources, as in Asabunko 2.2.0. The user
  pastes one URL on each line. Save replaces the full list. The library file
  keeps the list in `sources`, in the same form as Asabunko, so that a
  backup goes both ways.
- The app finds the kind of each URL from its form. When the form does not
  tell, the app reads the URL and finds the kind from what it gives.
- A URL with `user:password@` keeps only the user name in the list. The
  password goes to a file of the app, encrypted with AES-GCM. The key is in
  the Android Keystore, so the key does not leave the device. The app sends
  the password only to the host of the source.
- An OPDS catalog shows its links, its groups, and its books, with a search
  through OpenSearch and "Load more" for the next page. A book with the Page
  Streaming Extension reads at once, page by page, and the app keeps nothing.
  "Download" imports the file into the library.
- Java resolves a link that is only a query, such as `?page=2`, with no last
  part of the path. RFC 3986 keeps it. The core does this case itself.
- The reader of a remote book covers the tabs, so that Settings keeps its
  open screens when the reader closes.
- Release 0.7.0 has the sources and OPDS only. Search, Discover, and the
  page of a title move to 0.8.0 with the Mihon extensions. Reason: in
  Asabunko, Search and Discover ask only the installed online sources, and
  an OPDS catalog is not one. With no online source, the two screens are
  empty.
- The checks: the core tests of the source list and of OPDS 1.2 and 2.0,
  and a test on the emulator with `scripts/test-server.py` of Asabunko at
  `http://10.0.2.2:8765/opds/catalog`. The password, the covers, the search,
  the stream reader, and the download work.

## 2026-10-23: Mihon extensions, Search, and Discover

- The module `:mihon` has the source API of Mihon (commit d001e6f): the
  source classes, the models, the network helpers, the rate limits, and
  Jsoup helpers. The packages and the names stay as in Mihon, because the
  extensions call them by name. `mihon/NOTICE` lists the files that Moekoma
  changed. The app gives the extensions OkHttp with Brotli and Zstandard,
  kotlinx.serialization (JSON and ProtoBuf), coroutines, RxJava 1, Injekt,
  Jsoup, and AndroidX Preference, as Mihon does.
- An APK is a file of the app in `files/Mihon`. It loads with a
  DelegateLastClassLoader, as in Mihon. Android 14 loads code only from a
  file that the app cannot write, so each APK is read only.
- An APK installs only when it asks for the feature `tachiyomi.extension`,
  is for the library 1.4 or 1.6, and the key of its repository signed it.
  The key is the `signingKeyFingerprint` of `repo.json`, or the key of the
  new index. Reason: the user trusts the repository that they pasted, not
  each file that it points to.
- A repository has three forms: the old `index.min.json` with `repo.json`,
  a `repo.json` that points to `index.pb`, and the new index in Protocol
  Buffers. Keiyoushi keeps a stub "Outdated App" in its old index, so the
  app follows `index_v2`. The core reads all three, with tests.
- The library file of Asabunko does not know the kind "mihon". Moekoma keeps
  it in `moekomaKind`, and "kind" is empty, so that Asabunko reads the file.
  A kind that Moekoma does not know reads as no kind, and the app finds the
  kind again.
- A save of the source list installs the English sources of each list, as
  in Asabunko 2.2.0. For Keiyoushi this is 376 sources in 365 APKs, 28.9 MB
  on the emulator, and the install took about 70 seconds. The user removes
  a source in its list.
- Only the APKs of the 24 sources that the user used last stay in the
  memory. Reason: Search loads each installed source.
- An extension can keep facts in the memo of a series or a chapter
  (library 1.6). Moekoma keeps the memo in the ID, after a line break, so
  that the source gets it back.
- Many extensions put their own interceptors before Mihon's
  `CloudflareInterceptor`, so the client has one with that name. In this
  release it finds the browser check and gives a clear fault. The web view
  comes in 0.9.0.
- An error of an extension, such as a class that does not start, is a
  fault of its source. It does not stop the app.
- Search, Discover, the title page, and the reader of a series are the
  screens of Asabunko 2.2.0: Search asks six sources at the same time and
  joins one title of many sources; the title page compares the sources and
  chooses the source with the most chapters; Discover asks the first ten
  sources. With the 376 sources of Keiyoushi, a search for "Frieren" took
  about two minutes for all sources and gave 192 titles. The first results
  came after about 20 seconds.
- The readers cover the tabs, so that a tab keeps its open screens.
- The checks: the core tests of the search, the chapter groups, the
  languages, the merge of chapters, the index, and the kinds; a live core
  test with the index of Keiyoushi; and a live device test that installs
  MangaDex and reads a page (`-Pandroid.testInstrumentationRunnerArguments.live=1`).

## 2026-10-23: The browser check

- The client of the Mihon extensions has the Cloudflare interceptor of
  Mihon. It passes most checks in a web view with no window, as Mihon does.
  Many extensions put their own interceptors before it, so the client must
  have it with this name.
- A check that needs the user, such as a box to tick, goes to the app after
  the 30 seconds of Mihon. The app shows a banner, as in Asabunko 2.2.0. It
  is a banner, and not a screen, so that the app does not open a page that
  the user did not ask for.
- "Open" shows the site in a web view. The screen closes when a new
  `cf_clearance` cookie comes. An old cookie was refused, so only a new
  value counts. "Done" closes the screen too.
- The web view and the network clients share the cookie store of Android,
  through the `AndroidCookieJar` of Mihon. The client of the app has it
  too, so an OPDS catalog behind Cloudflare works after the check.
- A check counts only for the user agent that passed it. Each client and
  each web view sends the user agent of the web view of the device, with no
  mark of a web view.
- After a passed check, the screens with a fault load again: the title
  page, the list of a source, Discover, the reader of a series, and an
  OPDS catalog.
- The checks: the core tests of `BrowserCheck`; two device tests with the
  test server, a check that the web view with no window passes in 3.8
  seconds, and a check with a button that goes to the app; and a test on
  the emulator of the banner, the web view, a tap on the button, and the
  catalog that loads again. No real site of Cloudflare was tested, because
  a real check depends on the network and on Cloudflare.
- The installed sources move to a screen of their own with a filter.
  Reason: a repository such as Keiyoushi installs hundreds of sources.

## 2026-10-24: The Kotatsu parsers of Gekkoushi

- KotatsuApp archived the Kotatsu parsers in November 2025. The maintained
  fork is Gekkoushi/plugin-source, under the GPL 3. Its API is a separate
  library, UsagiApp/Tsuki. Its CI publishes the parsers as a dex jar in the
  releases of Gekkoushi/plugin.
- Moekoma uses that jar as the plugin, and its CI does not build one.
  Reason: the fork already makes the plugin, and the plugin changes often.
  A copy of the build in Moekoma would only be older.
- The jar holds only the parsers. Tsuki is in the app, in the module
  `:tsuki`, from the tag 1.0.5, which is the version of the jar. A new jar
  for a newer Tsuki needs a new release of Moekoma.
- The user pastes the URL of the repository, such as
  `https://github.com/Gekkoushi/plugin`, or the URL of a jar. The app takes
  the jar of the latest release. The jar has no signature, so the trust is
  the URL that the user pasted.
- The app gives the parsers the client of the Mihon extensions, with the
  cookies and the browser check, a web view for their JavaScript, the
  bitmaps of Android for the images that a site mixes, and the default of
  each setting. The client adds the headers of the parser of a request, then
  calls its interceptor, as Kotatsu does. An image gets the site as its
  referer, as in Kotatsu.
- The kind is "kotatsu", which Asabunko does not know, so the library file
  keeps it in `moekomaKind`. A broken parser and a parser of novels do not
  show.
- R8 must keep Tsuki as it is. With "allowoptimization", R8 made an open
  method final, and a parser that overrides it did not load. The release
  build is the check.
- With the lists of Keiyoushi and Gekkoushi, the app installs 576 sources.
  A title page now asks at most 24 other sources: the sources of the books
  first. Reason: it asked 575 sources each time a title opened. An
  installed source names its list, because two lists can have a source with
  the same name.
- The checks: core tests of the jar of a release and of the kind; a live
  device test that loads the plugin (915 sources, 263 in English) and reads
  a page of MangaPill; on the emulator, the save of the list (200 English
  sources in about 30 seconds), a title page and a chapter of MangaPill in
  the debug and the release builds.
- The emulator stopped two times in a long session: the system server did
  not answer, and a test waited. A new start of the emulator fixed it. The
  checks run on a new emulator before each release.

## 2026-10-24: The LNReader plugins in QuickJS

- The LNReader plugins run in QuickJS through quickjs-kt 1.0.15 by dokar3,
  under the Apache License 2.0. The same library runs in the JVM tests of
  the core module and on Android.
- The JavaScript is the JavaScript of Asabunko: `libraries.js`, `host.js`,
  and `lnreader.js`. A small shim gives the native functions of Asabunko
  (fetch, decode, timer, and log) with the functions of quickjs-kt.
  Reason: one copy of the plugin host is easier to keep than two. The
  bundle builds again from `js/` with `scripts/build-js.sh`.
- Each plugin has its own context. One call runs at a time in a context.
- Only one context starts at a time. quickjs-kt keeps the classes of the JVM
  in global references, and each new context replaces them with no lock.
  Two contexts that started at the same time stopped the app with a JNI
  fault. A device test starts 16 contexts at the same time. It failed in 1
  of 3 runs with no lock, and passed in 5 of 5 runs with the lock.
- A Map of Kotlin becomes a Map of JavaScript in quickjs-kt, so the host
  gives a JsObject for a plain object. The bytes go as an Int8Array.
- The app keeps the code of a plugin in `Sources/<list>/<id>.code`, and its
  settings in `<id>.settings.json`, as Asabunko does.
- A chapter of a novel downloads when the reader opens it. The reader shows
  a spinner, or the fault and "Try again".
- The filters and the settings of a source have one model, SourceOption,
  for the plugins of the next releases too: text, toggle, select, a list
  with include and exclude, sort, and range.
- The checks: core tests of the runtime, of a plugin with fixed answers, of
  gRPC, and a live test of AllNovelFull; the device suite; on the emulator,
  the save of the list (184 English plugins), the filters of AllNovel, and
  two chapters of a novel in the debug and the release builds.

## 2026-10-24: The Suwayomi servers

- The client is the client of Asabunko. It sends GraphQL to `/api/graphql`.
  The sources of the server are the sources of its extensions. The local
  source of the server, "0", is not there.
- The app keeps no code for a source of the server, because the server runs
  it. The English sources install when the user saves the list, as with
  the other lists.
- The password of the line goes to each request and each image of the host
  of the server, and not to another host.
- The ID of a filter is its position, and "3.1" is the filter 1 in the
  group 3. A group of checks is one choice of many values. Only a filter
  that the user changed goes to the server, as Asabunko does. The ID of a
  setting is the position of its preference.
- A real server gives each path from its own root, such as
  `/api/v1/manga/7/thumbnail`, and the app adds the URL of the line. The
  test server of Asabunko gave paths that already held `/suwayomi`, so the
  app asked for `/suwayomi/suwayomi/...` and the images did not load. The
  test server of Moekoma gives paths from its root, as a real server.
- The checks: core tests of the client with fixed answers; a device test
  that reads the images of a chapter with the password, filters the list,
  and changes a preference on the test server; on the emulator, the same
  flow in the debug and the release builds.

## 2026-10-24: Parity with Asabunko 3.0.0

- Asabunko went from 2.2.0 to 3.0.0. On this date its working tree has the
  version 3.0.0 with no tag yet, and its last tag is v2.8.0.
- Moekoma 2.0.0 has each function of Asabunko 3.0.0 and the Android extras.
  The releases from 1.0.0 to 2.0.0 follow the releases of Asabunko from 2.4.0
  to 3.0.0, one large function in each, in the same order.
- The functions of Asabunko 2.3.0 go into the releases of the sources that
  they change: Aidoku in 0.13.0, Paperback in 0.14.0. The fixes of 2.3.0 for
  the vertical reader, the save of the source list, and the filter sheet are
  in Moekoma already.
- An Android tool takes the place of an iOS tool: WorkManager for the
  background tasks of iOS, the text to speech of Android for the voice of
  iOS, an App Widget for WidgetKit, and the share sheet of Android.
- 2.0.0 is the release of quality, as 3.0.0 of Asabunko, with the speed,
  the fixes, and the tidy-up of all of Moekoma. Each speed change has a
  measure before and after it.
- Reason: the owner asked for the new functions of Asabunko 3 and for a
  Moekoma 2 that is faster and has fewer faults.
- "Parity with Asabunko 2.2.0" stays: 1.0.0 has the functions of Asabunko
  2.2.0. This decision adds the target of 2.0.0.

## 2026-10-24: The Aidoku sources in Chicory

- The Aidoku sources run in Chicory 1.7.5, a WebAssembly interpreter in
  Java, under the Apache License 2.0. The host is the host of Asabunko: the
  env, std, net, html, defaults, canvas, and js modules, the postcard data,
  and the result buffers of the exports. Each source has its own thread.
- The html module uses jsoup, which the sources expect.
- The core module is a JVM module, so the canvas and the web view are in an
  interface, AidokuPlatform. The app gives the Bitmap, the Canvas, the fonts,
  and a WebView with no window of Android. The JVM tests give the images of
  Java. A plain JavaScript context of the js module runs in QuickJS, under
  the same lock as the other QuickJS contexts.
- The app keeps the .aix package of a source in its code file, and the
  values of its settings in its settings file, as an LNReader plugin.
- The settings of a source are a form, SourceForm, as in Asabunko. The
  Paperback extensions of the next release use the same form.
- A web sign-in and an OAuth sign-in open in a web view of the app. An OAuth
  sign-in ends when the page goes to the callback scheme of the source.
  Reason: a Custom Tab needs an intent filter for each scheme.
- The speed on the emulator, release build, MangaBat: the load of the
  source 0.4 s, the popular list 0.9 s, and the title page of Martial Peak
  with 3877 chapters 11 s. On the JVM of the computer, the same work takes
  about 3 s. Chicory has no compiler on Android, and its interpreter checks
  the interruption of its thread at each call and loop. A faster engine is a
  task of 2.0.0.
- A title page asks at most four sources for the title at the same time,
  so that slow sources do not take the processor from the chosen source.
- A source that leaves the memory of the loaded sources closes a minute
  later. Before, its QuickJS context or its Chicory thread and memory
  stayed until the app stopped.
- The checks: core tests of the postcard data, the host with WAT modules
  (wat2wasm of Chicory), the canvas and the js modules, the options, and the
  settings form; live tests of Guya, MangaBat, Kagane (filters in code),
  MangaFire (a range), MANGA Plus (pages that the canvas decrypts), and the
  settings forms of the 48 English sources; device tests of the test server
  source, the canvas, and the web view; on the emulator, MangaBat, Madokami,
  and Comix in the debug and the release builds.

## 2026-10-24: The Paperback extensions in QuickJS

- The Paperback 0.9 extensions run in QuickJS with paperback.js of
  Asabunko, the Application global and the calls of the host. The native
  functions of paperback.js are bindings of Kotlin: the state, Base64, the
  digests, AES-CBC and AES-GCM of javax.crypto, the HTML entities of jsoup,
  random bytes, and UUIDs. The bytes cross as Int8Array, and come back as an
  ArrayBuffer.
- The state of an extension stays in its settings file, in the form of
  Asabunko: a plain map and a secure map of JSON text.
- `Application.executeInWebView` runs in a WebView of Android with no
  window. The app sets the cookies of the extension, loads the HTML, and
  runs the script of the extension again until it gives a value or 15
  seconds end.
- The fetch of a JavaScript source gives the cookies of each Set-Cookie
  header, as Asabunko does. The answer of the network keeps each Set-Cookie
  header, because one map cannot hold two headers with the same name.
- A settings form can ask for a Save. Its changes go to the extension only
  then. The form of a Paperback extension is the JSON of SourceForm from
  paperback.js.
- The checks: core tests of a test extension (the interceptors, the filters,
  the forms with a Save, OAuth with PKCE, the state, AES-CBC, and a web view
  that the test gives), of the cookies of the runtime, and of the
  repository; live tests of WeebCentral and MangaDex; device tests of the
  test server extension and of the web view of Android; on the emulator,
  WeebCentral and MangaDex with its forms, in the debug and the release
  builds.
