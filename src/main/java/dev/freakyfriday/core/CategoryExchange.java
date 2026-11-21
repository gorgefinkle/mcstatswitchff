package dev.freakyfriday.core;

import java.util.Objects;

/**
 * A complete category is one immutable snapshot (values + registered behavior state).
 * Ports implement capability checks and all-or-nothing local replacement/restore.
 * A failed write is rolled back on BOTH entities, including a partially written left.
 * All calls run on the game thread; never run two exchanges concurrently on these targets.
 */
public final class CategoryExchange {
    private CategoryExchange() {}

    public interface Port<E, B> {
        B snapshot(E entity) throws Exception;
        /** Checks the whole incoming bundle BEFORE either entity is mutated. */
        void validate(E recipient, B incoming) throws Exception;
        /** Replaces the complete category, including removing properties absent from incoming. */
        void replace(E recipient, B incoming) throws Exception;
        /** Restores the exact original state (not necessarily using the same validation rules). */
        void restore(E recipient, B original) throws Exception;
    }

    public static <E, B> void exchange(E first, E second, Port<E, B> port) throws Exception {
        Objects.requireNonNull(first, "first entity");
        Objects.requireNonNull(second, "second entity");
        Objects.requireNonNull(port, "category port");
        if (first == second) throw new IllegalArgumentException("Cannot exchange an entity with itself");
        // No mutation until BOTH snapshots and BOTH validations have succeeded.
        final B firstOriginal = Objects.requireNonNull(port.snapshot(first), "first snapshot");
        final B secondOriginal = Objects.requireNonNull(port.snapshot(second), "second snapshot");
        try {
            port.validate(first, secondOriginal);
            port.validate(second, firstOriginal);
            // Also protect against callbacks that mutate state during preflight.
            if (!firstOriginal.equals(port.snapshot(first)) || !secondOriginal.equals(port.snapshot(second)))
                throw new IllegalStateException("Category changed during validation; exchange aborted before writes");
            port.replace(first, secondOriginal);
            port.replace(second, firstOriginal);
            // A port can silently clamp, skip or transform values without throwing.
            if (!secondOriginal.equals(port.snapshot(first)) || !firstOriginal.equals(port.snapshot(second)))
                throw new IllegalStateException("Category replacement was not exact; rolling back both entities");
        } catch (Exception cause) {
            // Validation is expected to be pure, but an integration can violate
            // that contract. Inspect BOTH entities even if validation throws.
            // Skip writes to unchanged entities; restoring an untouched entity
            // can itself trigger callbacks or other observable side effects.
            recover(port, first, firstOriginal, "First", cause);
            recover(port, second, secondOriginal, "Second", cause);
            throw cause;
        }
    }

    private static <E, B> void recover(Port<E, B> port, E entity, B original,
                                        String name, Exception cause) {
        boolean needsRestore = true;
        try { needsRestore = !original.equals(port.snapshot(entity)); }
        catch (Exception inspectFailure) { cause.addSuppressed(inspectFailure); }
        if (needsRestore) {
            try { port.restore(entity, original); }
            catch (Exception rollback) { cause.addSuppressed(rollback); }
        }
        try {
            if (!original.equals(port.snapshot(entity)))
                cause.addSuppressed(new IllegalStateException(name + " entity rollback did not restore its category"));
        } catch (Exception verifyFailure) { cause.addSuppressed(verifyFailure); }

    }
}
