# Luma

A minimalist PDF and EPUB reader for Android. Clean interface, persistent
reading progress, and pencil-style highlights that survive a restart.

Built with Kotlin and Jetpack Compose, local Room storage, and the Android SDK
for file handling. No accounts, no network, no telemetry.

## What it does

- Opens PDF and EPUB files from device storage through the system file picker.
- Renders PDF pages with the platform `PdfRenderer` and EPUB chapters in a
  WebView with a reader stylesheet.
- Remembers the exact page you stopped on and restores it next launch.
- Turns pages with a horizontal swipe and a smooth slide animation. Swiping
  from right to left goes forward; left to right goes back. The arrow buttons
  do the same.
- Activates highlighting on demand, never by default. PDFs get a pen toggle
  you switch on to draw freehand marks; EPUB text is highlighted from the
  toolbar once you select it. Long-pressing EPUB text still shows the standard
  Copy, Share and Select All options.
- Offers six highlight colours and an opacity slider, yellow by default.
- Shows a highlight panel for the current page, with colour swatches and delete.
- Draws a reading progress bar.
- Offers theme (system, light, dark), a text size slider for EPUB chapters, and
  a Compatibility Mode that inverts a PDF or EPUB using black text on a white
  page into white text on a black background.

## Requirements

- Android Studio Koala or newer, or the command line below.
- JDK 17.
- Android SDK with API 34 platform and build tools.
- minSdk 24, targetSdk 34.

## Build and run locally

```bash
# Point Gradle at your SDK if it is not in the default location.
echo "sdk.dir=$HOME/Android/Sdk" > local.properties

./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk

# Install on a connected device or emulator:
./gradlew installDebug
```

On Windows use `gradlew.bat` in place of `./gradlew`.

The project ships with sample content. Open `sample.epub` from the app's
"Add book" picker to try the reader, EPUB highlighting, and progress tracking.

## Build with GitHub Actions

The workflow at `.github/workflows/build-apk.yml` assembles a debug APK on
`ubuntu-latest`. It runs on every push to `main` and on manual dispatch.

### Trigger the workflow

1. Push this project to a GitHub repository (see the commands at the end of
   this section).
2. Open the repository on GitHub and go to the **Actions** tab.
3. Select **Build Debug APK** in the left sidebar.
4. Click **Run workflow**, pick the `main` branch, and confirm with the green
   **Run workflow** button. The workflow also starts on its own after a push to
   `main`, so step 2 to 4 are only needed for a manual run.
5. Wait for the run to finish. The badge turns green when the unit tests and
   the debug build both pass.

### Download the APK

1. Open the finished run from the Actions tab.
2. Scroll to the **Artifacts** section at the bottom of the run summary.
3. Download **luma-debug-apk**. GitHub delivers a zip.
4. Unzip it. Inside is `app-debug.apk`.
5. Install it on a device with `adb install app-debug.apk`, or copy the file to
   the device and open it (allow install from unknown sources once).

Artifacts are kept for 14 days. The debug APK is unsigned for release use; it
is meant for testing on your own device.

### Push commands

```bash
git init -b main
git add .
git commit -m "Add Luma reader"
git remote add origin https://github.com/<your-account>/luma.git
git push -u origin main
```

## Project layout

```
app/src/main/java/com/luma/reader/
  LumaApp.kt              Application and hand-rolled dependency container
  MainActivity.kt         Compose host, theme wiring
  data/                   Room entities, DAOs, database, repository, settings
  epub/EpubParser.kt      Unzip and spine resolution, no third-party parser
  ui/theme/               Color scheme and typography (dark first)
  ui/library/             Library list, import, empty state
  ui/reader/              Reader screen, PDF canvas, EPUB WebView, view model
  ui/settings/            Theme and font size
  util/ReadingLogic.kt    Page validation, progress, duplicate detection
app/src/main/assets/      reader.css and reader.js for EPUB chapters
app/src/test/             Unit tests for ReadingLogic
.github/workflows/        build-apk.yml
```

## Data model

`books(id, path, lastReadPosition, lastPageNumber)` plus `title`, `format`,
`pageCount`, and `addedAt` so the library can list and sort entries.

`highlights(id, bookId, pageNumber, text, timestamp)` plus optional geometry so
a mark redraws after relaunch: `startOffset` and `endOffset` for EPUB, and a
normalized `rectLeft`, `rectTop`, `rectRight`, `rectBottom` for PDF pencil
bands. The extras were added because the four specified columns cannot locate a
mark on a page by themselves.

Page numbers are zero-based in storage and shown one-based in the UI.

## Security

- Picked files are copied into app-specific internal storage. The app keeps no
  reference to an external path and asks for no runtime permissions.
- No secrets ship in the app. `.env.example` documents optional release signing
  variables only; the debug workflow ignores it.
- EPUB parsing blocks external entities and DOCTYPE declarations, and unzip
  rejects entries that escape the extract directory.

## Sample EPUB

`sample.epub` is a valid three-chapter EPUB (uncompressed `mimetype` first, per
spec). Use it to verify import, chapter navigation, progress, and highlighting.

## Out of scope for v1

Cloud sync, advanced annotation tools, text-to-speech, and document conversion.
