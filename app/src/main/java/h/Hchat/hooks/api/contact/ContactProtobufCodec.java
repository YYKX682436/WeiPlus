package h.Hchat.hooks.api.contact;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

final class ContactProtobufCodec {
    private ContactProtobufCodec() {}

    static byte[] encode(JSONObject message) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        JSONArray fields = message.names();
        if (fields == null) return new byte[0];
        for (int index = 0; index < fields.length(); index++) {
            int field = Integer.parseInt(fields.getString(index));
            writeValue(output, field, message.get(fields.getString(index)));
        }
        return output.toByteArray();
    }

    private static void writeValue(ByteArrayOutputStream output, int field, Object value) throws Exception {
        if (value instanceof JSONArray) {
            JSONArray values = (JSONArray) value;
            for (int index = 0; index < values.length(); index++) {
                writeValue(output, field, values.get(index));
            }
        } else if (value instanceof JSONObject) {
            writeLength(output, field, encode((JSONObject) value));
        } else if (value instanceof Number) {
            writeVarint(output, field << 3);
            writeVarint(output, ((Number) value).longValue());
        } else if (value instanceof Boolean) {
            writeVarint(output, field << 3);
            writeVarint(output, (Boolean) value ? 1L : 0L);
        } else if (value != null) {
            writeLength(output, field, value.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private static void writeLength(ByteArrayOutputStream output, int field, byte[] value) {
        writeVarint(output, (field << 3) | 2);
        writeVarint(output, value == null ? 0 : value.length);
        if (value != null) output.write(value, 0, value.length);
    }

    private static void writeVarint(ByteArrayOutputStream output, long value) {
        while ((value & ~0x7fL) != 0L) {
            output.write((int) ((value & 0x7fL) | 0x80L));
            value >>>= 7;
        }
        output.write((int) value);
    }
}
