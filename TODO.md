# Roadmap

Each task here is a choice of the owner. A task leaves this list when its
release is out. The reason for each choice is in
[docs/decisions.md](docs/decisions.md).

Each release adds one large function. Read the same function in Asabunko
before you write it here.

## Releases up to 1.0.0

- 0.1.0 The library and the comic archives, with the comic reader, the
  three looks with E-ink, the volume keys, and the updates inside the app.
- 0.2.0 EPUB, TXT, FB2, and HTML novels, with the reader of novels.
- 0.3.0 PDF.
- 0.4.0 The folders of the device, which the app watches.
- 0.5.0 The source list and the OPDS catalogs.
- 0.6.0 Mihon extensions on the device.
- 0.7.0 The Kotatsu parsers as a plugin.
- 0.8.0 LNReader plugins.
- 0.9.0 Suwayomi servers.
- 0.10.0 Aidoku sources.
- 0.11.0 Paperback 0.9 extensions.
- 1.0.0 All of these functions, with the backup of Asabunko, the import of a
  Mihon backup, and the check for new chapters.

## Changes that come from Asabunko now

The owner makes these changes in Asabunko now. Port them when they are out,
and change the order of the releases if they need it.

- The library holds the books on a reading list and the books to continue.
- Browse is a search, as in ZettaStream. It chooses the best source, or the
  user chooses a source from a list.
- The sources move to Settings and install with no tap.
- A Discover page after the library, with recommended manga.

## After 1.0.0, when Asabunko has them

Port each one from Asabunko. Do not design it again here.

- The Cloudflare challenge in a web view, for each source, with
  executeInWebView of Paperback.
- MOBI and AZW3 books with no DRM.
- The downloads of chapters in the background.
- The filters and the settings of the sources.
- The canvas and js imports of Aidoku.
