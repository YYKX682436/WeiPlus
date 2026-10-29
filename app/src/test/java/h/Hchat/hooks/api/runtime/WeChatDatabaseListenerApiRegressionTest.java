package h.Hchat.hooks.api.runtime;

import android.content.ContentValues;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.message.WeChatMessageChangeApi;
import h.Hchat.hooks.api.model.DatabaseChange;
import h.Hchat.hooks.core.HookRegistry;
import h.Hchat.hooks.core.XposedRuntime;
import h.Hchat.tests.api102.ChainFixture;

import io.github.libxposed.api.XposedInterface;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35)
public class WeChatDatabaseListenerApiRegressionTest {
    private final HookRegistry registry = HookRegistry.get();

    @Before
    public void setUp() {
        initializeRuntimeIfNeeded();
        registry.unhookAll();
    }

    @After
    public void tearDown() {
        registry.unhookAll();
    }

    @Test
    public void productionApiHooksNestedWrapperAndWcdbWritesOnceAndRecoversAfterRevocation()
            throws Throwable {
        ClassLoader loader = getClass().getClassLoader();
        DexFinder finder = new DexFinder(null, loader, null);
        finder.sqliteDbWrapperClass = WrapperFixture.class;
        List<String> logs = new ArrayList<>();
        WeChatDatabaseListenerApi databaseApi =
                new WeChatDatabaseListenerApi(finder, loader, logs::add);
        WeChatMessageChangeApi messageApi =
                new WeChatMessageChangeApi(databaseApi, null, logs::add);
        List<WeChatMessageChangeApi.MessageChange> changes = new ArrayList<>();
        messageApi.subscribe(changes::add);

        messageApi.install();
        assertTrue("the validated wrapper's insert, update, and delete hooks are active",
                databaseApi.isOperational());
        assertTrue(messageApi.isInstalled());
        assertTrue(databaseApi.hookedWrapperInsertMethodCount() > 0);
        assertTrue(databaseApi.hookedWrapperUpdateMethodCount() > 0);
        assertTrue(databaseApi.hookedWrapperDeleteMethodCount() > 0);

        int registrations = registry.size();
        messageApi.install();
        assertEquals("repeat installation reuses active registrations", registrations, registry.size());

        ContentValues values = messageValues();
        String[] updateArgs = {"message", "77"};
        Method insert = WrapperFixture.class.getDeclaredMethod(
                "write", String.class, String.class, ContentValues.class);
        Method dbInsert = com.tencent.wcdb.database.SQLiteDatabase.class.getDeclaredMethod(
                "insert", String.class, String.class, ContentValues.class);
        assertEquals(71L, runNested(databaseApi, insert, dbInsert,
                new Object[]{"message", null, values}, 71L, null));
        assertEquals(1, changes.size());
        assertEquals(DatabaseChange.INSERT, changes.get(0).operation());

        Method update = WrapperFixture.class.getDeclaredMethod(
                "change", String.class, ContentValues.class, String.class, String[].class);
        Method dbUpdate = com.tencent.wcdb.database.SQLiteDatabase.class.getDeclaredMethod(
                "update", String.class, ContentValues.class, String.class, String[].class);
        assertEquals(2, runNested(databaseApi, update, dbUpdate,
                new Object[]{"message", values, "msgId = ?", updateArgs}, 2, null));
        assertEquals(2, changes.size());
        assertEquals(DatabaseChange.UPDATE, changes.get(1).operation());

        Method delete = WrapperFixture.class.getDeclaredMethod(
                "remove", String.class, String.class, String[].class);
        Method dbDelete = com.tencent.wcdb.database.SQLiteDatabase.class.getDeclaredMethod(
                "delete", String.class, String.class, String[].class);
        assertEquals(1, runNested(databaseApi, delete, dbDelete,
                new Object[]{"message", "msgId = ?", updateArgs}, 1, null));
        assertEquals(3, changes.size());
        assertEquals(DatabaseChange.DELETE, changes.get(2).operation());
        assertEquals("a wrapper call and its nested WCDB call publish one event each",
                3, changes.size());
        assertEquals("the observer leaves message content untouched", "", values.getAsString("msgSource"));
        assertArrayEquals(updateArgs, new String[]{"message", "77"});

        runNested(databaseApi, insert, dbInsert,
                new Object[]{"message", null, values}, -1L, null);
        runNested(databaseApi, update, dbUpdate,
                new Object[]{"message", values, "msgId = ?", updateArgs}, 0, null);
        runNested(databaseApi, delete, dbDelete,
                new Object[]{"message", "msgId = ?", updateArgs}, 0, null);
        assertEquals("failed or zero-row writes do not publish", 3, changes.size());

        IllegalStateException hostFailure = new IllegalStateException("host write failed");
        try {
            runNested(databaseApi, insert, dbInsert,
                    new Object[]{"message", null, values}, null, hostFailure);
            fail("host write exception must propagate");
        } catch (IllegalStateException actual) {
            assertSame(hostFailure, actual);
        }
        assertEquals("a failed host write never publishes", 3, changes.size());

        long oldGeneration = registry.installationGeneration();
        registry.unhookAll();
        assertTrue(registry.installationGeneration() > oldGeneration);
        assertFalse("revoked wrapper callbacks are no longer operational", databaseApi.isOperational());
        assertFalse("message API does not report a revoked hook generation as installed", messageApi.isInstalled());
        assertEquals(0, databaseApi.hookedWrapperMutationMethodCount());

        messageApi.install();
        assertTrue(databaseApi.isOperational());
        assertTrue(messageApi.isInstalled());
        runNested(databaseApi, insert, dbInsert,
                new Object[]{"message", null, values}, 72L, null);
        assertEquals("reinstalling the API does not duplicate its stable listener", 4, changes.size());

        Method replacementInsert = ReplacementWrapperFixture.class.getDeclaredMethod(
                "putRow", String.class, String.class, ContentValues.class);
        finder.sqliteDbWrapperClass = ReplacementWrapperFixture.class;
        databaseApi.install();
        messageApi.install();
        assertTrue("a newly located wrapper replaces old mutation hooks", databaseApi.isOperational());
        assertTrue(messageApi.isInstalled());
        assertFalse("old wrapper methods are no longer registered",
                registeredMethods(databaseApi).containsKey(insert));
        assertTrue("new wrapper methods are registered", registeredMethods(databaseApi).containsKey(replacementInsert));
        Method replacementDbInsert = com.tencent.wcdb.database.SQLiteDatabase.class.getDeclaredMethod(
                "insert", String.class, String.class, ContentValues.class);
        runNested(databaseApi, replacementInsert, replacementDbInsert,
                new Object[]{"message", null, values}, 73L, null);
        assertEquals("relocated wrappers continue to deliver one successful mutation", 5, changes.size());
    }

