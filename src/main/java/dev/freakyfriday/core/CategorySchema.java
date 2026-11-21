package dev.freakyfriday.core;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;

/**
 * Describes whether a whole category can be exchanged using the currently
 * supported attribute capabilities. Missing keys are not silently ignored.
 * A complete category swap requires both sides to possess each other's keys
 * or a future verified portability adapter capable of creating AND removing
 * those properties with functional effects.
 */
public record CategorySchema(Set<String> onlyOnFirst, Set<String> onlyOnSecond) {
    public CategorySchema {
        onlyOnFirst = Set.copyOf(onlyOnFirst);
        onlyOnSecond = Set.copyOf(onlyOnSecond);
    }

    public static CategorySchema compare(Set<String> first, Set<String> second) {
        if (first == null || second == null) throw new IllegalArgumentException("Missing category schema");
        var left = new LinkedHashSet<>(first);
        var right = new LinkedHashSet<>(second);
        left.removeAll(second);
        right.removeAll(first);
        return new CategorySchema(left, right);
    }

    public boolean compatible() { return onlyOnFirst.isEmpty() && onlyOnSecond.isEmpty(); }

    /** Describes original entity A-only and entity B-only keys without guessing. */
    public String explain() {
        if (compatible()) return "Compatible category schemas";
        return "Incompatible category contents; whole swap cancelled. "
            + "First entity only: " + sorted(onlyOnFirst)
            + "; second entity only: " + sorted(onlyOnSecond)
            + ". Missing keys require verified functional portability adapters; "
            + "native-only keys cannot simply be deleted.";
    }

    private static String sorted(Set<String> keys) {
        return keys.isEmpty() ? "none" : String.join(", ", new TreeSet<>(keys));
    }
}
