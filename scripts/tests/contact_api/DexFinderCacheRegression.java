package h.Hchat.dexkit;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public final class DexFinderCacheRegression {
    public static void main(String[] args) throws Exception {
        locatorFailureCanRetryAndRecover();
        cacheReloadRequiresCompleteSnapshots();
        contactAndGlobalCacheWritesAreIsolatedUnderConcurrency();
        System.out.println("DexFinder cache regression tests passed");
    }

    private static void locatorFailureCanRetryAndRecover() {
        RetryableLocatorState state = new RetryableLocatorState();
        check(state.beginAttempt(), "first locator attempt starts");
        state.finishAttempt(false);
        check(!state.isResolved(), "failed locator is not cached as resolved");
        check(state.beginAttempt(), "failed locator can retry");
        state.finishAttempt(true);
        check(state.isResolved(), "successful retry resolves");
        check(!state.beginAttempt(), "resolved locator does not repeat work");
    }

    private static void cacheReloadRequiresCompleteSnapshots() {
        MemoryPreferences global = new MemoryPreferences();
        MemoryPreferences contact = new MemoryPreferences();
        DexFinderCacheStore store = new DexFinderCacheStore(global, contact);

        check(store.beginGlobalSnapshot(false, true, "runtime", "3") == null,
                "partial global snapshot is refused");
        check(store.beginGlobalSnapshot(true, false, "runtime", "3") == null,
                "invalid global snapshot is refused");
        DexFinderCacheStore.Editor globalWrite = store.beginGlobalSnapshot(true, true, "runtime", "3");
        globalWrite.putString("packetBaseClasses", "sample.SceneBase").apply();

        DexFinderCacheStore.Editor contactWrite = store.beginContactSnapshot(true, "runtime", "1");
        contactWrite.putString("protobufStaticDispatchMethod", "sample.Dispatch#send()").apply();

        DexFinderCacheStore reloaded = new DexFinderCacheStore(global, contact);
        check(reloaded.canLoadGlobal(true,
                        global.getString(DexFinderCacheStore.GLOBAL_VERSION_KEY, ""), "3",
                        global.getBoolean(DexFinderCacheStore.GLOBAL_COMPLETE_KEY, false)),
                "complete global cache reloads");
        check(reloaded.canLoadContact(true,
                        contact.getString(DexFinderCacheStore.CONTACT_VERSION_KEY, ""), "1",
                        contact.getBoolean(DexFinderCacheStore.CONTACT_COMPLETE_KEY, false)),
                "complete contact cache reloads");
        check("sample.SceneBase".equals(global.getString("packetBaseClasses", "")),
                "global cache data survives reload");
        check("sample.Dispatch#send()".equals(contact.getString("protobufStaticDispatchMethod", "")),
                "contact cache data survives reload");
        check(!reloaded.canLoadGlobal(true, "2", "3", true), "old global version is rejected");
        check(!reloaded.canLoadContact(true, "", "1", false), "incomplete contact cache is rejected");
    }

    private static void contactAndGlobalCacheWritesAreIsolatedUnderConcurrency() throws Exception {
        MemoryPreferences global = new MemoryPreferences();
        MemoryPreferences contact = new MemoryPreferences();
        DexFinderCacheStore globalWriter = new DexFinderCacheStore(global, contact);
        DexFinderCacheStore contactWriter = new DexFinderCacheStore(global, contact);
        CountDownLatch start = new CountDownLatch(1);
        Thread fullResolve = new Thread(() -> {
            await(start);
            DexFinderCacheStore.Editor editor = globalWriter.beginGlobalSnapshot(true, true, "runtime", "3");
            editor.putString("full.global.marker", "complete").apply();
        });
        Thread contactResolve = new Thread(() -> {
            await(start);
            DexFinderCacheStore.Editor editor = contactWriter.beginContactSnapshot(true, "runtime", "1");
            editor.putString("complete.contact.marker", "resolved").apply();
        });
        fullResolve.start();
        contactResolve.start();
        start.countDown();
        fullResolve.join();
        contactResolve.join();

        check("complete".equals(global.getString("full.global.marker", "")),
                "global snapshot stays complete during contact resolution");
        check("resolved".equals(contact.getString("complete.contact.marker", "")),
                "contact snapshot persists independently");
        check(global.getString("complete.contact.marker", null) == null,
                "contact fields never enter global namespace");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static final class MemoryPreferences implements DexFinderCacheStore.Preferences {
        private final Map<String, Object> values = Collections.synchronizedMap(new HashMap<>());

        @Override
        public String getString(String key, String fallback) {
            Object value = values.get(key);
            return value instanceof String ? (String) value : fallback;
        }

        @Override
        public boolean getBoolean(String key, boolean fallback) {
            Object value = values.get(key);
            return value instanceof Boolean ? (Boolean) value : fallback;
        }

        @Override
        public DexFinderCacheStore.Editor edit() {
            return new MemoryEditor();
        }

        private final class MemoryEditor implements DexFinderCacheStore.Editor {
            private final Map<String, Object> pending = new HashMap<>();
            private boolean clear;

            @Override
            public DexFinderCacheStore.Editor clear() {
                clear = true;
                pending.clear();
                return this;
            }

            @Override
            public DexFinderCacheStore.Editor putString(String key, String value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public DexFinderCacheStore.Editor putBoolean(String key, boolean value) {
                pending.put(key, value);
                return this;
            }

            @Override
            public void apply() {
                synchronized (values) {
                    if (clear) values.clear();
                    for (Map.Entry<String, Object> item : pending.entrySet()) {
                        values.put(item.getKey(), item.getValue());
                    }
                }
            }
        }
    }
}
