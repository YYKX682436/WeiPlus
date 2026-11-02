package h.Hchat.hooks.items.moments;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

public final class MomentsRefreshSchedulerRegression {
    static final class Fixture implements MomentsRefreshScheduler.Clock, MomentsRefreshScheduler.TimerFactory,
            MomentsRefreshScheduler.Refresh {
        long wall = Instant.parse("2026-01-01T12:00:00Z").toEpochMilli();
        long monotonic;
        TimeZone zone = TimeZone.getTimeZone("UTC");
        int refreshes, results, created, shutdowns;
        boolean succeed = true;
        final List<Task> tasks = new ArrayList<>();
        MomentsRefreshScheduler scheduler = new MomentsRefreshScheduler(this, this, this);
        public long now() { return wall; }
        public long elapsed() { return monotonic; }
        public TimeZone timeZone() { return zone; }
        public MomentsRefreshScheduler.Timer create() {
            created++;
            return new MomentsRefreshScheduler.Timer() {
                boolean stopped;
                public MomentsRefreshScheduler.Cancel schedule(Runnable runnable, long delay) {
                    if (stopped) throw new AssertionError("scheduled after shutdown");
                    Task t = new Task(monotonic + delay, runnable); tasks.add(t);
                    return () -> t.cancelled = true;
                }
                public void shutdown() { stopped = true; shutdowns++; }
            };
        }
        public boolean refresh() { refreshes++; return succeed; }
        public void result(long time, boolean success, Throwable error) { results++; }
        void advance(long millis) {
            long target = monotonic + millis;
            int executed = 0;
            for (;;) {
                Task next = tasks.stream().filter(t -> !t.cancelled && t.due <= target)
                    .min((a,b) -> Long.compare(a.due,b.due)).orElse(null);
                if (next == null) break;
                if (++executed > 100) throw new AssertionError("scheduler looped without advancing time");
                wall += next.due - monotonic; monotonic = next.due; next.cancelled = true; next.action.run();
            }
            wall += target - monotonic; monotonic = target;
        }
        void jump(String time) { wall = Instant.parse(time).toEpochMilli(); scheduler.clockChanged(); }
        int pending() { return (int) tasks.stream().filter(t -> !t.cancelled).count(); }
    }
    static final class Task {
        final long due; final Runnable action; boolean cancelled;
        Task(long due, Runnable action) { this.due=due; this.action=action; }
    }
    static MomentsRefreshScheduler.Config config(boolean enabled, long interval, boolean window, String start, String end) {
        return new MomentsRefreshScheduler.Config(enabled, interval, window, start, end);
    }
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) {
        Fixture f = new Fixture();
        f.scheduler.configure(config(true, 300_000, false, "08:00:00", "23:00:00"));
        f.advance(999); check(f.refreshes==0,"first refresh too early");
        f.advance(1); check(f.refreshes==1 && f.results==1,"first refresh missing");
        f.advance(299_999); check(f.refreshes==1 && f.pending()==1,"unexpected fixed polling/early refresh");
        f.advance(1); check(f.refreshes==2,"interval refresh missing");
        f.advance(20_000); f.scheduler.configure(config(true, 10_000, false, "08:00", "23:00"));
        f.advance(0); check(f.refreshes==3,"shortened interval not applied immediately");
        f.succeed=false; f.scheduler.configure(config(true, 300_000, false, "08:00", "23:00"));
        f.advance(300_000); check(f.refreshes==4,"failed attempt missing");
        f.advance(29_999); check(f.refreshes==4,"failure retried early");
        f.advance(1); check(f.refreshes==5 && f.results==5,"30-second retry or result missing");
        f.scheduler.configure(config(false,300_000,false,"08:00","23:00"));
        int old=f.refreshes; f.advance(600_000); check(f.refreshes==old && f.pending()==0,"disable left a live task");
        f.jump("2026-01-01T21:00:00Z"); f.succeed=true;
        f.scheduler.configure(config(true,300_000,true,"23:00:00","02:00:00"));
        f.advance(2_000); check(f.refreshes==old,"outside overnight window refreshed");
        f.jump("2026-01-01T23:00:00Z"); f.advance(0); check(f.refreshes==old+1,"clock jump into window missed refresh");
        f.jump("2026-01-02T03:00:00Z"); f.advance(0); check(f.refreshes==old+1,"outside end of overnight window refreshed");
        f.scheduler.configure(config(true,300_000,false,"23:00","02:00")); f.advance(0);
        check(f.refreshes==old+2,"window change not applied immediately");
        f.jump("2026-01-02T02:00:00Z"); f.advance(0);
        check(f.refreshes==old+2,"backward clock jump ignored the previous attempt time");
        f.scheduler.configure(config(true,0,false,"23:00","02:00")); f.advance(0);
        check(f.refreshes==old+2,"backward clock jump bypassed elapsed interval");
        f.jump("2026-01-02T03:00:01Z"); f.advance(0);
        check(f.refreshes==old+3,"forward clock jump failed to recover");
        f.advance(999); check(f.refreshes==old+3,"zero interval caused a busy loop");
        f.advance(1); check(f.refreshes==old+4,"zero interval lost its one-second pacing");
        f.scheduler.destroy(); f.advance(600_000); check(f.pending()==0,"destroy left a task");
        check(f.created==f.shutdowns,"timer not shut down on disable/destroy");
        Fixture rebuilt=new Fixture(); rebuilt.scheduler.configure(config(true,300_000,false,"",""));
        rebuilt.advance(1000); check(rebuilt.refreshes==1,"destroy/recreate first refresh missing");
        rebuilt.scheduler.destroy();
        Fixture initialJump=new Fixture();
        initialJump.scheduler.configure(config(true,300_000,false,"",""));
        initialJump.advance(500); initialJump.jump("2025-12-31T00:00:00Z");
        initialJump.advance(499); check(initialJump.refreshes==0,"clock jump shortened the first delay");
        initialJump.advance(1); check(initialJump.refreshes==1,"backward clock jump stranded the first refresh");
        initialJump.scheduler.destroy();
        Fixture edits=new Fixture();
        edits.scheduler.configure(config(true,300_000,true,"13:00","14:00"));
        edits.advance(1000); check(edits.refreshes==0,"closed window refreshed");
        edits.scheduler.configure(config(true,300_000,true,"11:00","13:00"));
        edits.advance(0); check(edits.refreshes==1,"start/end edits did not open the window immediately");
        edits.scheduler.destroy();
        Fixture zone=new Fixture(); zone.wall=Instant.parse("2026-01-01T00:00:00Z").toEpochMilli();
        zone.scheduler.configure(config(true,300_000,true,"08:00","09:00"));
        zone.advance(1000); check(zone.refreshes==0,"UTC window incorrectly open");
        zone.zone=TimeZone.getTimeZone("Asia/Shanghai"); zone.scheduler.clockChanged(); zone.advance(0);
        check(zone.refreshes==1,"time zone change did not recalculate the window");
        zone.scheduler.destroy();
        Fixture fall=new Fixture(); fall.zone=TimeZone.getTimeZone("America/New_York");
        fall.wall=Instant.parse("2026-11-01T04:59:00Z").toEpochMilli();
        fall.scheduler.configure(config(true,300_000,true,"01:30","02:00"));
        fall.advance(1_860_000); check(fall.refreshes==1,"fall-back missed the first occurrence of the window");
        fall.scheduler.destroy();
        Fixture repeated=new Fixture(); repeated.zone=TimeZone.getTimeZone("America/New_York");
        repeated.wall=Instant.parse("2026-11-01T05:40:00Z").toEpochMilli();
        repeated.scheduler.configure(config(true,300_000,true,"01:00","01:30"));
        repeated.advance(1_200_000); check(repeated.refreshes==1,"fall-back transition did not reopen the window");
        repeated.scheduler.destroy();
        Fixture spring=new Fixture(); spring.zone=TimeZone.getTimeZone("America/New_York");
        spring.wall=Instant.parse("2026-03-08T06:59:00Z").toEpochMilli();
        spring.scheduler.configure(config(true,300_000,true,"02:30","04:00"));
        spring.advance(60_000); check(spring.refreshes==1,"spring-forward missed the first valid time in the window");
        spring.scheduler.destroy();
        Fixture skipped=new Fixture(); skipped.zone=TimeZone.getTimeZone("America/New_York");
        skipped.wall=Instant.parse("2026-03-08T06:59:00Z").toEpochMilli();
        skipped.scheduler.configure(config(true,300_000,true,"02:30","03:00"));
        skipped.advance(60_000); check(skipped.refreshes==0,"nonexistent window should not open in the gap");
        skipped.advance(84_600_000); check(skipped.refreshes==1,"skipped window did not recover next day");
        skipped.scheduler.destroy();
        Fixture epoch=new Fixture(); epoch.wall=-1000;
        epoch.scheduler.configure(config(true,300_000,false,"",""));
        epoch.advance(1000); check(epoch.refreshes==1,"epoch-zero first attempt did not run exactly once");
        epoch.advance(299_999); check(epoch.refreshes==1,"epoch-zero attempt lost its interval");
        epoch.advance(1); check(epoch.refreshes==2,"epoch-zero attempt did not schedule its next interval");
        epoch.scheduler.destroy();
        System.out.println("PASS: controlled clock, intervals, edits, failure retry, overnight window, clock jumps, disable and recreation");
    }
}
