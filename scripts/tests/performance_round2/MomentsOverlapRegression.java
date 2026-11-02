package h.Hchat.hooks.items.moments;
import java.util.TimeZone;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class MomentsOverlapRegression {
    static final AtomicInteger scheduled=new AtomicInteger();
    static final MomentsRefreshScheduler.Clock CLOCK=new MomentsRefreshScheduler.Clock() {
        public long now() { return System.currentTimeMillis(); }
        public long elapsed() { return System.nanoTime()/1_000_000L; }
        public TimeZone timeZone() { return TimeZone.getTimeZone("UTC"); }
    };
    static MomentsRefreshScheduler.TimerFactory timers() {
        return () -> {
            ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
            return new MomentsRefreshScheduler.Timer() {
                public MomentsRefreshScheduler.Cancel schedule(Runnable task,long delay) {
                    scheduled.incrementAndGet();
                    ScheduledFuture<?> future=worker.schedule(task,delay,TimeUnit.MILLISECONDS);
                    return () -> future.cancel(false);
                }
                public void shutdown() { worker.shutdownNow(); }
            };
        };
    }
    static void check(boolean ok,String message) { if (!ok) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        AtomicInteger active=new AtomicInteger(), peak=new AtomicInteger(), calls=new AtomicInteger();
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1), second=new CountDownLatch(1);
        MomentsRefreshScheduler.Refresh refresh=new MomentsRefreshScheduler.Refresh() {
            public boolean refresh() {
                int n=active.incrementAndGet(); peak.accumulateAndGet(n,Math::max);
                int call=calls.incrementAndGet();
                if (call==1) {
                    entered.countDown();
                    for (;;) try { release.await(); break; } catch (InterruptedException ignored) { }
                } else second.countDown();
                active.decrementAndGet(); return true;
            }
            public void result(long time,boolean success,Throwable error) { }
        };
        MomentsRefreshScheduler.Config enabled=new MomentsRefreshScheduler.Config(true,300_000,false,"","");
        MomentsRefreshScheduler first=new MomentsRefreshScheduler(CLOCK,timers(),refresh);
        MomentsRefreshScheduler next=new MomentsRefreshScheduler(CLOCK,timers(),refresh);
        try {
            first.configure(enabled); check(entered.await(5,TimeUnit.SECONDS),"first request did not start");
            first.destroy(); next.configure(enabled);
            Thread.sleep(1500); check(calls.get()==1 && peak.get()==1,"replacement overlapped unfinished request");
            check(scheduled.get()==2,"replacement polled while waiting for the old request");
            release.countDown(); check(second.await(5,TimeUnit.SECONDS),"replacement did not recover");
            check(peak.get()==1,"refreshes overlapped");
        } finally { release.countDown(); first.destroy(); next.destroy(); }
        System.out.println("PASS: destroyed runtime and replacement cannot overlap an in-flight refresh");
    }
}
