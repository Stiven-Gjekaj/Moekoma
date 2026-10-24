# Changelog

Every release is written here, newest first. A version is `MAJOR.MINOR.PATCH`.
Each release before 1.0.0 adds one large function. See "The releases and the
updates" in the decisions.

A tag such as `v0.1.0` builds the signed APK and attaches it to a release on
GitHub as `Moekoma.apk`.

## 2.1.0 (2026-10-24)

The last parts of Asabunko 3.0.0, after a new read of all its releases.

- The chapters of each book stay in memory until their file changes, as in
  Asabunko. The menus, the History, and the open of a chapter no longer
  read the file each time. For a book of 2,000 chapters, one read went from
  832 to 3 microseconds in a test on the computer.
- A chapter of a web novel that opens downloads the next chapter too, so
  that the next chapter opens with no wait.
- A test runs the Accessibility Test Framework on the four tabs, the pages
  of Settings, and the readers, as the audit of Asabunko does.
- A test reads each tab at the largest font size. No text goes past the
  edge of the screen, and each text keeps a margin.
- On the release commit, 55 of 55 device tests passed.

## 2.0.0 (2026-10-24)

The quality release. Each number is a measurement on the emulator. The
memory, the web view, and the font size fixes have tests.

- A search of 87 sources uses less memory. A source that leaves the cache of
  loaded sources now leaves the memory at once. Before, it stayed for one
  more minute. The peak Java heap of the same search went from 584 MB and
  643 MB to 459 MB, 179 MB, and 399 MB in three runs.
- A web view whose render process stops, for example with too little
  memory, no longer stops the app.
- At the largest font size, each tab label and the "Read list" heading stay
  on one line, and a switch row has space around its label.
- TalkBack reads the states of the filters, the pages, and the sentences in
  the language of the app.
- The undo and help icons turn in a language from right to left.
- `scripts/device-suite.sh` runs the full device suite with the test server
  and counts the tests. On the release commit, 47 of 47 tests passed.

## 1.5.0 (2026-10-24)

- The app is in Japanese when the device or the app uses Japanese. Android
  13 and later let you choose the language of Moekoma alone.
- A Home Screen widget shows the three books of Continue reading. A tap
  opens the book.
- "Share the file" in the menu of a book sends its file to another app: a
  CBZ, a PDF, or an EPUB.
- A light tap of the device tells you that a chapter or a book ends.
- A spread stays at its pages after a turn of the device.

## 1.4.0 (2026-10-24)

- Track your books on AniList and MyAnimeList. In Settings, Sources,
  Trackers, paste the client ID of a client that you register on the site,
  with the redirect URI moekoma://anilist or moekoma://myanimelist, and sign
  in. Then hold a book in the Library and choose Track.
- A tracked book sends the number of chapters that you read each time it
  goes up. A finished book is complete on the tracker. Private reading
  sends nothing.
- A stream of a Komga or a Kavita catalog opens at the last page that you
  read on the server. Each page that you read goes back to Komga. Kavita
  keeps it by itself.

## 1.3.0 (2026-10-24)

- The page layout button in the reader of pages chooses the fit (the
  screen, the width, or the height), "Crop the borders", "Two pages on a
  wide screen", and the taps at the edges (left and right, forward at each
  edge, or no turns).
- On a screen that is wider than it is tall, two pages show side by side.
  The first page is alone, as the cover of a book is. A page of two pages
  in one image is alone. "Move the pairs by one page" fixes a scan where
  the pairs are on the other pages.
- "Crop the borders" shows a scanned page with no plain white or black
  frame.
- A novel reads itself aloud with the text to speech of Android. The
  reader shows the sentence that it reads, and the speech goes on into the
  next chapter.
- Search the words of a novel. A result opens its chapter and shows the
  words.
- Keep bookmarks and highlights in a novel. Select words and choose
  Highlight. The Marks button adds a bookmark and lists the marks of the
  book.

