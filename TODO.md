# Roadmap

Each task here is a choice of the owner. A task leaves this list when its
release is out. The reason for each choice is in
[docs/decisions.md](docs/decisions.md).

Each release adds one large function. Read the same function in Asabunko
before you write it here, and port its design, its words, and its tests.
Moekoma 2.0.0 has the functions of Asabunko 3.0.0 and the Android extras.
Each release keeps the backup that Asabunko of the same functions reads.

## After 2.0.0

- The start of the app. On the emulator, a cold start takes about 3
  seconds, with a spread of more than 1 second between two starts. The
  Application part takes 50 to 130 ms, and the first frame of the Library
  takes most of the rest. A baseline profile of the start screens gave no
  difference that the emulator can show. Measure on a real device before a
  change.
- The size of the APK (20 MB unsigned). Most of it is the native libraries
  of libarchive and QuickJS for four ABIs.
- The memory of the page cache.

## Not on the list

- Each new release of Asabunko after 3.0.0 goes here as a new task.
