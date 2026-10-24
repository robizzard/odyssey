package org.gateshipone.odyssey.utils;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.gateshipone.odyssey.models.FileModel;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Offline directory index imported through Android's document picker. */
public final class DirectoryIndex {
    private static final String TAG = "OdysseyDirectoryIndex";
    private static final String MAGIC = "ODYSSEY_INDEX_V1";
    private static final String INDEX_FILE = "odyssey-directory-index.tsv";
    private static final String PREFS = "odyssey_directory_index";
    private static final String ROOT = "root";
    private static final String IMPORT_TIME = "import_time";
    private static DirectoryIndex cached;
    private final String root;
    private int fileCount;
    private int directoryCount;
    private final Map<String, List<FileModel>> children = new HashMap<>();
    private final Map<String, Integer> folderCounts = new HashMap<>();

    private DirectoryIndex(String root) { this.root = root; }

    private static String decode(String value) {
        return new String(Base64.decode(value, Base64.NO_WRAP | Base64.URL_SAFE), StandardCharsets.UTF_8);
    }

    private static String relativePath(String path) throws IOException {
        if (path.isEmpty()) return "";
        if (path.startsWith("/") || path.contains("\\") || path.indexOf('\0') >= 0) throw new IOException("Invalid index path");
        for (String part : path.split("/", -1)) {
            if (part.isEmpty() || part.equals(".") || part.equals("..")) throw new IOException("Unsafe index path");
        }
        return path;
    }

    private static DirectoryIndex parse(InputStream input, String root) throws IOException {
        DirectoryIndex result = new DirectoryIndex(new File(root).getCanonicalPath());
        result.children.put("", new ArrayList<>());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            if (!MAGIC.equals(reader.readLine())) throw new IOException("Unsupported index format");
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", -1);
                if (parts.length != 2 || !(parts[0].equals("D") || parts[0].equals("F"))) throw new IOException("Invalid index record");
                String relative;
                try { relative = relativePath(decode(parts[1])); }
                catch (IllegalArgumentException e) { throw new IOException("Bad index encoding", e); }
                if (relative.isEmpty()) throw new IOException("Empty index filename");
                int slash = relative.lastIndexOf('/');
                String parent = slash < 0 ? "" : relative.substring(0, slash);
                boolean directory = parts[0].equals("D");
                String fullPath = result.root + "/" + relative;
                result.children.computeIfAbsent(parent, k -> new ArrayList<>()).add(new FileModel(fullPath, directory));
                if (directory) {
                    result.directoryCount++;
                    result.children.computeIfAbsent(relative, k -> new ArrayList<>());
                    result.folderCounts.put(parent, result.folderCounts.getOrDefault(parent, 0) + 1);
                } else {
                    result.fileCount++;
                }
            }
        }
        for (List<FileModel> files : result.children.values()) {
            Collections.sort(files, (a, b) -> {
                if (a.isDirectory() != b.isDirectory()) return a.isDirectory() ? -1 : 1;
                return a.getName().compareToIgnoreCase(b.getName());
            });
        }
        return result;
    }

    public static synchronized void importIndex(Context context, InputStream source, String root) throws IOException {
        File target = new File(context.getFilesDir(), INDEX_FILE);
        File staging = new File(context.getFilesDir(), INDEX_FILE + ".tmp");
        try {
            try (OutputStream out = new FileOutputStream(staging)) {
                byte[] buffer = new byte[65536];
                int n;
                while ((n = source.read(buffer)) != -1) out.write(buffer, 0, n);
            }
            DirectoryIndex parsed;
            try (InputStream in = new FileInputStream(staging)) { parsed = parse(in, root); }
            if (target.exists() && !target.delete()) throw new IOException("Unable to replace existing index");
            if (!staging.renameTo(target)) throw new IOException("Unable to save index");
            if (!context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putString(ROOT, parsed.root)
                    .putLong(IMPORT_TIME, System.currentTimeMillis()).commit()) {
                Log.w(TAG, "Index saved but index preferences could not be persisted");
            }
            cached = parsed;
            Log.i(TAG, "Imported index: " + parsed.children.size() + " folders, root=" + parsed.root);
        } finally { if (staging.exists()) staging.delete(); }
    }

    private static synchronized DirectoryIndex load(Context context) {
        if (cached != null) return cached;
        String root = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ROOT, null);
        File file = new File(context.getFilesDir(), INDEX_FILE);
        if (root == null || !file.isFile()) return null;
        try (InputStream in = new FileInputStream(file)) { cached = parse(in, root); }
        catch (IOException e) { Log.e(TAG, "Unable to load directory index", e); }
        return cached;
    }

    /** Immutable summary of the currently imported index, or null if no usable index exists. */
    public static final class Status {
        public final String root;
        public final int files;
        public final int directories;
        public final long importedAt;

        private Status(String root, int files, int directories, long importedAt) {
            this.root = root;
            this.files = files;
            this.directories = directories;
            this.importedAt = importedAt;
        }
    }

    public static Status getStatus(Context context) {
        DirectoryIndex index = load(context);
        if (index == null) return null;
        long importedAt = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getLong(IMPORT_TIME, 0);
        if (importedAt <= 0) {
            // Indexes imported by earlier Odyssex versions had no timestamp preference.
            importedAt = new File(context.getFilesDir(), INDEX_FILE).lastModified();
        }
        return new Status(index.root, index.fileCount, index.directoryCount, importedAt);
    }

    private String key(String path) {
        String canonical;
        try { canonical = new File(path).getCanonicalPath(); }
        catch (IOException e) { return null; }
        if (canonical.equals(root)) return "";
        if (!canonical.startsWith(root + "/")) return null;
        return canonical.substring(root.length() + 1);
    }

    /** Null means not covered by the index; an empty list means an indexed empty directory. */
    public static List<FileModel> list(Context context, String path) {
        DirectoryIndex index = load(context);
        if (index == null) return null;
        String key = index.key(path);
        if (key == null) return null;
        List<FileModel> files = index.children.get(key);
        return files == null ? null : new ArrayList<>(files);
    }

    public static Integer subfolderCount(Context context, String path) {
        DirectoryIndex index = load(context);
        if (index == null) return null;
        String key = index.key(path);
        if (key == null || !index.children.containsKey(key)) return null;
        return index.folderCounts.getOrDefault(key, 0);
    }
}
