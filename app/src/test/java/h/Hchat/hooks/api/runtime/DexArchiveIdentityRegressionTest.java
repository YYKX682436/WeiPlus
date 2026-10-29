package h.Hchat.hooks.api.runtime;

import android.content.pm.ApplicationInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
public class DexArchiveIdentityRegressionTest {
    @Test public void sameArchiveSharesCacheAcrossBaseAndSplitValidationModes() throws Exception {
        File archive = resourceApk("resources.arsc", new byte[]{1, 2, 3, 4});
        try {
            assertEquals("a resource-only archive is not a valid base APK", "",
                    DexArchiveIdentity.fingerprint(archive.getAbsolutePath()));
            assertFalse("the same archive is valid as a resource split",
                    DexArchiveIdentity.fingerprintSplit(archive.getAbsolutePath()).isEmpty());
        } finally {
            archive.delete();
        }
    }

    @Test public void resourceOnlySplitHasStableAndDistinctIdentity() throws Exception {
        File first = resourceApk("resources.arsc", new byte[]{1, 2, 3, 4});
        File second = resourceApk("resources.arsc", new byte[]{1, 2, 3, 5});
        try {
            String firstIdentity = DexArchiveIdentity.fingerprintSplit(first.getAbsolutePath());
            String secondIdentity = DexArchiveIdentity.fingerprintSplit(second.getAbsolutePath());
            assertFalse("resource/config split without classes.dex is valid", firstIdentity.isEmpty());
            assertNotEquals("resource CRC/size must distinguish split content", firstIdentity, secondIdentity);
        } finally {
            first.delete();
            second.delete();
        }
    }

