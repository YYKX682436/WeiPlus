package h.Hchat.hooks.api.contact;

import java.util.ArrayList;
import java.util.List;

/** Minimal rcontact label-id list edits that preserve every unrelated ID. */
public final class ContactLabelIds {
    private ContactLabelIds() {}

    public static boolean contains(String currentValue, String targetId) {
        if (targetId == null || targetId.trim().isEmpty()) return false;
        for (String id : split(currentValue)) {
            if (targetId.trim().equals(id.trim())) return true;
        }
        return false;
    }

    /** Returns null for an unreadable/missing value, keeping it distinct from a valid empty list. */
    public static Boolean membershipState(String readableValue, String targetId) {
        if (readableValue == null || targetId == null || targetId.trim().isEmpty()) return null;
        return contains(readableValue, targetId);
    }

    /** Returns null for invalid arguments, the original value for a no-op, or the updated value. */
    public static String setMembership(String currentValue, String targetId, boolean enabled) {
        if (targetId == null || targetId.trim().isEmpty()) return null;
        String target = targetId.trim();
        List<String> ids = split(currentValue);
        boolean present = contains(currentValue, target);
        if (present == enabled) return currentValue == null ? "" : currentValue;
        if (enabled) {
            ids.add(target);
        } else {
            ids.removeIf(id -> target.equals(id.trim()));
        }
        if (ids.isEmpty()) return "";
        return join(ids) + '\u0000';
    }

    private static List<String> split(String value) {
        List<String> ids = new ArrayList<>();
        if (value == null || value.isEmpty()) return ids;
        String normalized = value.endsWith("\u0000")
                ? value.substring(0, value.length() - 1) : value;
        for (String id : normalized.split(",", -1)) {
            if (!id.isEmpty()) ids.add(id);
        }
        return ids;
    }

    private static String join(List<String> ids) {
        StringBuilder result = new StringBuilder();
        for (String id : ids) {
            if (result.length() > 0) result.append(',');
            result.append(id);
        }
        return result.toString();
    }
}
