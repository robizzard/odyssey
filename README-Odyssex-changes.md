# Odyssex — Odyssey with fast indexed directory browsing

**Odyssex** is a personal fork of [Odyssey](https://github.com/gateship-one/odyssey), an Android music player. The principal change is an **offline directory index** that dramatically reduces delays when browsing large music collections stored on a removable SDHC card. The fork is maintained at [robizzard/odyssey](https://github.com/robizzard/odyssey).

The index can be generated on Linux, imported through Android's file picker, and then used by Odyssex instead of repeatedly enumerating directories on the card. The feature has been tested on a phone running **Android 17**: logs showed `INDEX` lookups, and browsing was much faster, including after force-stopping and restarting the app.

## What's changed

- **Indexed file browsing:** When a directory belongs to the imported music tree, the Files browser obtains its immediate children from an in-memory index instead of calling `File.listFiles()`.
- **Normal fallback:** If there is no imported index, or the folder lies outside the indexed tree, normal directory browsing is retained.
- **Offline Python index generator:** `tools/generate-directory-index.py` recursively records subdirectories and supported media filenames on Linux. The generated index is independent of the SD card's mount point.
- **Android 17-compatible import:** The Files menu's **Import directory index for this folder** action uses the Android Storage Access Framework file picker (`ACTION_OPEN_DOCUMENT`). The chosen TSV is copied into the app's private storage; it need not remain in Downloads or in the music folder.
- **Playback integration:** Indexed lookups are also used for folder traversal when building playlists, and redundant folder counting is avoided where the index supplies that information. Existing music playback and metadata handling remain in place.
- **Diagnostics:** Android logcat reports `INDEX` or `FALLBACK`, the path, entry count and elapsed time.
- **Directory index status:** The Files menu includes **Directory index status**, showing the indexed Android root path, number of indexed directories, number of indexed files, and import date/time. Older imports without a stored timestamp use the saved index file's modification time.
- **Separate test installation:** The locally built debug variant can coexist with the original Odyssey using an `applicationIdSuffix`, and can be labelled **Odyssex** to distinguish the two apps. These branding/build settings were made in the local development checkout and may need to be applied to another checkout (see below).

## Generate an index on Linux

Mount the SDHC card and run the generator from the repository root:

```bash
cd ~/git/odyssey
python3 tools/generate-directory-index.py \
    /media/SDHC/audio \
    -o /media/SDHC/audio/odyssey-directory-index.tsv
```

The example collection contains `audio/flac/`, `audio/mp3/` and their subdirectories. **Index `audio` once**, not each format directory separately. You can put the output elsewhere if preferred; the import picker does not require a particular location.

The generated file begins with `ODYSSEY_INDEX_V1` and then has records of the form:

```text
D\tZmxhYw==
F\tZmxhYy9leGFtcGxlLm1wMw==
```

`D` indicates a directory and `F` a media file. The second column is a URL-safe Base64-encoded UTF-8 **path relative to the indexed root**. Base64 protects unusual filenames and embedded delimiters; it is not encryption. The format is intentionally plain TSV, **not gzip**. A roughly 4 MB index has worked satisfactorily; gzip-compressed indexes are not currently supported.

The script skips symbolic links and directories marked with `.nomedia`, and indexes media filenames with extensions recognised by its extension list. It does **not** extract tags, album art, durations or other media metadata.

## Import on Android

1. Put the SDHC card in the phone, with the generated TSV accessible to Android's file picker.
2. Open **Odyssex → Files** and navigate to the **`audio` directory** on the SDHC card. This must correspond to `/media/SDHC/audio` on Linux, **not** the SD card's parent directory and not `audio/flac`.
3. Choose **Import directory index for this folder** from the menu and select `odyssey-directory-index.tsv`.
4. Wait for the **Directory index imported** confirmation.
5. Use **Directory index status** in the same menu to inspect the root, counts and import date.

The import creates a private copy of the index. Importing another index replaces the previous one; only **one index root** is supported at a time. The index remains available across app restarts.

### Why the file picker?

Android's permission to read audio files does **not** automatically permit direct reading of an arbitrary `.tsv` file on external storage. Using the system file picker grants access to the selected file at import time; after import, Odyssex reads its private copy. Putting the TSV in `Download/` is convenient but offers no special exemption from the Android 17 storage restrictions. Keeping it in `audio/` on the SDHC card is equally reasonable.

## Verify that the index is being used

Connect the phone with USB debugging enabled and run:

```bash
adb logcat -c
adb logcat -v time -s OdysseyDirectoryIndex:V
```

Navigate to a folder such as `audio/flac/Classical/Composers`.

- `INDEX ...` means that the directory's entries came from the imported index.
- `FALLBACK ...` means that the ordinary filesystem enumeration was used.

Each log entry also reports the number of entries and timing for the listing lookup. This timing does **not** necessarily include subsequent UI rendering or metadata work.

To verify that the index survives a cold app restart, run (assuming the debug application ID below):

```bash
adb shell am force-stop org.gateshipone.odyssey.debug
adb shell monkey -p org.gateshipone.odyssey.debug 1
```

Then navigate again and look for `INDEX` messages. This does not remove settings or the imported index.

## Build and install alongside Odyssey

With an Android SDK, JDK and `adb` installed, build a debug APK:

```bash
cd ~/git/odyssey
./gradlew assembleDebug
```

To retain the original Odyssey app, add the following within the existing `debug { ... }` block of `app/build.gradle` **if it is not already present**:

```groovy
applicationIdSuffix ".debug"
```

This produces application ID `org.gateshipone.odyssey.debug`, separate from `org.gateshipone.odyssey`. To display the name **Odyssex**, set `app_name` to `Odyssex` in `app/src/main/res/values/strings.xml`; alternatively, use a debug-only resource override if release builds should retain the original branding.

Rebuild and install:

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you try to update the original app without changing its application ID, installation can fail with `INSTALL_FAILED_UPDATE_INCOMPATIBLE` because the original and your locally built APK have different signing certificates. **Do not uninstall the original just to bypass this** unless you deliberately want to lose its app data.

Check the installed package names:

```bash
adb shell pm list packages | grep odyssey
```

## Index lifecycle and limitations

- The index is a **snapshot**, not a continuously updated database. After adding, removing or renaming music, rerun the Linux generator and reimport the TSV.
- A correct **indexed root path** is essential; the path selected in Odyssex must correspond to the Linux generator's root even though their mount paths differ.
- If a folder is outside the index, the normal browser still works but may be slower.
- Indexed listing does not eliminate all file operations or all playback/metadata costs; it targets directory enumeration and related folder traversal.
- Indexes are validated at import and stored privately. The format currently supports one uncompressed `ODYSSEY_INDEX_V1` index.
- The status menu reports the **time imported**, not the age of the underlying music collection or the time the Linux index was generated.

## Key implementation files

| File | Purpose |
| --- | --- |
| `tools/generate-directory-index.py` | Generates the index on Linux |
| `app/src/main/java/org/gateshipone/odyssey/utils/DirectoryIndex.java` | Parses, persists, caches, queries and summarises the index |
| `app/src/main/java/org/gateshipone/odyssey/utils/PermissionHelper.java` | Indexed folder listing and fallback |
| `app/src/main/java/org/gateshipone/odyssey/fragments/FilesFragment.java` | Import action and status dialog |
| `app/src/main/java/org/gateshipone/odyssey/utils/FileExplorerHelper.java` | Directory/playlist traversal integration |
| `INDEXING.md` | Short index-format and usage notes |

## Original Odyssey project

Odyssey is an open-source Android music player supporting artist, album and file browsing, playlists, bookmarks, artwork, widgets and other features. See the [upstream repository](https://github.com/gateship-one/odyssey), its [FAQ](https://github.com/gateship-one/odyssey/wiki/FAQ), and [screenshots](https://github.com/gateship-one/odyssey/wiki/Screenshots).

The original project is licensed under **GPLv3 or later**. This fork retains the upstream licensing and attribution; see the repository's licence and author files.