## 1.2.0 (2026-10-24)

- Put the books of your read list in categories, such as "Reading" or "On
  hold". The chips above the read list show one category. "Add a category"
  and "Edit" open the editor, where you add, rename, order, and remove the
  categories. Hold a book to choose its categories.
- Search the library by title or writer with the search button.
- Filter the read list: Unread, On the device, Comics, and Novels.
- The History in the Library shows the chapters that you opened, by day. A
  tap reads the chapter again. "Clear" removes all of the history.
- "Private reading" in Settings, Reader keeps your reads out of the history
  and out of Continue reading.
- The Storage page in Settings, Sources shows the space of the downloads of
  each book. Remove the read chapters, the downloads of a book, or all
  downloads.
- "Move books to another source" in Settings, Sources moves many books at
  one time. Moekoma finds each title in the new source, and you see the
  matches before the move.

## 1.1.0 (2026-10-24)

- New chapters come to you. Turn on "Check in the background" in Settings,
  New chapters. The books of your read list check for new chapters about
  every six hours, and a notification tells you about each book with new
  chapters. A tap on the notification opens the book.
- The Updates list in the Library shows the new chapters of each check, by
  day. A tap reads the chapter. "Clear" empties the list.
- "Download new chapters" in the downloads menu of a title downloads the
  new chapters of the book after each check. "Download only on Wi-Fi" keeps
  these downloads off a network that costs money.

Fixes:

- A check names the books that did not update.
- A search of many sources does not stop the app. The app asks Android for
  the large heap, as Mihon does.

## 1.0.0 (2026-10-24)

- Back up the library in Settings, Backup. The backup is one JSON file with
  the books and their places, the source list with no passwords, the
  installed sources, and the chapters of each online book. Asabunko restores
  it, and Moekoma restores a backup of Asabunko.
- Restore a backup. Moekoma installs each source of the backup again and
  names the sources that did not install. A book from a file comes back
  only when its file is on the device.
- Import a Mihon backup. Its series, the chapters that you read, its
  categories, and its repositories come in. A series comes in when its
  source installs.
- Check all online books for new chapters with the button in the Library,
  or pull the Library down. A pull with no new chapters says nothing.
- Move a book to another source. When you open a title in another source,
  "Move my book" moves your book there, and your place goes to the chapter
  with the same number.

Fixes:

- Each quick tap at the edge turns one page. Before, two quick taps zoomed
  the page, and a tap during a turn got lost.
- The books of a line of the source list that you remove and add again find
  their source.
- A strip of a webtoon in the vertical reader fills the width of the screen
  and stays sharp.
- The licenses name QuickJS and Chicory.

## 0.15.0 (2026-10-24)

- Download the chapters of an online series. The download button on the
  page of a title downloads all chapters, or the chapters from your place.
  Hold a book in the Library to download it or to remove its downloads.
- The downloads go on in the background, also when you close the app. A
  notification shows the progress.
- A downloaded chapter opens from the device, with no network.
- Each chapter shows its state: on the device, downloading, waiting, or
  failed. "Try the failed downloads again" downloads the failed chapters.
- A downloaded chapter holds only real images. A page of HTML, such as a
  check page, does not count as a page.
- A book with downloaded chapters stays in the library when you take it off
  the read list.

## 0.14.0 (2026-10-24)

- Run the Paperback 0.9 extensions. Paste the URL of a repository, such as
  https://inkdex.github.io/extensions/0.9/stable/versioning.json, in
  Settings, Sources. Moekoma adds its English extensions. A tracker, such
  as AniList, is not a reader and does not install.
- The filters of an extension show, and the gear opens its settings form,
  such as the languages and the content filters of MangaDex. A form that has
  a Save button keeps its changes only when you tap Save.
- An extension can sign in with OAuth, such as MangaDex.
- An extension that runs a page in a web view works, such as MangaFire.
- A JavaScript source gets the cookies of each answer.

