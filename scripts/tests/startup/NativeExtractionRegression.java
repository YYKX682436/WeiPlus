import h.Hchat.loader.utils.NativeLibraryLoader;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.zip.*;

public final class NativeExtractionRegression {
  private static void check(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    Path root = Path.of(args[0]);
    byte[] bytes =
        "deterministic module generation".getBytes(java.nio.charset.StandardCharsets.UTF_8);
    Path archive = root.resolve("module.apk");
    try (ZipOutputStream output = new ZipOutputStream(Files.newOutputStream(archive))) {
      output.putNextEntry(new ZipEntry("lib/arm64-v8a/libtest.so"));
      output.write(bytes);
      output.closeEntry();
    }
    NativeLibraryLoader loader = new NativeLibraryLoader();
    Method valid =
        NativeLibraryLoader.class.getDeclaredMethod(
            "isValidCachedLibrary", File.class, long.class, long.class);
    Method extract =
        NativeLibraryLoader.class.getDeclaredMethod(
            "extractLibrary", ZipFile.class, ZipEntry.class, File.class, String.class, File.class);
    Method name =
        NativeLibraryLoader.class.getDeclaredMethod(
            "versionedLibraryName", String.class, long.class);
    valid.setAccessible(true);
    extract.setAccessible(true);
    name.setAccessible(true);
    File cache = root.resolve("cache").toFile();
    cache.mkdirs();
    try (ZipFile zip = new ZipFile(archive.toFile())) {
      ZipEntry entry = zip.getEntry("lib/arm64-v8a/libtest.so");
      String cacheName = (String) name.invoke(loader, "libtest.so", entry.getCrc());
      File target = new File(cache, cacheName);
      check(
          !(boolean) valid.invoke(loader, target, entry.getSize(), entry.getCrc()),
          "Missing cache accepted");
      extract.invoke(loader, zip, entry, cache, cacheName, target);
      check(
          (boolean) valid.invoke(loader, target, entry.getSize(), entry.getCrc()),
          "Extracted CRC rejected");
      byte[] damaged = bytes.clone();
      damaged[0] ^= 1;
      Files.write(target.toPath(), damaged);
      check(
          !(boolean) valid.invoke(loader, target, entry.getSize(), entry.getCrc()),
          "Same-size corruption accepted");
      extract.invoke(loader, zip, entry, cache, cacheName, target);
      check(
          java.util.Arrays.equals(bytes, Files.readAllBytes(target.toPath())),
          "Corrupt cache not replaced");
      ZipEntry invalid = new ZipEntry(entry);
      invalid.setCrc(entry.getCrc() ^ 1);
      try {
        extract.invoke(loader, zip, invalid, cache, cacheName, target);
        throw new AssertionError("Bad APK CRC accepted");
      } catch (InvocationTargetException failure) {
        check(failure.getCause() instanceof IllegalStateException, "CRC failure changed");
      }
      check(
          java.util.Arrays.equals(bytes, Files.readAllBytes(target.toPath())),
          "Failed extraction replaced valid cache");
      check(
          java.util.Arrays.stream(cache.list()).noneMatch(value -> value.contains(".tmp-")),
          "Temporary extraction leaked");
      check(
          !cacheName.equals(name.invoke(loader, "libtest.so", entry.getCrc() ^ 1)),
          "Different generations share name");
    }
    System.out.println(
        "PASS native extraction: identity CRC, same-size corruption, replacement, failed extraction"
            + " cleanup");
  }
}