    @Test public void applicationIdentityAcceptsResourceOnlyConfigurationSplit() throws Exception {
        File base = resourceApk("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a});
        File split = resourceApk("resources.arsc", new byte[]{8, 7, 6, 5});
        File changedSplit = resourceApk("resources.arsc", new byte[]{8, 7, 6, 4});
        ApplicationInfo info = new ApplicationInfo();
        info.sourceDir = base.getAbsolutePath();
        info.splitSourceDirs = new String[]{split.getAbsolutePath()};
        try {
            String identity = DexArchiveIdentity.forApplication(info);
            assertFalse("a resource-only split must not invalidate a healthy base APK", identity.isEmpty());
            ApplicationInfo changed = new ApplicationInfo();
            changed.sourceDir = base.getAbsolutePath();
            changed.splitSourceDirs = new String[]{changedSplit.getAbsolutePath()};
            assertNotEquals("split entry metadata is part of the runtime identity",
                    identity, DexArchiveIdentity.forApplication(changed));
        } finally {
            base.delete();
            split.delete();
            changedSplit.delete();
        }
    }

    @Test public void changingResourceSplitAtSamePathInvalidatesApplicationIdentity() throws Exception {
        File base = resourceApk("classes.dex", new byte[]{0x64, 0x65, 0x78, 0x0a});
        File split = resourceApk("resources.arsc", new byte[]{8, 7, 6, 5});
        File replacement = resourceApk("resources.arsc", new byte[]{8, 7, 6, 4});
        File marker = new File(split.getParentFile(), "weiplus-split-marker-" + System.nanoTime());
        long splitModified = split.lastModified();
        replacement.setLastModified(splitModified);
        split.setLastModified(splitModified);
        ApplicationInfo info = new ApplicationInfo();
        info.sourceDir = base.getAbsolutePath();
        info.splitSourceDirs = new String[]{split.getAbsolutePath()};
        try {
            String before = DexArchiveIdentity.forApplication(info);
            Files.move(replacement.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(marker.toPath(), split.toPath(), StandardCopyOption.REPLACE_EXISTING);
            split.setLastModified(splitModified);

            String after = DexArchiveIdentity.forApplication(info);
            assertNotEquals("resource split archive metadata is retained in the identity", before, after);
        } finally {
            base.delete();
            split.delete();
            replacement.delete();
            marker.delete();
        }
    }

    @Test public void baseArchiveResourceChangeInvalidatesIdentityEvenWhenVersionAndFileMetadataMatch() throws Exception {
        File first = archiveWithStableDexAndResource((byte) 1);
        File second = archiveWithStableDexAndResource((byte) 2);
        try {
            assertEquals("fixture keeps archive length constant", first.length(), second.length());
            assertEquals("fixture keeps file modification time constant", first.lastModified(), second.lastModified());
            assertNotEquals("central-directory metadata must identify a changed APK",
                    DexArchiveIdentity.fingerprint(first.getAbsolutePath()),
                    DexArchiveIdentity.fingerprint(second.getAbsolutePath()));
        } finally {
            first.delete();
            second.delete();
        }
    }

    @Test public void replacingApkAtSamePathInvalidatesFingerprintDespiteSameSizeAndMtime() throws Exception {
        File apk = archiveWithStableDexAndResource((byte) 1);
        File replacement = archiveWithStableDexAndResource((byte) 2);
        File marker = new File(apk.getParentFile(), "weiplus-apk-marker-" + System.nanoTime());
        long modified = apk.lastModified();
        long length = apk.length();
        String before;
        try {
            assertEquals(length, replacement.length());
            replacement.setLastModified(modified);
            apk.setLastModified(modified);
            before = DexArchiveIdentity.fingerprint(apk.getAbsolutePath());
            Files.move(replacement.toPath(), marker.toPath(), StandardCopyOption.REPLACE_EXISTING);
            Files.move(marker.toPath(), apk.toPath(), StandardCopyOption.REPLACE_EXISTING);
            apk.setLastModified(modified);

            assertEquals("replacement fixture preserves size", length, apk.length());
            assertEquals("replacement fixture preserves mtime", modified, apk.lastModified());
            assertNotEquals("inode/ctime change invalidates a same-path APK replacement",
                    before, DexArchiveIdentity.fingerprint(apk.getAbsolutePath()));
        } finally {
            apk.delete();
            replacement.delete();
            marker.delete();
        }
    }

    @Test public void inPlaceArchiveUpdateInvalidatesFingerprintWhenSizeAndMtimeAreRestored() throws Exception {
        File apk = archiveWithStableDexAndResource((byte) 1);
        long modified = apk.lastModified();
        long length = apk.length();
        try {
            String before = DexArchiveIdentity.fingerprint(apk.getAbsolutePath());
            writeArchive(apk, (byte) 2);
            apk.setLastModified(modified);

            assertEquals(length, apk.length());
            assertEquals(modified, apk.lastModified());
            assertNotEquals("ctime identifies an in-place write with restored size and mtime",
                    before, DexArchiveIdentity.fingerprint(apk.getAbsolutePath()));
        } finally {
            apk.delete();
        }
    }

    @Test public void concurrentRuntimeIdentityRequestsShareOneArchiveEnumeration() throws Exception {
        File apk = archiveWithStableDexAndResource((byte) 3);
        int callers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        try {
            @SuppressWarnings("unchecked")
            Future<String>[] results = new Future[callers];
            for (int i = 0; i < callers; i++) {
                results[i] = pool.submit(() -> {
                    ready.countDown();
                    if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("start timed out");
                    return DexArchiveIdentity.fingerprint(apk.getAbsolutePath());
                });
            }
            if (!ready.await(5, TimeUnit.SECONDS)) throw new AssertionError("workers not ready");
            start.countDown();
            String expected = results[0].get(5, TimeUnit.SECONDS);
            assertFalse(expected.isEmpty());
            for (int i = 1; i < callers; i++) assertEquals(expected, results[i].get(5, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
            apk.delete();
        }
    }

    @Test public void failedReadIsNotCachedAndLaterValidArchiveCanBeIdentified() throws Exception {
        File apk = File.createTempFile("weiplus-retry-identity-", ".apk");
        try {
            assertEquals("", DexArchiveIdentity.fingerprint(apk.getAbsolutePath()));
            writeArchive(apk, (byte) 7);
            String identity = DexArchiveIdentity.fingerprint(apk.getAbsolutePath());
            assertFalse(identity.isEmpty());
        } finally {
            apk.delete();
        }
    }

    @Test public void archiveFingerprintCacheIsBounded() throws Exception {
        File[] archives = new File[129];
        try {
            for (int i = 0; i < archives.length; i++) {
                archives[i] = archiveWithStableDexAndResource((byte) i);
                assertFalse(DexArchiveIdentity.fingerprint(archives[i].getAbsolutePath()).isEmpty());
            }
            assertEquals("the process-local cache stays within its fixed capacity", 128,
                    cachedArchiveCount());
            DexArchiveIdentity.fingerprint(archives[0].getAbsolutePath());
            assertEquals("a cache miss after eviction does not exceed the fixed capacity", 128,
                    cachedArchiveCount());
        } finally {
            for (File archive : archives) if (archive != null) archive.delete();
        }
    }

    private static int cachedArchiveCount() throws Exception {
        java.lang.reflect.Field field = DexArchiveIdentity.class.getDeclaredField("FINGERPRINTS");
        field.setAccessible(true);
        java.lang.reflect.Field lockField = DexArchiveIdentity.class.getDeclaredField("CACHE_LOCK");
        lockField.setAccessible(true);
        Object lock = lockField.get(null);
        synchronized (lock) {
            return ((java.util.Map<?, ?>) field.get(null)).size();
        }
    }

    private static File archiveWithStableDexAndResource(byte resourceByte) throws Exception {
        File file = File.createTempFile("weiplus-base-apk-", ".apk");
        writeArchive(file, resourceByte);
        file.setLastModified(1_700_000_000_000L);
        return file;
    }

    private static void writeArchive(File file, byte resourceByte) throws Exception {
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(new byte[]{0x64, 0x65, 0x78, 0x0a});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("resources.arsc"));
            zip.write(new byte[]{resourceByte, 4, 5, 6});
            zip.closeEntry();
        }
    }

    private static File resourceApk(String name, byte[] content) throws Exception {
        File file = File.createTempFile("weiplus-resource-split-", ".apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry(name));
            zip.write(content);
            zip.closeEntry();
        }
        return file;
    }
}
