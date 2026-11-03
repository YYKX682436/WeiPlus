package h.Hchat.hooks.api.runtime;

import android.content.pm.ApplicationInfo;
import android.system.Os;
import android.system.StructStat;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads APK central-directory entry metadata; never streams or hashes APK/DEX payload bytes. */
public final class DexArchiveIdentity {
    private static final Pattern DEX_ENTRY = Pattern.compile("classes(?:[0-9]+)?\\.dex");
    private static final Pattern ACTIVE_TINKER_DEX_ARCHIVE = Pattern.compile(
            "zip file \\\"([^\\\"]*/tinker/patch-[^\\\"]*/dex/[^\\\"]+\\.(?:jar|apk|zip))\\\"");
    private static final int MAX_CACHED_ARCHIVES = 128;
    private static final Object CACHE_LOCK = new Object();
    // Key: canonical path + file key/dev/inode/size/mode/nanosecond mtime and ctime.
    // Base-vs-split validation is not identity, so the same archive cannot be scanned twice by role.
    private static final LinkedHashMap<FileSnapshot, ArchiveFingerprint> FINGERPRINTS =
            new LinkedHashMap<FileSnapshot, ArchiveFingerprint>(16, 0.75f, true);

    private DexArchiveIdentity() {}

    public static String forApplication(ApplicationInfo info) {
        if (info == null || info.sourceDir == null || info.sourceDir.isEmpty()) return "";
        List<String> archives = new ArrayList<>();
        String base = fingerprint(info.sourceDir, true);
        if (base.isEmpty()) return "";
        archives.add("base{" + base + "}");
        String[] splits = info.splitSourceDirs;
        if (splits != null) {
            for (int i = 0; i < splits.length; i++) {
                String split = fingerprint(splits[i], false);
                if (split.isEmpty()) return "";
                archives.add("split" + i + "{" + split + "}");
            }
        }
        return String.join(";", archives);
    }

    public static String fingerprint(String path) {
        return fingerprint(path, true);
    }

    /** Fingerprints a resource/config split without requiring it to contain classes.dex. */
    public static String fingerprintSplit(String path) {
        return fingerprint(path, false);
    }

    /**
     * Fingerprints only DEX archives named by the active Tinker ClassLoader description.
     * Patch metadata, inactive patch directories and historical APKs are intentionally ignored.
     */
    public static String activeTinkerCodeIdentity(ClassLoader loader) {
        if (loader == null) return "none";
        String loaderDescription;
        try {
            loaderDescription = String.valueOf(loader);
        } catch (Throwable unavailable) {
            return "";
        }
        java.util.regex.Matcher matcher = ACTIVE_TINKER_DEX_ARCHIVE.matcher(loaderDescription);
        List<String> codeArchives = new ArrayList<>();
        while (matcher.find()) {
            String identity = codeFingerprint(matcher.group(1));
            if (identity.isEmpty()) return "";
            codeArchives.add(identity);
        }
        Collections.sort(codeArchives);
        return codeArchives.isEmpty() ? "none" : String.join(";", codeArchives);
    }

    private static String codeFingerprint(String path) {
        if (path == null || path.isEmpty()) return "";
        FileSnapshot before = snapshot(path);
        if (before == null) return "";
        synchronized (CACHE_LOCK) {
            ArchiveFingerprint cached = FINGERPRINTS.get(before);
            if (cached != null) return cached.hasDex ? cached.codeIdentity : "";
            ArchiveFingerprint fingerprint = scanArchive(before);
            if (fingerprint == null) return "";
            FileSnapshot after = snapshot(before.canonicalPath);
            if (!before.equals(after)) return "";
            FINGERPRINTS.put(before, fingerprint);
            while (FINGERPRINTS.size() > MAX_CACHED_ARCHIVES) {
                FileSnapshot eldest = FINGERPRINTS.keySet().iterator().next();
                FINGERPRINTS.remove(eldest);
            }
            return fingerprint.hasDex ? fingerprint.codeIdentity : "";
        }
    }

