package h.Hchat.hooks.items.sportstep;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Stable, unambiguous host/account namespace; empty identities must fail closed. */
public final class StepScopeKey {
    private StepScopeKey() {}

    public static String of(String host, String account) {
        if (host == null || host.trim().isEmpty() || account == null || account.trim().isEmpty()) return null;
        byte[] h = host.getBytes(StandardCharsets.UTF_8);
        byte[] a = account.getBytes(StandardCharsets.UTF_8);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(ByteBuffer.allocate(4).putInt(h.length).array());
            digest.update(h);
            digest.update(ByteBuffer.allocate(4).putInt(a.length).array());
            digest.update(a);
            byte[] bytes = digest.digest();
            char[] chars = new char[bytes.length * 2];
            char[] hex = "0123456789abcdef".toCharArray();
            for (int i = 0; i < bytes.length; i++) {
                chars[i * 2] = hex[(bytes[i] & 255) >>> 4];
                chars[i * 2 + 1] = hex[bytes[i] & 15];
            }
            return new String(chars);
        } catch (NoSuchAlgorithmException e) {
            return null;
        }
    }
}
