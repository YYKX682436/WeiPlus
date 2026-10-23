package h.Hchat.hooks.api.contact;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import org.json.JSONObject;

import h.Hchat.dexkit.DexFinder;
import h.Hchat.utils.KavaReflector;

/** Sends only the two contact mutations that lack a reliable native Scene API. */
final class ContactProtobufSender {
    private static final String MODIFY_LABEL_URI = "/cgi-bin/micromsg-bin/modifycontactlabellist";
    private static final String SET_CONTACT_PROPERTY_URI = "/cgi-bin/micromsg-bin/setcontactproperty";
    private static final int MODIFY_LABEL_CGI = 638;
    private static final int SET_CONTACT_PROPERTY_CGI = 10022;

    private ContactProtobufSender() {}

    static boolean modifyContactLabelList(DexFinder dexFinder, String username, String labelIds) throws Exception {
        JSONObject item = new JSONObject();
        item.put("1", username);
        item.put("2", labelIds == null ? "" : labelIds);
        JSONObject request = new JSONObject();
        request.put("2", 1);
        request.put("3", item);
        return send(dexFinder, MODIFY_LABEL_URI, MODIFY_LABEL_CGI, request);
    }

    static boolean setContactRemark(DexFinder dexFinder, String username, String remark, String roomName)
            throws Exception {
        JSONObject request = new JSONObject();
        request.put("2", username);
        request.put("3", new JSONObject().put("1", remark == null ? "" : remark));
        if (roomName != null && !roomName.isEmpty()) request.put("4", roomName);
        return send(dexFinder, SET_CONTACT_PROPERTY_URI, SET_CONTACT_PROPERTY_CGI, request);
    }

    private static boolean send(DexFinder dexFinder, String uri, int cgiId, JSONObject payload) throws Exception {
        if (dexFinder == null || !dexFinder.resolveContactProtobufSenderApi()) return false;
        byte[] body = ContactProtobufCodec.encode(payload);
        Object request = KavaReflector.newInstance(
                KavaReflector.findConstructor(dexFinder.protobufRawReqClass, byte[].class), body);
        Object response = KavaReflector.newInstance(
                KavaReflector.findConstructor(dexFinder.protobufGenericRespClass));
        Object builder = KavaReflector.newInstance(
                KavaReflector.findConstructor(dexFinder.protobufConfigBuilderClass));
        if (request == null || response == null || builder == null) return false;

        writeField(builder, "a", request);
        writeField(builder, "b", response);
        writeField(builder, "c", uri);
        writeField(builder, "d", cgiId);
        writeField(builder, "e", 0);
        writeField(builder, "f", 0);
        writeField(builder, "l", 1);
        writeField(builder, "n", body);

        Object requestResponse = buildRequestResponse(dexFinder, builder);
        if (requestResponse == null) return false;
        Class<?> callbackType = dexFinder.protobufCallbackClass;
        Object callback = Proxy.newProxyInstance(
                callbackType.getClassLoader(),
                new Class<?>[]{callbackType},
                (proxy, method, args) -> {
                    if ("hashCode".equals(method.getName())) return System.identityHashCode(proxy);
                    if ("equals".equals(method.getName())) {
                        return proxy == (args != null && args.length > 0 ? args[0] : null);
                    }
                    if ("toString".equals(method.getName())) return "HchatContactProtobufCallback";
                    return defaultValue(method.getReturnType());
                });
        KavaReflector.invokeOrThrow(
                dexFinder.protobufStaticDispatchMethod, null, requestResponse, callback, false);
        return true;
    }

    private static Object buildRequestResponse(DexFinder dexFinder, Object builder) {
        try {
            Object result = KavaReflector.invoke(KavaReflector.findMethod(builder.getClass(), "a"), builder);
            if (result != null && dexFinder.protobufReqRespClass.isInstance(result)) return result;
        } catch (Throwable ignored) {
        }
        for (Method method : KavaReflector.declaredMethods(builder.getClass())) {
            if (method.getParameterTypes().length != 0 || method.getReturnType() == void.class) continue;
            if (!dexFinder.protobufReqRespClass.isAssignableFrom(method.getReturnType())) continue;
            Object result = KavaReflector.invoke(method, builder);
            if (result != null) return result;
        }
        return null;
    }

    private static void writeField(Object target, String name, Object value) throws Exception {
        Field field = KavaReflector.findFieldRecursive(target.getClass(), name);
        if (field == null || !KavaReflector.writeField(field, target, value)) {
            throw new IllegalStateException("联系人请求字段不可用: " + name);
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (!type.isPrimitive() || type == void.class) return null;
        if (type == boolean.class) return false;
        if (type == char.class) return '\0';
        if (type == long.class) return 0L;
        if (type == float.class) return 0f;
        if (type == double.class) return 0d;
        if (type == byte.class) return (byte) 0;
        if (type == short.class) return (short) 0;
        return 0;
    }
}
