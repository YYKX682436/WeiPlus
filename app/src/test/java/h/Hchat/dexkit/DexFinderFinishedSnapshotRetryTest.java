package h.Hchat.dexkit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.Proxy;
import org.junit.Test;
import h.Hchat.hooks.core.XposedRuntime;
import io.github.libxposed.api.XposedInterface;
import org.luckypray.dexkit.query.FindClass;
import org.luckypray.dexkit.query.FindMethod;
import org.luckypray.dexkit.result.ClassData;
import org.luckypray.dexkit.result.MethodData;

/** Drives real DexFinder locator methods after a completed global snapshot. */
public class DexFinderFinishedSnapshotRetryTest {
    @Test public void missingMuteDatabaseAndContactLocatorsRetryAfterGlobalScan() throws Exception {
        initializeLoggerIfNeeded();
        AtomicInteger methodQueries = new AtomicInteger();
        AtomicInteger classQueries = new AtomicInteger();
        DexFinderQuery query = new DexFinderQuery() {
            @Override public List<MethodData> findMethod(FindMethod ignored) {
                methodQueries.incrementAndGet();
                return Collections.emptyList();
            }
            @Override public List<ClassData> findClass(FindClass ignored) {
                classQueries.incrementAndGet();
                return Collections.emptyList();
            }
        };
        DexFinder finder = new DexFinder(query, getClass().getClassLoader(), null);
        setBoolean(finder, "resolutionFinished", true);
        setBoolean(finder, "cacheLoadAttempted", true);
        finder.resolveAll();
        assertEquals("completed global scan is not repeated", 0, methodQueries.get());
        assertEquals("completed global scan does not restart class scans", 0, classQueries.get());

        int beforeMute = methodQueries.get();
        finder.resolveConversationMuteApi();
        int afterFirstMute = methodQueries.get();
        finder.resolveConversationMuteApi();
        assertTrue("incomplete mute locator stays retryable after global completion",
                methodQueries.get() > afterFirstMute && afterFirstMute > beforeMute);
        int afterMuteRetries = methodQueries.get();

        int beforeDatabase = classQueries.get();
        finder.resolveDatabaseApi();
        assertTrue("incomplete database locator performs a targeted query", classQueries.get() > beforeDatabase);

        int beforeContact = methodQueries.get();
        finder.resolveContactCardApi();
        assertTrue("incomplete contact locator performs a targeted query", methodQueries.get() > beforeContact);
        int afterContact = methodQueries.get();

        setMethod(finder, "serviceGetterMethod", ServiceFixture.class.getDeclaredMethod("get", Class.class));
        int beforeWarmHit = methodQueries.get();
        finder.resolveServiceManagerApi();
        assertTrue("validated cached method is reused without DexKit", methodQueries.get() == beforeWarmHit);

        System.out.println("DexFinder retry query counts: mute-first=" + (afterFirstMute - beforeMute)
                + " mute-retry=" + (afterMuteRetries - afterFirstMute)
                + " classes=" + (classQueries.get() - beforeDatabase)
                + " contact=" + (afterContact - beforeContact) + " valid-warm-hit=0");
    }

    private static void setBoolean(DexFinder finder, String name, boolean value) throws Exception {
        Field field = DexFinder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.setBoolean(finder, value);
    }

    private static void setMethod(DexFinder finder, String name, java.lang.reflect.Method value)
            throws Exception {
        Field field = DexFinder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(finder, value);
    }

    public static final class ServiceFixture {
        public static Object get(Class<?> ignored) { return null; }
    }

    private static void initializeLoggerIfNeeded() {
        try {
            XposedRuntime.get();
            return;
        } catch (IllegalStateException ignored) {}
        XposedInterface framework = (XposedInterface) Proxy.newProxyInstance(
                XposedInterface.class.getClassLoader(), new Class<?>[]{XposedInterface.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (!type.isPrimitive() || type == void.class) return null;
                    if (type == boolean.class) return false;
                    if (type == char.class) return '\0';
                    return 0;
                });
        XposedRuntime.initialize(framework);
    }
}
