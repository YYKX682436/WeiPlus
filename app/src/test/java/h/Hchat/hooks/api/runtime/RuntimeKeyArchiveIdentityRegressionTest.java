package h.Hchat.hooks.api.runtime;

import android.content.Context;
import android.content.ContextWrapper;
import android.content.pm.ApplicationInfo;

import h.Hchat.dexkit.DexMethodCache;
import h.Hchat.hooks.api.model.WeChatVersionInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class RuntimeKeyArchiveIdentityRegressionTest {
    @Test public void productionRuntimeKeyPathRetainsStableArchiveIdentity() throws Exception {
        File apk = archiveWithDex();
        ApplicationInfo hostInfo = new ApplicationInfo();
        hostInfo.sourceDir = apk.getAbsolutePath();
        Context hostContext = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public ApplicationInfo getApplicationInfo() {
                return hostInfo;
            }
        };

        try {
            String first = DexMethodCache.INSTANCE.runtimeKey(hostContext, getClass().getClassLoader());
            String second = DexMethodCache.INSTANCE.runtimeKey(hostContext, getClass().getClassLoader());
            String third = DexMethodCache.INSTANCE.runtimeKey(hostContext, getClass().getClassLoader());

            assertFalse(first.isEmpty());
            assertEquals(first, second);
            assertEquals(second, third);
            // Measured ZIP enumeration counts are collected with temporary instrumentation;
            // this production-path regression remains free of persistent measurement hooks.
        } finally {
            apk.delete();
        }
    }

    @Test public void runtimeKeyIgnoresPatchMetadataButTracksLoadedTinkerDex() throws Exception {
        File apk = archiveWithDex();
        File dataDir = Files.createTempDirectory("weiplus-runtime-patch-").toFile();
        ApplicationInfo hostInfo = new ApplicationInfo();
        hostInfo.sourceDir = apk.getAbsolutePath();
        hostInfo.dataDir = dataDir.getAbsolutePath();
        Context hostContext = new ContextWrapper(RuntimeEnvironment.getApplication()) {
            @Override public ApplicationInfo getApplicationInfo() {
                return hostInfo;
            }
        };
        try {
            ClassLoader firstLoader = new ClassLoader() {
                @Override public String toString() { return "loader-A"; }
            };
            ClassLoader secondLoader = new ClassLoader() {
                @Override public String toString() { return "loader-B"; }
            };
            WeChatVersionInfo firstLoaderIdentity = WeChatVersionApi.build(hostContext, firstLoader);
            WeChatVersionInfo secondLoaderIdentity = WeChatVersionApi.build(hostContext, secondLoader);
            assertEquals("stable Dex cache scope intentionally excludes loader generation",
                    firstLoaderIdentity.cacheKey, secondLoaderIdentity.cacheKey);
            assertFalse("loader diagnostic changes while the archive cache retains no loader",
                    firstLoaderIdentity.classLoaderHash.equals(secondLoaderIdentity.classLoaderHash));

            File patchMetadata = new File(dataDir, "tinker/patch_meta.info");
            writeBytes(patchMetadata, new byte[]{1, 2, 3});
            String beforeMetadataUpdate = WeChatVersionApi.buildCacheKey(hostContext, firstLoader);
            writeBytes(patchMetadata, new byte[]{3, 2, 1});
            assertEquals("patch metadata alone must not change the Dex locator key",
                    beforeMetadataUpdate, WeChatVersionApi.buildCacheKey(hostContext, firstLoader));

            File patchDexJar = new File(dataDir, "tinker/patch-20261029/dex/classes.dex.jar");
            writeDexArchive(patchDexJar, new byte[]{1, 2, 3, 4});
            ClassLoader activePatchLoader = new ClassLoader() {
                @Override public String toString() {
                    return "AndroidNClassLoader[DexPathList[[zip file \"" + patchDexJar + "\"]]]";
                }
            };
            String beforeCodeUpdate = WeChatVersionApi.buildCacheKey(hostContext, activePatchLoader);
            java.nio.file.attribute.FileTime originalMtime = Files.getLastModifiedTime(patchDexJar.toPath());
            writeDexArchive(patchDexJar, new byte[]{4, 3, 2, 1});
            Files.setLastModifiedTime(patchDexJar.toPath(), originalMtime);
            String afterCodeUpdate = WeChatVersionApi.buildCacheKey(hostContext, activePatchLoader);
            assertFalse("runtime key must reflect changed DEX CRC even when size and mtime are restored",
                    beforeCodeUpdate.equals(afterCodeUpdate));
        } finally {
            apk.delete();
            deleteRecursively(dataDir);
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static void writeDexArchive(File file, byte[] dex) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(dex);
            zip.closeEntry();
        }
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) for (File child : children) deleteRecursively(child);
        file.delete();
    }

    private static File archiveWithDex() throws Exception {
        File file = File.createTempFile("weiplus-runtime-key-", ".apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(new byte[]{0x64, 0x65, 0x78, 0x0a});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("resources.arsc"));
            zip.write(new byte[]{1, 2, 3, 4});
            zip.closeEntry();
        }
        return file;
    }
}
