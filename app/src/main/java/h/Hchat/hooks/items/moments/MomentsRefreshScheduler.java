package h.Hchat.hooks.items.moments;

import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.time.zone.ZoneRules;

/** Schedules a single next check; all elapsed-time and timer dependencies are injectable. */
public final class MomentsRefreshScheduler {
    public interface Clock { long now(); long elapsed(); TimeZone timeZone(); }
    public interface Cancel { void cancel(); }
    public interface Timer { Cancel schedule(Runnable task, long delayMillis); void shutdown(); }
    public interface TimerFactory { Timer create(); }
    public interface Refresh {
        boolean refresh() throws Exception;
        void result(long time, boolean success, Throwable error);
    }
    public static final class Config {
        final boolean enabled, windowEnabled;
        final long intervalMillis;
        final String start, end;
        public Config(boolean enabled, long intervalMillis, boolean windowEnabled, String start, String end) {
            this.enabled=enabled; this.intervalMillis=Math.max(0, intervalMillis);
            this.windowEnabled=windowEnabled; this.start=start; this.end=end;
        }
    }

    private static final long FIRST_DELAY_MS=1_000L, RETRY_DELAY_MS=30_000L;
    // An already executing request may outlive a destroyed runtime. A replacement must not overlap it.
    private static final Object REFRESH_GATE = new Object();
    private static final Set<Runnable> GATE_WAITERS = new HashSet<>();
    private static boolean refreshInFlight;
    private final Clock clock;
    private final TimerFactory timers;
    private final Refresh refresh;
    private Config config;
    private Timer timer;
    private Cancel pending;
    private Runnable gateWaiter;
    private long sequence, generation, lastAttemptAt, firstNotBefore;
    private boolean running, destroyed, hasAttempt;

    public MomentsRefreshScheduler(Clock clock, TimerFactory timers, Refresh refresh) {
        this.clock=clock; this.timers=timers; this.refresh=refresh;
    }

    public synchronized void configure(Config next) {
        if (destroyed) return;
        boolean wasEnabled = config != null && config.enabled;
        config=next;
        if (!next.enabled) {
            if (wasEnabled) generation++;
            stopTimer();
            return;
        }
        if (!wasEnabled) {
            generation++;
            lastAttemptAt=0;
            hasAttempt=false;
            firstNotBefore=clock.elapsed()+FIRST_DELAY_MS;
            timer=timers.create();
        }
        rescheduleLocked();
    }

    public synchronized void clockChanged() {
        if (!destroyed && config != null && config.enabled) rescheduleLocked();
    }

    public synchronized void destroy() {
        if (destroyed) return;
        destroyed=true;
        generation++;
        stopTimer();
    }

    private void stopTimer() {
        sequence++;
        if (pending != null) pending.cancel();
        pending=null;
        removeGateWaiter();
        if (timer != null) timer.shutdown();
        timer=null;
    }

    private void rescheduleLocked() {
        sequence++;
        if (pending != null) pending.cancel();
        pending=null;
        removeGateWaiter();
        if (destroyed || config == null || !config.enabled || timer == null || running) return;
        long delay=nextDelay(clock.now(), clock.elapsed(), config, hasAttempt, lastAttemptAt, firstNotBefore, clock.timeZone());
        scheduleLocked(delay);
    }

    private void scheduleLocked(long delay) {
        long token=++sequence;
        pending=timer.schedule(() -> runTask(token), Math.max(0, delay));
    }

    private void runTask(long token) {
        long attempted, attemptGeneration, interval;
        synchronized (this) {
            if (token != sequence || destroyed || config == null || !config.enabled || timer == null) return;
            pending=null;
            long now=clock.now();
            long delay=nextDelay(now, clock.elapsed(), config, hasAttempt, lastAttemptAt, firstNotBefore, clock.timeZone());
            if (delay > 0) { scheduleLocked(delay); return; }
            Runnable waiter = () -> gateReleased(token);
            synchronized (REFRESH_GATE) {
                if (refreshInFlight) {
                    gateWaiter=waiter;
                    GATE_WAITERS.add(waiter);
                    return;
                }
                refreshInFlight=true;
            }
            running=true;
            attemptGeneration=generation;
            interval=config.intervalMillis;
            attempted=now;
            lastAttemptAt=now;
            hasAttempt=true;
        }
        boolean success=false;
        Throwable failure=null;
        try { success=refresh.refresh(); }
        catch (Throwable error) { failure=error; }
        try { refresh.result(attempted, success, failure); }
        catch (Throwable ignored) { /* result logging must not strand the scheduler */ }
        finally {
            releaseGate();
            synchronized (this) {
                running=false;
                if (!destroyed && config != null && config.enabled) {
                    if (generation == attemptGeneration && !success) {
                        lastAttemptAt=attempted-interval+Math.min(interval, RETRY_DELAY_MS);
                    }
                    // A zero interval used to run at most once per fixed-delay tick.
                    if (generation == attemptGeneration && interval == 0) firstNotBefore=clock.elapsed()+FIRST_DELAY_MS;
                    rescheduleLocked();
                }
            }
        }
    }

    private void removeGateWaiter() {
        if (gateWaiter == null) return;
        synchronized (REFRESH_GATE) { GATE_WAITERS.remove(gateWaiter); }
        gateWaiter=null;
    }

