package android.system;

import java.nio.file.Files;
import java.nio.file.Path;

public final class Os {
    private Os() {}

    public static StructStat stat(String path) throws Exception {
        Path file = Path.of(path);
        long device = ((Number) Files.getAttribute(file, "unix:dev")).longValue();
        long inode = ((Number) Files.getAttribute(file, "unix:ino")).longValue();
        long mode = ((Number) Files.getAttribute(file, "unix:mode")).longValue();
        Object ctime = Files.getAttribute(file, "unix:ctime");
        long milliseconds = ctime instanceof java.nio.file.attribute.FileTime
                ? ((java.nio.file.attribute.FileTime) ctime).toMillis()
                : Files.getLastModifiedTime(file).toMillis();
        long seconds = Math.floorDiv(milliseconds, 1000L);
        long nanos = Math.floorMod(milliseconds, 1000L) * 1_000_000L;
        return new StructStat(device, inode, mode, seconds, nanos);
    }
}
