package h.Hchat.dexkit;

import java.util.function.Predicate;
import java.util.function.Supplier;

/** Reuses a trusted, structurally validated descriptor and scans only on targeted recovery. */
public final class DexCacheRestorePolicy {
    private DexCacheRestorePolicy() {}

    public static <T> T restore(boolean runtimeIdentityMatches, boolean locatorWasResolved,
                                T cachedValue, Predicate<T> structureValidator,
                                Supplier<T> targetedQuery) {
        if (runtimeIdentityMatches && locatorWasResolved && cachedValue != null) {
            try {
                if (structureValidator.test(cachedValue)) return cachedValue;
            } catch (Throwable ignored) {
                // A descriptor which cannot be validated falls through to its one locator query.
            }
        }
        try {
            return targetedQuery.get();
        } catch (Throwable ignored) {
            // A locator-specific failure must not turn a partial cache hit into a global rescan.
            return null;
        }
    }
}