    private void gateReleased(long token) {
        synchronized (this) {
            if (token != sequence || destroyed || config == null || !config.enabled || timer == null) return;
            gateWaiter=null;
            scheduleLocked(0);
        }
    }

    private static void releaseGate() {
        Runnable[] waiters;
        synchronized (REFRESH_GATE) {
            refreshInFlight=false;
            waiters=GATE_WAITERS.toArray(new Runnable[0]);
            GATE_WAITERS.clear();
        }
        for (Runnable waiter : waiters) {
            try { waiter.run(); } catch (Throwable ignored) { /* one waiter cannot strand the others */ }
        }
    }

    static long nextDelay(long now, long elapsed, Config config, boolean hasAttempt, long lastAttemptAt, long firstNotBefore, TimeZone zone) {
        long intervalDelay = !hasAttempt
                ? Math.max(0, firstNotBefore-elapsed)
                : Math.max(0, saturatedAdd(lastAttemptAt, config.intervalMillis)-now);
        if (hasAttempt && config.intervalMillis == 0) {
            intervalDelay=Math.max(intervalDelay, Math.max(0, firstNotBefore-elapsed));
        }
        long windowDelay = windowDelay(now, config, zone);
        return Math.max(intervalDelay, windowDelay);
    }

    private static long saturatedAdd(long a, long b) {
        return a > Long.MAX_VALUE-b ? Long.MAX_VALUE : a+b;
    }

    private static long windowDelay(long now, Config config, TimeZone zone) {
        if (!config.windowEnabled) return 0;
        int start=parseTime(config.start), end=parseTime(config.end);
        if (start < 0 || end < 0 || start == end) return 0;
        Calendar current=Calendar.getInstance(zone);
        current.setTimeInMillis(now);
        int second=current.get(Calendar.HOUR_OF_DAY)*3600+current.get(Calendar.MINUTE)*60+current.get(Calendar.SECOND);
        boolean inside=start < end ? second >= start && second < end : second >= start || second < end;
        if (inside) return 0;
        // A local start can occur twice at a fall-back, or not at all during a spring gap.
        // A transition can itself open the window, even when the nominal start is skipped.
        try {
            ZoneId zoneId=zone.toZoneId();
            ZoneRules rules=zoneId.getRules();
            Instant nowInstant=Instant.ofEpochMilli(now);
            LocalDate date=nowInstant.atZone(zoneId).toLocalDate();
            long openingAt=Long.MAX_VALUE;
            for (int day=0; day<=2; day++) {
                LocalDateTime localStart=date.plusDays(day).atTime(start/3600,(start%3600)/60,start%60);
                List<ZoneOffset> offsets=rules.getValidOffsets(localStart);
                for (ZoneOffset offset : offsets) {
                    long candidate=localStart.toInstant(offset).toEpochMilli();
                    if (candidate > now && isInsideWindow(candidate, zoneId, start, end)) {
                        openingAt=Math.min(openingAt,candidate);
                    }
                }
                if (offsets.isEmpty()) {
                    ZoneOffsetTransition gap=rules.getTransition(localStart);
                    if (gap != null) {
                        long candidate=gap.getInstant().toEpochMilli();
                        if (candidate > now && isInsideWindow(candidate, zoneId, start, end)) {
                            openingAt=Math.min(openingAt,candidate);
                        }
                    }
                }
            }
            ZoneOffsetTransition transition=rules.nextTransition(nowInstant);
            if (transition != null) {
                long candidate=transition.getInstant().toEpochMilli();
                if (candidate > now && isInsideWindow(candidate, zoneId, start, end)) {
                    openingAt=Math.min(openingAt,candidate);
                }
            }
            if (openingAt != Long.MAX_VALUE) return openingAt-now;
        } catch (RuntimeException ignored) {
            // Keep the existing Calendar fallback for unusual or unavailable zone identifiers.
        }
        Calendar opening=(Calendar) current.clone();
        opening.set(Calendar.HOUR_OF_DAY,start/3600);
        opening.set(Calendar.MINUTE,(start%3600)/60);
        opening.set(Calendar.SECOND,start%60);
        opening.set(Calendar.MILLISECOND,0);
        if (opening.getTimeInMillis() <= now) opening.add(Calendar.DAY_OF_MONTH,1);
        return Math.max(0,opening.getTimeInMillis()-now);
    }

    private static boolean isInsideWindow(long time, ZoneId zone, int start, int end) {
        ZonedDateTime local=Instant.ofEpochMilli(time).atZone(zone);
        int second=local.getHour()*3600+local.getMinute()*60+local.getSecond();
        return start < end ? second >= start && second < end : second >= start || second < end;
    }

    private static int parseTime(String value) {
        if (value == null) return -1;
        String[] parts=value.trim().split(":",-1);
        if (parts.length < 2 || parts.length > 3) return -1;
        try {
            int h=Integer.parseInt(parts[0]), m=Integer.parseInt(parts[1]);
            int s=parts.length==3 ? Integer.parseInt(parts[2]) : 0;
            if (h<0 || h>23 || m<0 || m>59 || s<0 || s>59) return -1;
            return h*3600+m*60+s;
        } catch (NumberFormatException error) { return -1; }
    }
}
