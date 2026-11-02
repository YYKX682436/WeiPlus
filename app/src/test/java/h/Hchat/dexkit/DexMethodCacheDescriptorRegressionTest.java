package h.Hchat.dexkit;

import android.content.SharedPreferences;
import org.junit.Test;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class DexMethodCacheDescriptorRegressionTest {
    private static final String RUNTIME =
            "com.tencent.mm|8.0.80|3220|client|tinker|patch|1700000000|dex-crc|patch-files|stable";

    public static final class Owner {
        public Owner(String value, int flag) {}
        public String target(String value) { return value; }
        public int overloaded(int value) { return value; }
    }

    @Test public void validatesCachedMethodAndConstructorOwnerSignature() throws Exception {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences prefs = preferences(values);
        Method target = Owner.class.getDeclaredMethod("target", String.class);
        DexMethodCache.INSTANCE.save(prefs, RUNTIME, "target", target);
        assertEquals(target, DexMethodCache.INSTANCE.load(prefs, RUNTIME, Owner.class.getClassLoader(), "target"));

        values.put("target", "L" + Owner.class.getName().replace('.', '/')
                + ";->target(I)I");
        assertNull(DexMethodCache.INSTANCE.load(prefs, RUNTIME, Owner.class.getClassLoader(), "target"));

        Constructor<?> constructor = Owner.class.getDeclaredConstructor(String.class, int.class);
        DexMethodCache.INSTANCE.saveConstructor(prefs, RUNTIME, "owner_ctor", constructor);
        assertEquals(constructor, DexMethodCache.INSTANCE.loadConstructor(
                prefs, RUNTIME, Owner.class.getClassLoader(), "owner_ctor"));
        values.put("owner_ctor", "L" + Owner.class.getName().replace('.', '/')
                + ";-><init>(Ljava/lang/String;)V");
        assertNull(DexMethodCache.INSTANCE.loadConstructor(
                prefs, RUNTIME, Owner.class.getClassLoader(), "owner_ctor"));
    }

    @Test public void upgradeResponseRejectsPartiallyInvalidDescriptorList() throws Exception {
        Map<String, Object> values = new HashMap<>();
        SharedPreferences prefs = preferences(values);
        Method target = Owner.class.getDeclaredMethod("target", String.class);
        DexMethodCache.INSTANCE.saveList(prefs, RUNTIME, "upgrade_response", java.util.List.of(target));
        assertEquals(1, DexMethodCache.INSTANCE.loadUpgradeResponse(prefs, RUNTIME, Owner.class.getClassLoader()).getMethods().size());
        values.put("upgrade_response", values.get("upgrade_response") + "\nLbad/Owner;->missing()V");
        var lookup = DexMethodCache.INSTANCE.loadUpgradeResponse(prefs, RUNTIME, Owner.class.getClassLoader());
        assertTrue(lookup.getInvalid());
        assertTrue(lookup.getMethods().isEmpty());
        values.put("upgrade_response", "");
        assertTrue(DexMethodCache.INSTANCE.loadUpgradeResponse(prefs, RUNTIME, Owner.class.getClassLoader()).getInvalid());
        values.put("upgrade_response", Integer.valueOf(7));
        assertTrue(DexMethodCache.INSTANCE.loadUpgradeResponse(prefs, RUNTIME, Owner.class.getClassLoader()).getInvalid());
        values.remove("upgrade_response");
        assertFalse(DexMethodCache.INSTANCE.loadUpgradeResponse(prefs, RUNTIME, Owner.class.getClassLoader()).getInvalid());
        assertFalse(DexMethodCache.INSTANCE.loadUpgradeResponse(prefs, RUNTIME.replace("dex-crc", "other"), Owner.class.getClassLoader()).getInvalid());
    }

    private static SharedPreferences preferences(Map<String, Object> values) {
        return (SharedPreferences) Proxy.newProxyInstance(
                SharedPreferences.class.getClassLoader(), new Class<?>[]{SharedPreferences.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getString")) {
                        Object value = values.get(args[0]);
                        if (value != null && !(value instanceof String)) throw new ClassCastException("not a string");
                        return value instanceof String ? value : args[1];
                    }
                    if (method.getName().equals("contains")) return values.containsKey(args[0]);
                    if (method.getName().equals("edit")) return editor(values);
                    return defaultValue(method.getReturnType());
                });
    }

    private static SharedPreferences.Editor editor(Map<String, Object> values) {
        return (SharedPreferences.Editor) Proxy.newProxyInstance(
                SharedPreferences.Editor.class.getClassLoader(),
                new Class<?>[]{SharedPreferences.Editor.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "clear": values.clear(); return proxy;
                        case "putString": values.put((String) args[0], args[1]); return proxy;
                        case "remove": values.remove(args[0]); return proxy;
                        case "apply": return null;
                        case "commit": return true;
                        default: return defaultValue(method.getReturnType());
                    }
                });
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        return 0;
    }
}
