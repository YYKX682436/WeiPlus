package h.Hchat.hooks.api.runtime;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.TextUtils;

import h.Hchat.hooks.api.model.WeChatVersionInfo;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 微信版本、clientVersion 与 Tinker 热更新指纹 API。
 */
public final class WeChatVersionApi {
    public interface Logger {
        void log(String message);
    }

    private static final Pattern CLIENT_VER_PATTERN =
            Pattern.compile("(?:patch\\.client\\.ver|clientVersion|CLIENT_VERSION)\\s*[=:]\\s*(0x[0-9a-fA-F]+|\\d+)");
    private static final Pattern TINKER_ID_PATTERN =
            Pattern.compile("(?:NEW_TINKER_ID|TINKER_ID)\\s*[=:]\\s*([^,}\\s]+)");
    private static final Pattern PATCH_ID_PATTERN =
            Pattern.compile("intent_patch_(?:new|old)_version\\s*[=:]\\s*([^,}\\s]+)");
    private static final String STABLE_CACHE_SCOPE = "stable";

    private final Context hostContext;
    private final ClassLoader classLoader;
    private final Logger logger;
    private volatile WeChatVersionInfo cached;
    private volatile long cachedAt;
    private static final long IDENTITY_REFRESH_INTERVAL_MS = 3000L;

    public WeChatVersionApi(Context hostContext, ClassLoader classLoader, Logger logger) {
        this.hostContext = hostContext;
        this.classLoader = classLoader;
        this.logger = logger;
    }

    public boolean isAvailable() {
        return hostContext != null;
    }

    public synchronized WeChatVersionInfo current() {
        WeChatVersionInfo value = cached;
        long now = System.currentTimeMillis();
        if (value != null && now - cachedAt < IDENTITY_REFRESH_INTERVAL_MS) return value;
        return refresh();
    }

    /** Force a fresh hashless APK/patch identity check after a Tinker or runtime transition. */
    public synchronized WeChatVersionInfo refresh() {
        WeChatVersionInfo fresh = build(hostContext, classLoader);
        cached = fresh;
        cachedAt = System.currentTimeMillis();
        return fresh;
    }

    public String packageName() {
        return current().packageName;
    }

    public String versionName() {
        return current().versionName;
    }

    public long versionCode() {
        return current().versionCode;
    }

    public String clientVersion() {
        return current().clientVersion;
    }

    public String tinkerId() {
        return current().tinkerId;
    }

    public String patchId() {
        return current().patchId;
    }

    public String cacheKey() {
        return current().cacheKey;
    }

    public boolean hasTinkerPatch() {
        return current().hasTinkerPatch();
    }

    public static WeChatVersionInfo build(Context context, ClassLoader loader) {
        if (context == null) {
            return new WeChatVersionInfo("", "", 0L, "", "", "", 0L,
                    "", "", loaderHash(loader), "");
        }

        String packageName = context.getPackageName();
        PackageInfo packageInfo = null;
        ApplicationInfo appInfo = null;
        try {
            packageInfo = context.getPackageManager().getPackageInfo(packageName, 0);
        } catch (Throwable ignored) {}
        try {
            appInfo = context.getApplicationInfo();
        } catch (Throwable ignored) {}
        return buildForPackage(context, packageName, packageInfo, appInfo, loader);
    }

    /** Package-ready counterpart; uses the supplied PackageInfo/ApplicationInfo, not Application. */
    public static WeChatVersionInfo buildForPackageReady(Context metadataContext,
                                                         String packageName,
                                                         PackageInfo packageInfo,
                                                         ApplicationInfo appInfo,
                                                         ClassLoader loader) {
        return buildForPackage(metadataContext, packageName, packageInfo, appInfo, loader);
    }

