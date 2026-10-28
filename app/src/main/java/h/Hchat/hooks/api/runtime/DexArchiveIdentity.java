package h.Hchat.hooks.api.runtime;

import android.content.pm.ApplicationInfo;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads APK central-directory entry metadata; never streams or hashes APK/DEX payload bytes. */
public final class DexArchiveIdentity {
    private static final Pattern DEX_ENTRY = Pattern.compile("classes(?:[0-9]+)?\\.dex");

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

    private static String fingerprint(String path, boolean requireDex) {
        if (path == null || path.isEmpty()) return "";
        File file = new File(path);
        if (!file.isFile()) return "";
        List<String> dex = new ArrayList<>();
        MessageDigest metadataDigest;
        try {
            metadataDigest = MessageDigest.getInstance("SHA-256");
        } catch (Throwable unavailable) {
            return "";
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
                }
            }
        } catch (Throwable ignored) {
            return "";
        }
        if ((dex.isEmpty() && requireDex) || entryCount == 0) return "";
        Collections.sort(dex);
        String contents = "dex{" + String.join(",", dex) + "};archive-metadata-sha256="
                + hex(metadataDigest.digest());
        return file.length() + ":" + file.lastModified() + ":" + contents;
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