    @Test
    public void genericSqliteHooksCannotMakeTheMessageApiOperationalOnTheirOwn() {
        ClassLoader loader = getClass().getClassLoader();
        DexFinder finder = new DexFinder(null, loader, null);
        finder.sqliteDbWrapperClass = null;
        WeChatDatabaseListenerApi databaseApi =
                new WeChatDatabaseListenerApi(finder, loader, ignored -> {});
        int before = registry.size();
        databaseApi.install();
        assertFalse(databaseApi.isOperational());
        assertTrue("generic WCDB mutation hooks may be installed for fallback observation",
                databaseApi.hookedMethodCount() > 0);
        assertTrue(registry.size() > before);
    }

    private Object runNested(WeChatDatabaseListenerApi databaseApi,
                             Method wrapperMethod,
                             Method databaseMethod,
                             Object[] args,
                             Object result,
                             Throwable failure) throws Throwable {
        Object wrapper = wrapperMethod.getDeclaringClass().getDeclaredConstructor().newInstance();
        Object database = new com.tencent.wcdb.database.SQLiteDatabase();
        ChainFixture wrapperChain = new ChainFixture(wrapperMethod, wrapper, args,
                (receiver, forwardedArgs) -> {
                    ChainFixture databaseChain = new ChainFixture(databaseMethod, database, forwardedArgs,
                            (dbReceiver, dbArgs) -> {
                                if (failure != null) throw failure;
                                return result;
                            });
                    return databaseChain.run(hookerFor(databaseApi, databaseMethod));
                });
        return wrapperChain.run(hookerFor(databaseApi, wrapperMethod));
    }

    @SuppressWarnings("unchecked")
    private XposedInterface.Hooker hookerFor(WeChatDatabaseListenerApi api, Method method) throws Exception {
        Map<Method, ?> registrations = registeredMethods(api);
        Object registration = registrations.get(method);
        assertNotNull("production API registered " + method, registration);
        Field handleField = registration.getClass().getDeclaredField("handle");
        handleField.setAccessible(true);
        Object handle = handleField.get(registration);
        Field callbackField = handle.getClass().getDeclaredField("callback");
        callbackField.setAccessible(true);
        return (XposedInterface.Hooker) callbackField.get(handle);
    }

    @SuppressWarnings("unchecked")
    private Map<Method, ?> registeredMethods(WeChatDatabaseListenerApi api) throws Exception {
        Field registrationsField = WeChatDatabaseListenerApi.class.getDeclaredField("hookRegistrations");
        registrationsField.setAccessible(true);
        return (Map<Method, ?>) registrationsField.get(api);
    }

    private ContentValues messageValues() {
        ContentValues values = new ContentValues();
        values.put("isSend", 0);
        values.put("type", 1);
        values.put("talker", "peer");
        values.put("content", "original body");
        values.put("createTime", 100L);
        values.put("msgSvrId", 200L);
        values.put("msgSource", "");
        values.put("lvbuffer", "<msgsource>source data</msgsource>".getBytes(StandardCharsets.UTF_8));
        return values;
    }

    private void initializeRuntimeIfNeeded() {
        try {
            XposedRuntime.get();
        } catch (IllegalStateException missing) {
            XposedRuntime.initialize(new h.Hchat.tests.api102.FrameworkFixture().create());
        }
    }

    public static final class WrapperFixture {
        public long write(String table, String nullColumnHack, ContentValues values) { return 0L; }
        public int change(String table, ContentValues values, String where, String[] args) { return 0; }
        public int remove(String table, String where, String[] args) { return 0; }
    }

    public static final class ReplacementWrapperFixture {
        public long putRow(String table, String nullColumnHack, ContentValues values) { return 0L; }
        public int changeRows(String table, ContentValues values, String where, String[] args) { return 0; }
        public int removeRows(String table, String where, String[] args) { return 0; }
    }
}