    private static WeChatVersionInfo buildForPackage(Context metadataContext,
                                                     String packageName,
                                                     PackageInfo packageInfo,
                                                     ApplicationInfo appInfo,
                                                     ClassLoader loader) {
        String versionName = packageInfo != null && packageInfo.versionName != null
                ? packageInfo.versionName : "";
        long versionCode = packageInfo == null ? 0L
                : Build.VERSION.SDK_INT >= 28 ? packageInfo.getLongVersionCode() : packageInfo.versionCode;
        String sourcePath = appInfo != null ? appInfo.sourceDir : "";
        long sourceLastModified = 0L;
        if (!TextUtils.isEmpty(sourcePath)) {
            File source = new File(sourcePath);
            sourceLastModified = source.lastModified();
        }
        String apkDexIdentity = DexArchiveIdentity.forApplication(appInfo);
        String patchFileIdentity = patchFileIdentity(appInfo);

        String loaderText = String.valueOf(loader);
        String clientVersion = readBuildConfigClientVersion(loader);
        if (TextUtils.isEmpty(clientVersion)) clientVersion = match(CLIENT_VER_PATTERN, loaderText);
        if (TextUtils.isEmpty(clientVersion)) clientVersion = readTinkerValue(appInfo, "patch.client.ver");
        if (TextUtils.isEmpty(clientVersion)) clientVersion = readTinkerValue(appInfo, "client.ver");
        String tinkerId = match(TINKER_ID_PATTERN, loaderText);
        if (TextUtils.isEmpty(tinkerId)) tinkerId = readTinkerValue(appInfo, "NEW_TINKER_ID");
        if (TextUtils.isEmpty(tinkerId)) tinkerId = readTinkerValue(appInfo, "TINKER_ID");
        String patchId = match(PATCH_ID_PATTERN, loaderText);
        if (TextUtils.isEmpty(patchId)) patchId = readPatchDirectoryName(appInfo);
        String classLoaderHash = loaderHash(loader);

        String cacheKey = buildCacheKey(packageName, versionName, versionCode,
                clientVersion, tinkerId, patchId, sourceLastModified, apkDexIdentity,
                patchFileIdentity, STABLE_CACHE_SCOPE);
        return new WeChatVersionInfo(packageName, versionName, versionCode,
                clientVersion, tinkerId, patchId, sourceLastModified,
                apkDexIdentity, patchFileIdentity, classLoaderHash, cacheKey);
    }

    public static boolean hasCompleteRuntimeIdentity(WeChatVersionInfo info) {
        return info != null
                && WeChatVersionGate.isTargetPackage(info.packageName)
                && WeChatVersionGate.parse(info.versionName) != null
                && !TextUtils.isEmpty(info.apkDexIdentity)
                && !TextUtils.isEmpty(info.cacheKey);
    }

    public static String classLoaderIdentity(ClassLoader loader) {
        return loaderHash(loader);
    }

    public static String buildCacheKey(Context context, ClassLoader loader) {
        return build(context, loader).cacheKey;
    }

    private static String buildCacheKey(String packageName,
                                        String versionName,
                                        long versionCode,
                                        String clientVersion,
                                        String tinkerId,
                                        String patchId,
                                        long sourceLastModified,
                                        String apkDexIdentity,
                                        String patchFileIdentity,
                                        String classLoaderHash) {
        StringBuilder sb = new StringBuilder();
        sb.append(safe(packageName))
                .append('|').append(safe(versionName))
                .append('|').append(versionCode)
                .append('|').append(safe(clientVersion))
                .append('|').append(safe(tinkerId))
                .append('|').append(safe(patchId))
                .append('|').append(sourceLastModified)
                .append('|').append(safe(apkDexIdentity))
                .append('|').append(safe(patchFileIdentity))
                .append('|').append(safe(classLoaderHash));
        return sb.toString();
    }

    private static String readTinkerValue(ApplicationInfo appInfo, String key) {
        if (appInfo == null || TextUtils.isEmpty(appInfo.dataDir) || TextUtils.isEmpty(key)) return "";
        String value = scanTextFiles(new File(appInfo.dataDir, "tinker"), key, 0, new int[]{0});
        return value != null ? value : "";
    }

    private static String scanTextFiles(File dir, String key, int depth, int[] visited) {
        if (dir == null || !dir.exists() || depth > 4 || visited[0] >= 256) return "";
        File[] files = dir.listFiles();
        if (files == null) return "";
        for (File file : files) {
            if (++visited[0] > 256) return "";
            if (file == null) continue;
            if (file.isDirectory()) {
                String value = scanTextFiles(file, key, depth + 1, visited);
                if (!TextUtils.isEmpty(value)) return value;
                continue;
            }
            String name = file.getName();
            if (TextUtils.isEmpty(name)
                    || (!name.endsWith(".meta") && !name.endsWith(".txt")
                    && !name.endsWith(".properties"))) {
                continue;
            }
            String value = readKeyFromFile(file, key);
            if (!TextUtils.isEmpty(value)) return value;
        }
        return "";
    }

