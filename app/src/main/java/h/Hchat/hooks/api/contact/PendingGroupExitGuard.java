package h.Hchat.hooks.api.contact;

import android.content.Context;
import android.content.SharedPreferences;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Small account-scoped guard that prevents re-submitting an unresolved native leave request. */
final class PendingGroupExitGuard {
    private static final String PREFS = "hchat_batch_group_exit_guard";
    private static final String PREFIX = "pending_";
    private final SharedPreferences preferences;

    PendingGroupExitGuard(Context context) {
        Context applicationContext = context != null && context.getApplicationContext() != null
                ? context.getApplicationContext() : context;
        this.preferences = applicationContext != null
                ? applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE) : null;
    }

    boolean contains(String accountIdentity, String roomId) {
        String key = key(accountIdentity, roomId);
        return key != null && preferences != null && preferences.getBoolean(key, false);
    }

    void mark(String accountIdentity, String roomId) {
        String key = key(accountIdentity, roomId);
        if (key == null || preferences == null) return;
        preferences.edit().putBoolean(key, true).apply();
    }

    void clear(String accountIdentity, String roomId) {
        String key = key(accountIdentity, roomId);
        if (key == null || preferences == null) return;
        preferences.edit().remove(key).apply();
    }

    private String key(String accountIdentity, String roomId) {
        String account = accountIdentity == null ? "" : accountIdentity.trim();
        String room = roomId == null ? "" : roomId.trim();
        if (account.isEmpty() || room.isEmpty()) return null;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    (account + '\u0000' + room).getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(PREFIX);
            for (byte value : digest) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
            return result.toString();
        } catch (Throwable ignored) {
            return null;
        }
    }
}
