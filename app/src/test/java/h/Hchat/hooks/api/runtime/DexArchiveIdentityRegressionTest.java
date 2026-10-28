package h.Hchat.hooks.api.runtime;

import android.content.pm.ApplicationInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.FileOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

@RunWith(RobolectricTestRunner.class)
public class DexArchiveIdentityRegressionTest {
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

    private static File archiveWithStableDexAndResource(byte resourceByte) throws Exception {
        File file = File.createTempFile("weiplus-base-apk-", ".apk");
        try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
            zip.putNextEntry(new ZipEntry("classes.dex"));
            zip.write(new byte[]{0x64, 0x65, 0x78, 0x0a});
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("resources.arsc"));
            zip.write(new byte[]{resourceByte, 4, 5, 6});
            zip.closeEntry();
        }
        file.setLastModified(1_700_000_000_000L);
        return file;
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
