package h.Hchat.hooks.api.runtime;

import android.content.pm.ApplicationInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Reads APK central-directory DEX metadata only; never streams or hashes the APK payload. */
public final class DexArchiveIdentity {
    private static final Pattern DEX_ENTRY = Pattern.compile("classes(?:[0-9]+)?\\.dex");

    private DexArchiveIdentity() {}

    public static String forApplication(ApplicationInfo info) {
        if (info == null || info.sourceDir == null || info.sourceDir.isEmpty()) return "";
        List<String> archives = new ArrayList<>();
        String base = fingerprint(info.sourceDir);
        if (base.isEmpty()) return "";
        archives.add("base{" + base + "}");
        String[] splits = info.splitSourceDirs;
        if (splits != null) {
            for (int i = 0; i < splits.length; i++) {
                String split = fingerprint(splits[i]);
                if (split.isEmpty()) return "";
                archives.add("split" + i + "{" + split + "}");
            }
        }
        return String.join(";", archives);
    }

    public static String fingerprint(String path) {
        if (path == null || path.isEmpty()) return "";
        File file = new File(path);
        if (!file.isFile()) return "";
        List<String> dex = new ArrayList<>();
        try (ZipFile zip = new ZipFile(file)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory() && DEX_ENTRY.matcher(entry.getName()).matches()) {
                    dex.add(entry.getName() + ":" + entry.getCrc() + ":" + entry.getSize());
                }
            }
        } catch (Throwable ignored) {
            return "";
        }
        if (dex.isEmpty()) return "";
        Collections.sort(dex);
        return file.length() + ":" + file.lastModified() + ":" + String.join(",", dex);
    }
}
