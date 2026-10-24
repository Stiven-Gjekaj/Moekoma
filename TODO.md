# Roadmap

Each task here is a choice of the owner. A task leaves this list when its
release is out. The reason for each choice is in
[docs/decisions.md](docs/decisions.md).

Each release adds one large function. Read the same function in Asabunko
before you write it here, and port its design, its words, and its tests.
Moekoma 2.0.0 has the functions of Asabunko 3.0.0 and the Android extras.
Each release keeps the backup that Asabunko of the same functions reads.

## Releases up to 1.0.0

- 0.15.0 Chapter downloads in the background, with WorkManager.
- 1.0.0 The backup of Asabunko in both directions, the import of a Mihon
  backup, the check for new chapters, the move of a book to another source,
  and the fixes, the speed, and the comforts of the review of Asabunko 2.0.0.

## Releases from 1.0.0 to 2.0.0

- 1.1.0 New chapters come to you, as Asabunko 2.4.0: the check of the read
  list in the background with WorkManager, a notification for each book
  with new chapters, the Updates list, "Download new chapters" for each
  book, and "Download only on Wi-Fi".
- 1.2.0 The tools of the library, as Asabunko 2.5.0: categories of the read
  list, the search and the filters of the library, the History, "Private
  reading", the Storage page, and the move of many books to another source.
- 1.3.0 The reader does more, as Asabunko 2.6.0: two pages side by side on a
  wide screen, the page layout menu (the fit, "Crop the borders", the taps at
  the edges), a novel that reads itself aloud with the text to speech of
  Android, the search of the words of a novel, and bookmarks and highlights.
- 1.4.0 Trackers and servers, as Asabunko 2.7.0: AniList and MyAnimeList,
  the Track action of a book, and the progress of Komga and Kavita.
- 1.5.0 Moekoma fits into Android, as Asabunko 2.8.0: the app in Japanese, a
  Home Screen widget of Continue reading, "Share the file" of a book (CBZ,
  PDF, or EPUB), HTML files in "Open with", a light vibration at the end of a
  chapter or a book, and a spread that stays in the middle after a turn of
  the device.
- 2.0.0 Quality, as Asabunko 3.0.0, and the review of all of Moekoma:
  - Speed: the start of the app with a baseline profile, the load of the
    library and the source list off the main thread, the memory of the page
    cache and of the loaded sources, the start of the JavaScript and
    WebAssembly sources, and the size of the APK. Measure each change before
    and after it.
  - Fixes: a full pass over the readers, the sources, the downloads, the
    backup, and the settings, with a test for each fault that it finds.
  - Tidy-up: code that nothing calls goes, and the same code in two places
    becomes one function. Each screen reads its data one time.
  - Accessibility: TalkBack on each tab, the settings, and the readers, and
    the largest font size and display size with no text past the edge.
  - A script runs the full device suite with the test server on the release
    commit before each tag. The CHANGELOG gives the number of tests that
    passed.

## Not on the list

- Each new release of Asabunko after 3.0.0 goes here as a new task.