Fixes:

- A row of a settings form does not show its value two times.
- A title of a Paperback extension has no spaces or invisible marks at its
  ends.

## 0.13.0 (2026-10-24)

- Run the Aidoku sources. Paste the URL of a list of Aidoku sources, such as
  https://aidoku-community.github.io/sources/index.min.json, in Settings,
  Sources. Moekoma adds its English sources.
- A source that draws its pages again, such as MANGA Plus, gives its pages.
- The filters of an Aidoku source show, also the filters that it makes in
  code and the range filters, such as the release year of MangaFire.
- The gear of an Aidoku source opens its settings form: groups, pages,
  choices, lists that you edit, buttons, links, steppers, and the sign-in
  with a password, a web page, or OAuth.

Fixes:

- A source that leaves the memory of the app closes. Before, its JavaScript
  context or its WebAssembly instance stayed.
- A title page asks at most four sources for the title at the same time.
- The footer of Sources in Settings names all kinds of source.

## 0.12.0 (2026-10-24)

- Read the sources of a Suwayomi server. Paste the URL of the server, such
  as http://user:password@192.168.1.5:4567, in Settings, Sources. Moekoma
  adds its English sources. The server runs them.
- The filters and the settings of a Mihon source on the server show, and a
  change goes to the server.
- An LNReader or a Suwayomi line in the source list opens its list of
  sources.

## 0.11.0 (2026-10-24)

- Run the LNReader plugins. Paste the URL of a list of plugins, such as
  https://raw.githubusercontent.com/LNReader/lnreader-plugins/plugins/v3.0.0/.dist/plugins.min.json,
  in Settings, Sources. Moekoma adds its English novel sources.
- Read a novel of a plugin. The reader downloads each chapter when you open
  it.
- Filter the list of a source. A source with filters shows a filter button.
- Change the settings of a source. A source with settings shows a settings
  button.

## 0.10.0 (2026-10-24)

- Run the Kotatsu parsers. Paste the URL of a repository of the parsers,
  such as https://github.com/Gekkoushi/plugin, in Settings, Sources.
  Moekoma takes its latest plugin and adds its English sources.
- Search, Discover, and the title page ask these sources too.
- Each installed source shows the name of its list.
- A title page asks at most 24 other sources, so it opens fast with many
  sources.
- A fault with no message names the kind of error.

## 0.9.0 (2026-10-23)

- Pass the browser check of Cloudflare. Moekoma passes most checks with no
  window, as Mihon does.
- A check that needs you, such as a box to tick, shows a banner. Tap Open,
  pass the check once, and the screen closes by itself. The page that did
  not load loads again.
- An OPDS catalog behind Cloudflare works after the check too.
- The installed sources have a screen of their own, with a filter by name
  or language.

## 0.8.0 (2026-10-23)

- Run Mihon extensions on your device. Paste the URL of a repository, such
  as Keiyoushi, in Settings, Sources. Moekoma installs its English sources
  with no tap. Each APK must be signed by the key of its repository.
- Search: type a title, and Moekoma asks all your sources at the same time.
  A title that many sources have shows one time, with the number of its
  sources.
- Discover: a banner of popular titles, and rows of the popular titles and
  the new chapters of your sources.
- The page of a title compares its sources and chooses the source with the
  most chapters. You can choose another source. A long list of chapters
  shows by volume or in parts of 50.
- Read a series one chapter at a time. The buttons for the next chapter
  show on the last page. A series that you open stays in Continue reading.
- Add a series to the read list with the bookmark.
- A site with the browser check of Cloudflare gives a clear message. The
  check comes in the next version.

## 0.7.0 (2026-10-23)

- Add your sources in Settings, Sources. Paste one URL on each line.
- Browse OPDS catalogs, such as Komga, Kavita, and Calibre-web: their
  sections, their books, and their search.
