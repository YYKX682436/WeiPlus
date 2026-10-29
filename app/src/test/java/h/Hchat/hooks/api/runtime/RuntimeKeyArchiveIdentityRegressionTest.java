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

    @Test public void runtimeKeyRetainsLoaderAndTinkerPatchIdentity() throws Exception {
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

            File patch = new File(dataDir, "tinker/patch-20261029/patch.apk");
            assertTrue(patch.getParentFile().mkdirs());
            writeBytes(patch, new byte[]{1, 2, 3});
            String beforePatchUpdate = WeChatVersionApi.buildCacheKey(hostContext, firstLoader);
            java.nio.file.attribute.FileTime originalMtime = Files.getLastModifiedTime(patch.toPath());
            writeBytes(patch, new byte[]{3, 2, 1});
            Files.setLastModifiedTime(patch.toPath(), originalMtime);
            String afterPatchUpdate = WeChatVersionApi.buildCacheKey(hostContext, firstLoader);
            assertFalse("runtime key must reflect a same-size Tinker patch update with restored mtime",
                    beforePatchUpdate.equals(afterPatchUpdate));
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
