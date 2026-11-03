package h.Hchat.loader.utils;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import h.Hchat.hooks.api.runtime.HostApkPathDecision;
import android.os.Process;
import h.Hchat.hooks.core.XposedRuntime;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;
import java.util.zip.ZipFile;

/** Native 库加载器。 负责从模块 APK 中提取并加载 Native so，避免模块入口混入底层加载细节。 */
public class NativeLibraryLoader {
  private static final String TAG = "[Hchat:NativeLoader]";
  private static final Object EXTRACTION_LOCK = new Object();
  private static final NativeLoadCache LOADED_LIBRARIES = new NativeLoadCache();
  private static final String DEXKIT_SO = "libdexkit.so";
  private static final String SILK_CODEC_SO = "libsilk_codec.so";

  public void loadDexKit(Context ctx, ClassLoader moduleClassLoader) {
    loadLibrary(ctx, moduleClassLoader, DEXKIT_SO, false);
  }

  public boolean loadSilkCodec(Context ctx, ClassLoader moduleClassLoader) {
    return loadLibrary(ctx, moduleClassLoader, SILK_CODEC_SO, true);
  }

  /** The framework supplies the identity of this loaded module generation. */
  private String getModuleApkPath(Context context) {
    ApplicationInfo moduleInfo = XposedRuntime.get().getModuleApplicationInfo();
    String path = moduleInfo != null ? moduleInfo.sourceDir : null;
    if (path == null || !new File(path).isFile()) {
      throw new IllegalStateException("框架未提供可读的模块 APK");
    }
    android.content.pm.PackageInfo modulePackage = null;
    String installedPath = "";
    try {
      modulePackage = context.getPackageManager().getPackageArchiveInfo(path, 0);
    } catch (Throwable ignored) {}
    try {
      ApplicationInfo installedModule = context.getPackageManager().getApplicationInfo("h.Hchat", 0);
      installedPath = installedModule != null ? installedModule.sourceDir : "";
    } catch (Throwable ignored) {}
    h.Hchat.utils.HLog.i(TAG + " framework_module_apk version="
        + (modulePackage != null && modulePackage.versionName != null ? modulePackage.versionName : "")
        + " versionCode=" + (modulePackage != null ? modulePackage.versionCode : 0)
        + " source=" + path + " package_manager_source=" + installedPath);
    if (!installedPath.isEmpty() && !HostApkPathDecision.samePath(path, installedPath)) {
      h.Hchat.utils.HLog.e(TAG + " module_apk_path_diagnostic framework_loaded=" + path
          + " package_manager_installed=" + installedPath
          + " action=keep_framework_generation_no_fallback");
    }
    return path;
  }

  private boolean loadLibrary(
      Context ctx, ClassLoader moduleClassLoader, String soName, boolean optional) {
    if (!Process.is64Bit()) {
      String message = "当前版本仅支持 ARM64 微信进程，无法加载 " + soName;
      h.Hchat.utils.HLog.e(TAG + " " + message);
      if (optional) return false;
      throw new IllegalStateException(message);
    }
    return LOADED_LIBRARIES.load(
        moduleClassLoader,
        soName,
        () -> loadLibraryUncached(ctx, moduleClassLoader, soName, optional));
  }

