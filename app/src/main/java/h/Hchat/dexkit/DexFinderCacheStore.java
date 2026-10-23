package h.Hchat.dexkit;

/**
 * Separates the complete global DexFinder snapshot from the lazy contact sender cache.
 * The preference interfaces keep the persistence policy testable without Android.
 */
final class DexFinderCacheStore {
    static final String GLOBAL_COMPLETE_KEY = "cache.complete";
    static final String GLOBAL_KEY_KEY = "cache.key";
    static final String GLOBAL_VERSION_KEY = "cache.snapshot.version";
    static final String CONTACT_COMPLETE_KEY = "contact.complete";
    static final String CONTACT_KEY_KEY = "contact.cache.key";
    static final String CONTACT_VERSION_KEY = "contact.cache.version";

    interface Preferences {
        String getString(String key, String fallback);
        boolean getBoolean(String key, boolean fallback);
        Editor edit();
    }

    interface Editor {
        Editor clear();
        Editor putString(String key, String value);
        Editor putBoolean(String key, boolean value);
        void apply();
    }

    private final Preferences global;
    private final Preferences contact;

    DexFinderCacheStore(Preferences global, Preferences contact) {
        this.global = global;
        this.contact = contact;
    }

    boolean canLoadGlobal(boolean runtimeMatches, String savedVersion, String expectedVersion,
                          boolean complete) {
        return global != null && runtimeMatches && complete
                && expectedVersion.equals(savedVersion);
    }

    boolean canSaveGlobal(boolean resolutionComplete, boolean descriptorsValid) {
        return global != null && resolutionComplete && descriptorsValid;
    }

    boolean canLoadContact(boolean runtimeMatches, String savedVersion, String expectedVersion,
                           boolean complete) {
        return contact != null && runtimeMatches && complete
                && expectedVersion.equals(savedVersion);
    }

    boolean canSaveContact(boolean complete) {
        return contact != null && complete;
    }

    Editor beginGlobalSnapshot(boolean resolutionComplete, boolean descriptorsValid,
                               String runtimeKey, String version) {
        if (global == null || !resolutionComplete || !descriptorsValid) return null;
        return global.edit().clear()
                .putString(GLOBAL_KEY_KEY, runtimeKey)
                .putString(GLOBAL_VERSION_KEY, version)
                .putBoolean(GLOBAL_COMPLETE_KEY, true);
    }

    Editor beginContactSnapshot(boolean complete, String runtimeKey, String version) {
        if (contact == null || !complete) return null;
        return contact.edit().clear()
                .putString(CONTACT_KEY_KEY, runtimeKey)
                .putString(CONTACT_VERSION_KEY, version)
                .putBoolean(CONTACT_COMPLETE_KEY, true);
    }

    void clearContactSnapshot() {
        if (contact != null) contact.edit().clear().apply();
    }
}
