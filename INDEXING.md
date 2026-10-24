# Odyssey offline directory index (experimental)

## Generate on Linux

```sh
python3 tools/generate-directory-index.py /media/$USER/MUSIC -o /tmp/odyssey-directory-index.tsv
```

Copy `odyssey-directory-index.tsv` somewhere Android's system file picker can open it (e.g. Downloads or the SD card). In **Odyssey > Files**, navigate to the same directory used as `music_root` in the generator (not its parent). Open the overflow menu and select **Import directory index for this folder**. Choose the TSV file. The app validates it and imports it to its private storage. Android's picker can read this file even though Odyssey's ordinary audio permission cannot.

When the contents of the card change, regenerate and import. The index is a snapshot and does not check for stale tracks. Browsing folders *inside* the indexed tree takes the index fast path; other folders fall back to `File.listFiles()`.

## Notes

- The imported root path must match the path Odyssey displays on this phone. Paths in the index are relative to the selected root; no Linux mount points are stored.
- A `.nomedia` directory is skipped like Odyssey's existing filesystem browser.
- The index includes directory names and filenames only. File metadata/playback stays with Odyssey's existing code.
- Watch Android logs using `adb logcat -s OdysseyDirectoryIndex:D` to distinguish INDEX and FALLBACK operations.
- Only a single imported index is supported. Import a replacement to update it.
- This is a source patch, not a tested APK. Build and device validation are still required.