    private static String readKeyFromFile(File file, String key) {
        if (file == null || file.length() > 64 * 1024) return "";
        java.io.BufferedReader reader = null;
        try {
            reader = new java.io.BufferedReader(new java.io.FileReader(file));
            String line;
            while ((line = reader.readLine()) != null) {
                int index = line.indexOf(key);
                if (index < 0) continue;
                int eq = line.indexOf('=', index + key.length());
                if (eq < 0) eq = line.indexOf(':', index + key.length());
                if (eq < 0 || eq + 1 >= line.length()) continue;
                return line.substring(eq + 1).trim();
            }
        } catch (Throwable ignored) {
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (Throwable ignored) {}
            }
        }
        return "";
    }

    private static String readPatchDirectoryName(ApplicationInfo appInfo) {
        try {
            if (appInfo == null || TextUtils.isEmpty(appInfo.dataDir)) return "";
            File tinkerDir = new File(appInfo.dataDir, "tinker");
            File[] files = tinkerDir.listFiles();
            if (files == null) return "";
            long newestTime = 0L;
            String newest = "";
            for (File file : files) {
                if (file == null || !file.isDirectory()) continue;
                String name = file.getName();
                if (TextUtils.isEmpty(name) || !name.startsWith("patch-")) continue;
                long time = file.lastModified();
                if (time >= newestTime) {
                    newestTime = time;
                    newest = name;
                }
            }
            return newest;
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String patchFileIdentity(ApplicationInfo appInfo) {
        if (appInfo == null || TextUtils.isEmpty(appInfo.dataDir)) return "none";
        File root = new File(appInfo.dataDir, "tinker");
        if (!root.exists()) return "none";
        List<String> entries = new ArrayList<>();
        collectPatchMetadata(root, root, 0, new int[]{0}, entries);
        Collections.sort(entries);
        if (entries.isEmpty()) return "present-empty:" + root.lastModified();
        StringBuilder value = new StringBuilder();
        for (String entry : entries) {
            if (value.length() > 0) value.append(',');
            value.append(entry);
        }
        return value.toString();
    }

    private static void collectPatchMetadata(File root, File current, int depth, int[] visited,
                                             List<String> output) {
        if (current == null || depth > 4 || visited[0] >= 256) return;
        File[] files = current.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file == null || ++visited[0] > 256) return;
            if (file.isDirectory()) {
                if (current.equals(root) && !file.getName().startsWith("patch-")) continue;
                collectPatchMetadata(root, file, depth + 1, visited, output);
                continue;
            }
            boolean insidePatch = !current.equals(root);
            String name = file.getName();
            if (!insidePatch && !(name.startsWith("patch")
                    || name.startsWith("intent_patch") || name.startsWith("tinker"))) continue;
            String relative = file.getAbsolutePath().substring(root.getAbsolutePath().length());
            output.add(relative + ':' + file.length() + ':' + file.lastModified());
        }
    }

    private static String match(Pattern pattern, String text) {
        if (pattern == null || TextUtils.isEmpty(text)) return "";
        try {
            Matcher matcher = pattern.matcher(text);
            return matcher.find() ? matcher.group(1) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readBuildConfigClientVersion(ClassLoader loader) {
        if (loader == null) return "";
        try {
            Class<?> clazz = Class.forName("com.tencent.mm.boot.BuildConfig", false, loader);
            String value = readStaticField(clazz, "CLIENT_VERSION_ARM64");
            if (TextUtils.isEmpty(value)) value = readStaticField(clazz, "CLIENT_VERSION");
            if (TextUtils.isEmpty(value)) value = readStaticField(clazz, "CLIENT_VERSION_INT");
            if (TextUtils.isEmpty(value)) value = readStaticField(clazz, "CLIENTVERSION");
            return value != null ? value.trim() : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String readStaticField(Class<?> clazz, String fieldName) {
        if (clazz == null || TextUtils.isEmpty(fieldName)) return "";
        try {
            java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
            field.setAccessible(true);
            Object value = field.get(null);
            return value != null ? String.valueOf(value) : "";
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static String loaderHash(ClassLoader loader) {
        return Integer.toHexString(String.valueOf(loader).hashCode());
    }

    private static String safe(String value) {
        return value != null ? value : "";
    }

    private void log(String message) {
        if (logger != null) logger.log("[WeChatVersionApi] " + message);
    }
}