- Read a comic of a catalog at once, page by page, with no download.
- Download a book of a catalog into the library.
- For a server with an account, write the user name and the password in the
  URL. The password goes to the encrypted store of the device.

## 0.6.0 (2026-10-23)

- Watch the folders of your device. Open Settings, Folders, and add a
  folder. Moekoma imports each book in it and in its subfolders: EPUB, PDF,
  comic archives, Kindle books, text, and folders of images.
- Moekoma looks for new books when the app opens and two times a day, also
  when the app is closed. "Look again" looks now.
- A book that you delete does not come back. Remove a folder to stop the
  watch. Its books stay in the library.

## 0.5.0 (2026-10-23)

- Import MOBI, AZW, and AZW3 books with no DRM. A KF8 book, such as an AZW3,
  keeps its styles and its images. A joint MOBI file opens its KF8 part.
- Each part of the book is a chapter, with its title from its heading.
- The cover page of a KF8 book shows its image.
- A book with DRM, or with the compression of the Kindle store, gives a
  clear message.

## 0.4.0 (2026-10-23)

- Adds PDF. Import a PDF, or open it with Moekoma from another app. The title
  and the author come from the PDF, and the cover from its first page.
- Read a PDF page by page from the left or from the right, or in one
  vertical column. Pinch or double tap to zoom. The page stays.
- A PDF with a password does not import, and the app tells you why.

## 0.3.0 (2026-10-23)

- Adds novels. Import EPUB 2 and EPUB 3 books, TXT files, FB2 books, and
  HTML pages, or open them with Moekoma from another app.
- The chapters come from the spine of an EPUB, with their titles from the
  table of contents. A TXT file gets a chapter at each heading, such as
  "Chapter 12" or "第十二章", and a long text with no headings gets parts.
- A TXT file can be in UTF-8, UTF-16, Shift JIS, GB18030, or Windows-1252.
- Read one chapter at a time, with a list of the chapters that you can
  search, and buttons for the next and the previous chapter. The place in
  each chapter stays.
- Choose a serif font, a sans serif font, or Karla, and the size, the line
  spacing, the margins, and justified text.
- The volume keys move one screen, and go to the next chapter at the end.
  On an e-ink screen, tap at the bottom or the top to move one screen.
- The scripts of a book do not run.
- An EPUB with DRM does not import, and the app tells you why.

## 0.2.0 (2026-10-23)

- The library of Asabunko 2: Continue reading, and the read list. A book that
  you import goes into the read list. The menu of a book can add it again.
- Sort the read list by date added, last read, title, or unread first.
- An empty library and an empty read list show art: the mark on a soft
  glow.
- A library file that does not load stays as it is. Moekoma keeps a copy,
  saves nothing over it, and lets you start a new library in Settings.
- The library saves a short time after the last change, and at once when the
  app goes to the background.
- The screen stays on while a book is open. Settings, Reader turns it off.
- The splash screen has the warm dark of the app.
- The README has a banner with the name Moekoma.

## 0.1.0 (2026-10-23)

- The first release: the library and the comic reader.
- Import CBZ, CBR, CB7, and CBT files, ZIP, RAR, RAR5, 7z, and TAR archives,
  and folders of images. "Open with" and "Share" from another app also
  import the file.
- The title, the writer, and the direction come from ComicInfo.xml.
- Read page by page from the right to the left or from the left to the
  right, or in one vertical column for a webtoon. Pinch or double tap to
  zoom.
- The library shows the books to continue, with the page and the progress.
- Mark a book as read or as unread, and delete a book.
- Choose "Warm dark", "Paper", "E-ink", or the look of the device. The
  E-ink look has no animation, turns the pages, and can clear the screen
  every few pages. An e-ink reader proposes it at the first start.
- The volume keys turn the page. Settings can turn this off or reverse the
  keys.
- A tablet shows the tabs in a side bar.
- The app tells you when a newer version is available, and installs it.