    /** Cheap change token for non-APK patch files; it reads filesystem metadata only. */
    static String fileChangeToken(String path) {
        FileSnapshot snapshot = snapshot(path);
        if (snapshot == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(snapshot.canonicalPath.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(snapshot.fileKey.getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update((snapshot.device + ":" + snapshot.inode + ":" + snapshot.size + ":"
                    + snapshot.mode + ":" + snapshot.modifiedNanos + ":" + snapshot.changedNanos)
                    .getBytes(StandardCharsets.UTF_8));
            return hex(digest.digest());
        } catch (Throwable unavailable) {
            return snapshot.canonicalPath + ":" + snapshot.device + ":" + snapshot.inode + ":"
                    + snapshot.size + ":" + snapshot.mode + ":" + snapshot.modifiedNanos
                    + ":" + snapshot.changedNanos;
        }
    }

    private static String fingerprint(String path, boolean requireDex) {
        if (path == null || path.isEmpty()) return "";
        FileSnapshot before = snapshot(path);
        if (before == null) return "";
        synchronized (CACHE_LOCK) {
            ArchiveFingerprint cached = FINGERPRINTS.get(before);
            if (cached != null) return requireDex && !cached.hasDex ? "" : cached.identity;
            ArchiveFingerprint fingerprint = scanArchive(before);
            if (fingerprint == null) return "";
            FileSnapshot after = snapshot(before.canonicalPath);
            // Never publish metadata read across an APK replacement or in-place update.
            if (!before.equals(after)) return "";
            FINGERPRINTS.put(before, fingerprint);
            while (FINGERPRINTS.size() > MAX_CACHED_ARCHIVES) {
                FileSnapshot eldest = FINGERPRINTS.keySet().iterator().next();
                FINGERPRINTS.remove(eldest);
            }
            return requireDex && !fingerprint.hasDex ? "" : fingerprint.identity;
        }
    }

    private static ArchiveFingerprint scanArchive(FileSnapshot snapshot) {
        File file = new File(snapshot.canonicalPath);
        List<String> dex = new ArrayList<>();
        MessageDigest metadataDigest;
        MessageDigest dexDigest;
        try {
            metadataDigest = MessageDigest.getInstance("SHA-256");
            dexDigest = MessageDigest.getInstance("SHA-256");
        } catch (Throwable unavailable) {
            return null;
        }
        int entryCount = 0;
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                entryCount++;
                String item = entry.getName() + ":" + entry.getCrc() + ":" + entry.getSize();
                // Hash only ZIP central-directory metadata. No APK/DEX payload is read here.
                metadataDigest.update((item + "\n").getBytes(StandardCharsets.UTF_8));
                if (DEX_ENTRY.matcher(entry.getName()).matches()) {
                    dex.add(item);
                    dexDigest.update((item + "\\n").getBytes(StandardCharsets.UTF_8));
                }
            }
        } catch (Throwable ignored) {
            return null;
        }
        if (entryCount == 0) return null;
        Collections.sort(dex);
        String contents = "dex{" + String.join(",", dex) + "};archive-metadata-sha256="
                + hex(metadataDigest.digest());
        return new ArchiveFingerprint(file.length() + ":" + file.lastModified() + ":" + contents,
                "dex{" + hex(dexDigest.digest()) + "}", !dex.isEmpty());
    }

    /** Cheap process-local invalidation key; full ZIP metadata remains the returned identity. */
    private static FileSnapshot snapshot(String path) {
        try {
            File file = new File(path);
            if (!file.isFile()) return null;
            String canonicalPath = file.getCanonicalPath();
            BasicFileAttributes attributes = Files.readAttributes(
                    new File(canonicalPath).toPath(), BasicFileAttributes.class);
            StructStat stat = Os.stat(canonicalPath);
            long changedNanos = stat.st_ctim.tv_sec * 1_000_000_000L + stat.st_ctim.tv_nsec;
            try {
                Object changeTime = Files.getAttribute(new File(canonicalPath).toPath(), "unix:ctime");
                if (changeTime instanceof FileTime) {
                    changedNanos = ((FileTime) changeTime).to(TimeUnit.NANOSECONDS);
                }
            } catch (Throwable ignored) {}
            Object fileKey = attributes.fileKey();
            return new FileSnapshot(canonicalPath,
                    fileKey != null ? fileKey.toString() : "",
                    stat.st_dev, stat.st_ino, attributes.size(), stat.st_mode,
                    attributes.lastModifiedTime().to(TimeUnit.NANOSECONDS), changedNanos);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static final class FileSnapshot {
        final String canonicalPath;
        final String fileKey;
        final long device;
        final long inode;
        final long size;
        final long mode;
        final long modifiedNanos;
        final long changedNanos;

        FileSnapshot(String canonicalPath, String fileKey,
                     long device, long inode, long size, long mode, long modifiedNanos,
                     long changedNanos) {
            this.canonicalPath = canonicalPath;
            this.fileKey = fileKey;
            this.device = device;
            this.inode = inode;
            this.size = size;
            this.mode = mode;
            this.modifiedNanos = modifiedNanos;
            this.changedNanos = changedNanos;
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof FileSnapshot)) return false;
            FileSnapshot that = (FileSnapshot) other;
            return device == that.device
                    && inode == that.inode
                    && size == that.size
                    && mode == that.mode
                    && modifiedNanos == that.modifiedNanos
                    && changedNanos == that.changedNanos
                    && fileKey.equals(that.fileKey)
                    && canonicalPath.equals(that.canonicalPath);
        }

        @Override public int hashCode() {
            int result = canonicalPath.hashCode();
            result = 31 * result + fileKey.hashCode();
            result = 31 * result + Long.hashCode(device);
            result = 31 * result + Long.hashCode(inode);
            result = 31 * result + Long.hashCode(size);
            result = 31 * result + Long.hashCode(mode);
            result = 31 * result + Long.hashCode(modifiedNanos);
            result = 31 * result + Long.hashCode(changedNanos);
            return result;
        }
    }

    private static final class ArchiveFingerprint {
        final String identity;
        final String codeIdentity;
        final boolean hasDex;

        ArchiveFingerprint(String identity, String codeIdentity, boolean hasDex) {
            this.identity = identity;
            this.codeIdentity = codeIdentity;
            this.hasDex = hasDex;
        }
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray();
        char[] result = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            result[i * 2] = digits[value >>> 4];
            result[i * 2 + 1] = digits[value & 0x0f];
        }
        return new String(result);
    }
}
