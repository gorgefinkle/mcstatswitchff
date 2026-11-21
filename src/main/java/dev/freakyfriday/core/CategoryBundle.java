package dev.freakyfriday.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable snapshot of one category: numerical bases, permanent modifiers and
 * (future) executable behavior state. Absence of a modifier set means no permanent
 * modifiers for that attribute. TEMPORARY item/effect modifiers are external to
 * the category and are intentionally never captured or moved.
 */
public record CategoryBundle(Map<String, Double> attributes, Map<String, String> behaviors,
                             Map<String, Set<PermanentModifier>> permanentModifiers) {
    public CategoryBundle(Map<String, Double> attributes, Map<String, String> behaviors) {
        this(attributes, behaviors, Map.of());
    }
    public CategoryBundle {
        attributes = Map.copyOf(Objects.requireNonNull(attributes));
        behaviors = Map.copyOf(Objects.requireNonNull(behaviors));
        for (var item : attributes.entrySet()) {
            if (item.getKey().isBlank() || !Double.isFinite(item.getValue()))
                throw new IllegalArgumentException("Invalid category attribute " + item.getKey());
        }
        for (var item : behaviors.entrySet()) {
            if (item.getKey().isBlank() || item.getValue().isBlank())
                throw new IllegalArgumentException("Invalid category behavior " + item.getKey());
        }
        Map<String, Set<PermanentModifier>> safe = new HashMap<>();
        for (var item : Objects.requireNonNull(permanentModifiers).entrySet()) {
            if (!attributes.containsKey(item.getKey()))
                throw new IllegalArgumentException("Modifier belongs to missing attribute " + item.getKey());
            Set<PermanentModifier> mods = Set.copyOf(Objects.requireNonNull(item.getValue()));
            if (mods.isEmpty()) continue;
            Set<String> ids = new HashSet<>();
            for (PermanentModifier modifier : mods) {
                if (!ids.add(modifier.id()))
                    throw new IllegalArgumentException("Duplicate modifier id on " + item.getKey() + ": " + modifier.id());
            }
            safe.put(item.getKey(), mods);
        }
        permanentModifiers = Map.copyOf(safe);
    }
}