  private boolean loadLibraryUncached(
      Context ctx, ClassLoader moduleClassLoader, String soName, boolean optional) {
    String moduleApk;
    String abi = "arm64-v8a";
    String entryPath = "lib/" + abi + "/" + soName;
    File cacheDir = new File(new File(ctx.getCacheDir(), "Hchat_native"), abi);

    try {
      moduleApk = getModuleApkPath(ctx);
      try (ZipFile apk = new ZipFile(moduleApk)) {
        java.util.zip.ZipEntry entry = apk.getEntry(entryPath);
        if (entry == null) {
          throw new IllegalStateException("模块 APK 缺少 Native 库: " + entryPath);
        }

        cacheDir.mkdirs();
        String cacheName = versionedLibraryName(soName, entry.getCrc());
        File destSo = new File(cacheDir, cacheName);
        h.Hchat.utils.HLog.i(TAG + " native_cache_identity so=" + soName
            + " abi=" + abi + " crc=" + Long.toHexString(entry.getCrc())
            + " cache=" + destSo.getName());
        synchronized (EXTRACTION_LOCK) {
          File lockFile = new File(cacheDir, cacheName + ".lock");
          try (RandomAccessFile lockAccess = new RandomAccessFile(lockFile, "rw");
              java.nio.channels.FileLock ignored = lockAccess.getChannel().lock()) {
            boolean reusedCache = isValidCachedLibrary(destSo, entry.getSize(), entry.getCrc());
            if (!reusedCache) {
              extractLibrary(apk, entry, cacheDir, cacheName, destSo);
            }
            try {
              System.load(destSo.getAbsolutePath());
            } catch (UnsatisfiedLinkError firstLoadError) {
              if (!reusedCache) throw firstLoadError;
              if (!destSo.delete()) {
                throw new IllegalStateException("无法删除损坏的 Native 缓存: " + destSo, firstLoadError);
              }
              extractLibrary(apk, entry, cacheDir, cacheName, destSo);
              try {
                System.load(destSo.getAbsolutePath());
              } catch (Throwable retryError) {
                retryError.addSuppressed(firstLoadError);
                throw retryError;
              }
            }
            deleteOldVersions(cacheDir, soName, destSo);
            return true;
          }
        }
      }
    } catch (Throwable e) {
      h.Hchat.utils.HLog.e(TAG + " " + soName + " 加载失败: " + e.getMessage(), e);
      if (optional) return false;
      throw new IllegalStateException("无法加载 " + soName, e);
    }
  }

  private String versionedLibraryName(String soName, long crc) {
    int dot = soName.lastIndexOf('.');
    String suffix = "-" + Long.toHexString(crc);
    if (dot <= 0) return soName + suffix;
    return soName.substring(0, dot) + suffix + soName.substring(dot);
  }

  private boolean isValidCachedLibrary(File file, long expectedSize, long expectedCrc) {
    if (!file.isFile() || file.length() <= 0 || expectedSize >= 0 && file.length() != expectedSize)
      return false;
    try (InputStream input = Files.newInputStream(file.toPath())) {
      CRC32 crc = new CRC32();
      byte[] buffer = new byte[8192];
      int length;
      while ((length = input.read(buffer)) > 0) crc.update(buffer, 0, length);
      return expectedCrc < 0 || crc.getValue() == expectedCrc;
    } catch (java.io.IOException failure) {
      return false;
    }
  }

  private void extractLibrary(
      ZipFile apk, java.util.zip.ZipEntry entry, File cacheDir, String cacheName, File destination)
      throws Exception {
    File temp =
        new File(
            cacheDir, cacheName + ".tmp-" + Process.myPid() + "-" + Thread.currentThread().getId());
    try {
      CRC32 crc = new CRC32();
      try (InputStream input = apk.getInputStream(entry);
          FileOutputStream output = new FileOutputStream(temp, false)) {
        byte[] buffer = new byte[8192];
        int length;
        while ((length = input.read(buffer)) > 0) {
          output.write(buffer, 0, length);
          crc.update(buffer, 0, length);
        }
        output.getFD().sync();
      }
      if (entry.getCrc() >= 0 && crc.getValue() != entry.getCrc()) {
        throw new IllegalStateException("Native 缓存 CRC 校验失败: " + cacheName);
      }
      try {
        Files.move(
            temp.toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ignored) {
        Files.move(temp.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      temp.delete();
    }
  }

  private void deleteOldVersions(File cacheDir, String soName, File current) {
    String prefix =
        soName.endsWith(".so") ? soName.substring(0, soName.length() - 3) + "-" : soName + "-";
    File[] files = cacheDir.listFiles();
    if (files == null) return;
    for (File file : files) {
      if (file == null || file.equals(current)) continue;
      String name = file.getName();
      if (name.equals(soName) || (name.startsWith(prefix) && name.endsWith(".so"))) {
        file.delete();
      }
    }
  }
}
