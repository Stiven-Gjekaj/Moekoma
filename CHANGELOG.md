# Changelog

Every release is written here, newest first. A version is `MAJOR.MINOR.PATCH`.
Each release before 1.0.0 adds one large function. See "The releases and the
updates" in the decisions.

A tag such as `v0.1.0` builds the signed APK and attaches it to a release on
GitHub as `Moekoma.apk`.

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
