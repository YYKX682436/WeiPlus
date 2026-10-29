package h.Hchat.hooks.api.runtime;

import android.content.ContentValues;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.hooks.api.message.WeChatMessageChangeApi;
import h.Hchat.hooks.api.message.WeChatLvBufferMsgSource;
import h.Hchat.hooks.api.model.WeChatMessage;
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
        WeChatMessageChangeApi.Subscription firstListener = messageApi.subscribe(changes::add);

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
        assertFalse("business listener credentials expire with the hook generation", firstListener.isActive());
        assertEquals(0, databaseApi.hookedWrapperMutationMethodCount());

        messageApi.install();
        assertTrue(databaseApi.isOperational());
        assertTrue(messageApi.isInstalled());
        runNested(databaseApi, insert, dbInsert,
                new Object[]{"message", null, values}, 72L, null);
        assertEquals("reinstalling hooks does not revive a revoked business callback", 3, changes.size());
        WeChatMessageChangeApi.Subscription replacementListener = messageApi.subscribe(changes::add);
        assertTrue("business code can explicitly subscribe in the replacement generation",
                replacementListener.isActive());
        runNested(databaseApi, insert, dbInsert,
                new Object[]{"message", null, values}, 72L, null);
        assertEquals("the replacement business callback receives one event", 4, changes.size());

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

    @Test
    public void messageEventRecoversMsgSourceFromLvBufferWithoutChangingHostValues() throws Throwable {
        byte[] originalLvBuffer = "binary-prefix<msgsource><atuserlist><![CDATA[user-a,user-b]]></atuserlist></msgsource>suffix"
                .getBytes(StandardCharsets.UTF_8);
        ContentValues values = messageValues();
        values.put("msgSource", "");
        values.put("reserved", "");
        values.put("lvbuffer", originalLvBuffer);
        Map<String, Object> before = snapshot(values);
        List<WeChatMessageChangeApi.MessageChange> changes = observeInsert(values, 91L, null);

        assertEquals(1, changes.size());
        assertEquals("<msgsource><atuserlist><![CDATA[user-a,user-b]]></atuserlist></msgsource>",
                changes.get(0).message.msgSource);
        assertEquals("", values.getAsString("msgSource"));
        assertArrayEquals(originalLvBuffer, values.getAsByteArray("lvbuffer"));
        assertEquals("", values.getAsString("reserved"));
        assertContentValuesUnchanged(before, values);
        assertEquals(Arrays.asList("user-a", "user-b"), changes.get(0).message.getAtUserList());
    }

    @Test
    public void structuredLvBufferPreservesAtAllSemanticsAndExistingMsgSourceWins() throws Throwable {
        String lvSource = "<msgsource><atuserlist><![CDATA[notify@all]]></atuserlist></msgsource>";
        ContentValues values = messageValues();
        values.put("msgSource", "<msgsource><atuserlist>user-original</atuserlist></msgsource>");
        values.put("lvbuffer", structuredLvBuffer(lvSource));
        Map<String, Object> before = snapshot(values);
        List<WeChatMessageChangeApi.MessageChange> changes = observeInsert(values, 92L, null);
        assertEquals(1, changes.size());
        assertEquals("<msgsource><atuserlist>user-original</atuserlist></msgsource>",
                changes.get(0).message.msgSource);
        assertContentValuesUnchanged(before, values);

        values.put("msgSource", "");
        before = snapshot(values);
        changes = observeInsert(values, 93L, null);
        assertEquals(1, changes.size());
        assertEquals(lvSource, changes.get(0).message.msgSource);
        assertEquals(WeChatMessage.AtMentionType.AT_ALL,
                WeChatMessage.classifyAtMention(changes.get(0).message.msgSource, "hello", "self"));
        assertArrayEquals(structuredLvBuffer(lvSource), values.getAsByteArray("lvbuffer"));
        assertContentValuesUnchanged(before, values);
    }

    @Test
    public void malformedAndOversizedLvBuffersFailClosed() throws Throwable {
        ContentValues values = messageValues();
        values.put("lvbuffer", new byte[]{'{', 0, 9, 1, 2});
        List<WeChatMessageChangeApi.MessageChange> changes = observeInsert(values, 94L, null);
        assertEquals("", changes.get(0).message.msgSource);

        values.put("lvbuffer", new byte[]{'<', 'm', 's', 'g', 's', 'o', 'u', 'r', 'c', 'e', '>', (byte) 0xff,
                '<', '/', 'm', 's', 'g', 's', 'o', 'u', 'r', 'c', 'e', '>'});
        changes = observeInsert(values, 96L, null);
        assertEquals("invalid UTF-8 is rejected", "", changes.get(0).message.msgSource);

        values.put("lvbuffer", "<msgsource><atuserlist>truncated".getBytes(StandardCharsets.UTF_8));
        changes = observeInsert(values, 97L, null);
        assertEquals("truncated XML is rejected", "", changes.get(0).message.msgSource);

        values.put("lvbuffer", new byte[1024 * 1024]);
        changes = observeInsert(values, 95L, null);
        assertEquals("", changes.get(0).message.msgSource);
    }

    @Test
    public void detailsReadStructuredXmlAndLegacyNonXmlWhileDatabaseRequiresXml() throws Throwable {
        byte[] xml = structuredLvBuffer("<msgsource><atuserlist>notify@all</atuserlist></msgsource>");
        byte[] legacy = structuredLvBuffer(".msgsource.atuserlist=notify@all");
        assertEquals("<msgsource><atuserlist>notify@all</atuserlist></msgsource>",
                WeChatLvBufferMsgSource.extractRawSource(xml));
        String detailsSource = WeChatLvBufferMsgSource.extractRawSource(legacy);
        assertEquals(".msgsource.atuserlist=notify@all", detailsSource);
        assertEquals("notify@all", WeChatMessage.msgSourceValue(detailsSource, ".msgsource.atuserlist"));
        assertEquals("", WeChatLvBufferMsgSource.extractXml(legacy));

        ContentValues values = messageValues();
        values.put("msgSource", "");
        values.put("lvbuffer", legacy);
        byte[] before = legacy.clone();
        List<WeChatMessageChangeApi.MessageChange> changes = observeInsert(values, 98L, null);
        assertEquals(1, changes.size());
        assertEquals("database event rejects non-XML structured source", "", changes.get(0).message.msgSource);
        assertArrayEquals(before, values.getAsByteArray("lvbuffer"));
        assertEquals("", values.getAsString("msgSource"));
    }

    @Test
    public void structuredFieldIsSelectedInsteadOfXmlElsewhereAndInvalidLengthsFailClosed() throws Throwable {
        byte[] decoy = "<msgsource>decoy</msgsource>".getBytes(StandardCharsets.UTF_8);
        byte[] source = "<msgsource>actual</msgsource>".getBytes(StandardCharsets.UTF_8);
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(1 + 2 + decoy.length + 4 + 2 + source.length + 1);
        buffer.put((byte) '{').putShort((short) decoy.length).put(decoy).putInt(0)
                .putShort((short) source.length).put(source).put((byte) '}');
        byte[] original = buffer.array().clone();
        assertEquals("<msgsource>actual</msgsource>", WeChatLvBufferMsgSource.extractXml(buffer.array()));
        assertEquals("<msgsource>actual</msgsource>", WeChatLvBufferMsgSource.extractRawSource(buffer.array()));
        assertArrayEquals(original, buffer.array());
        ContentValues values = messageValues();
        values.put("lvbuffer", buffer.array());
        assertEquals("<msgsource>actual</msgsource>", observeInsert(values, 99L, null).get(0).message.msgSource);

        byte[] invalid = buffer.array().clone();
        invalid[1] = 0x7f;
        invalid[2] = (byte) 0xff;
        assertEquals("", WeChatLvBufferMsgSource.extractRawSource(invalid));
        assertEquals("", WeChatLvBufferMsgSource.extractXml(invalid));
        byte[] truncated = Arrays.copyOf(buffer.array(), buffer.array().length - 2);
        truncated[truncated.length - 1] = '}';
        assertEquals("", WeChatLvBufferMsgSource.extractRawSource(truncated));
        byte[] nonText = structuredLvBuffer("x");
        nonText[nonText.length - 2] = (byte) 0xff;
        assertEquals("", WeChatLvBufferMsgSource.extractRawSource(nonText));
        assertEquals("", WeChatLvBufferMsgSource.extractRawSource(new byte[65537]));
    }

    private List<WeChatMessageChangeApi.MessageChange> observeInsert(
            ContentValues values, long result, Throwable failure) throws Throwable {
        ClassLoader loader = getClass().getClassLoader();
        DexFinder finder = new DexFinder(null, loader, null);
        finder.sqliteDbWrapperClass = WrapperFixture.class;
        WeChatDatabaseListenerApi databaseApi = new WeChatDatabaseListenerApi(finder, loader, ignored -> {});
        WeChatMessageChangeApi messageApi = new WeChatMessageChangeApi(databaseApi, null, ignored -> {});
        List<WeChatMessageChangeApi.MessageChange> changes = new ArrayList<>();
        messageApi.subscribe(changes::add);
        messageApi.install();
        Method insert = WrapperFixture.class.getDeclaredMethod(
                "write", String.class, String.class, ContentValues.class);
        Method dbInsert = com.tencent.wcdb.database.SQLiteDatabase.class.getDeclaredMethod(
                "insert", String.class, String.class, ContentValues.class);
        runNested(databaseApi, insert, dbInsert,
                new Object[]{"message", null, values}, result, failure);
        return changes;
    }

    private byte[] structuredLvBuffer(String source) {
        byte[] first = "x".getBytes(StandardCharsets.UTF_8);
        byte[] sourceBytes = source.getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[1 + 2 + first.length + 4 + 2 + sourceBytes.length + 1];
        int offset = 0;
        bytes[offset++] = '{';
        bytes[offset++] = 0;
        bytes[offset++] = (byte) first.length;
        bytes[offset++] = first[0];
        offset += 4;
        bytes[offset++] = (byte) (sourceBytes.length >>> 8);
        bytes[offset++] = (byte) sourceBytes.length;
        System.arraycopy(sourceBytes, 0, bytes, offset, sourceBytes.length);
        bytes[bytes.length - 1] = '}';
        return bytes;
    }

    private Map<String, Object> snapshot(ContentValues values) {
        Map<String, Object> snapshot = new java.util.HashMap<>();
        for (String key : values.keySet()) {
            Object value = values.get(key);
            snapshot.put(key, value instanceof byte[] ? ((byte[]) value).clone() : value);
        }
        return snapshot;
    }

    private void assertContentValuesUnchanged(Map<String, Object> before, ContentValues after) {
        assertEquals(before.keySet(), after.keySet());
        for (String key : before.keySet()) {
            Object expected = before.get(key);
            Object actual = after.get(key);
            if (expected instanceof byte[]) assertArrayEquals((byte[]) expected, (byte[]) actual);
            else assertEquals("ContentValues field " + key, expected, actual);
        }
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
