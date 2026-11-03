package android.system;

public final class StructStat {
    public final long st_dev;
    public final long st_ino;
    public final long st_mode;
    public final Timespec st_ctim;

    public StructStat(long device, long inode, long mode, long seconds, long nanos) {
        st_dev = device;
        st_ino = inode;
        st_mode = mode;
        st_ctim = new Timespec(seconds, nanos);
    }

    public static final class Timespec {
        public final long tv_sec;
        public final long tv_nsec;
        Timespec(long seconds, long nanos) { tv_sec = seconds; tv_nsec = nanos; }
    }
}
