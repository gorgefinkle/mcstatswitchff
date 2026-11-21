package dev.freakyfriday.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One selector swaps the WHOLE category. These rules classify registered attribute IDs;
 * unknown modded attributes remain discoverable under "modded", never silently dropped.
 * Third-party integrations may override classification before registration is frozen.
 */
public final class CategoryDefinitions {
    private CategoryDefinitions() {}
    public record Group(String id, String label) {}
    public static final Group VITALITY = new Group("vitality", "Vitality");
    public static final Group MOBILITY = new Group("mobility", "Mobility");
    public static final Group COMBAT = new Group("combat", "Combat");
    public static final Group DEFENSE = new Group("defense", "Defense");
    public static final Group PHYSICAL = new Group("physical", "Physical Traits");
    public static final Group UTILITY = new Group("utility", "Other Vanilla Stats");
    public static final Group MODDED = new Group("modded", "Modded Stats");
    // Deliberately not selectable until real ability/behavior transfer is implemented.
    public static final Group SIGNATURE = new Group("signature", "Signature Abilities");
    public static final Map<String, Group> GROUPS;
    private static final Map<String, String> ASSIGNMENTS = new LinkedHashMap<>();
    static {
        Map<String,Group> groups = new LinkedHashMap<>();
        for (Group g : new Group[]{VITALITY,MOBILITY,COMBAT,DEFENSE,PHYSICAL,UTILITY,MODDED,SIGNATURE}) groups.put(g.id(),g);
        GROUPS = Collections.unmodifiableMap(groups);
        assign(VITALITY, "max_health", "max_absorption", "oxygen_bonus", "healing_efficiency");
        assign(MOBILITY, "movement_speed", "flying_speed", "jump_strength", "gravity", "step_height", "safe_fall_distance", "fall_damage_multiplier", "sneaking_speed", "movement_efficiency", "water_movement_efficiency", "submerged_mining_speed", "mining_efficiency");
        assign(COMBAT, "attack_damage", "attack_speed", "attack_knockback", "attack_range", "entity_interaction_range", "block_interaction_range", "sweeping_damage_ratio");
        assign(DEFENSE, "armor", "armor_toughness", "knockback_resistance", "explosion_knockback_resistance", "burning_time", "enchantment_effectiveness");
        assign(PHYSICAL, "scale", "block_break_speed");
    }
    private static void assign(Group group, String... paths) {
        for (String path : paths) ASSIGNMENTS.put("minecraft:" + path,group.id());
    }
    /** Explicit extension point. Requiring known group IDs prevents arbitrary UI pollution. */
    public static synchronized void classify(String registryId, String categoryId) {
        Objects.requireNonNull(registryId);
        if (!registryId.contains(":")) throw new IllegalArgumentException("Expected namespaced attribute id");
        if (!GROUPS.containsKey(categoryId) || SIGNATURE.id().equals(categoryId))
            throw new IllegalArgumentException("Invalid numeric attribute category: " + categoryId);
        ASSIGNMENTS.put(registryId, categoryId);
    }
    public static synchronized String categoryFor(String id) {
        String known = ASSIGNMENTS.get(id);
        if (known != null) return known;
        // Modded attributes are classified by intent when the ID clearly says
        // what the stat measures, without a whitelist of mod names or species.
        // Unclear names remain under Modded Stats for explicit integrations.
        if (!id.startsWith("minecraft:")) {
            int colon = id.indexOf(':');
            String path = colon >= 0 ? id.substring(colon + 1).toLowerCase(java.util.Locale.ROOT) : id;
            String[] tokens = path.split("[^a-z0-9]+");
            java.util.Set<String> words = new java.util.HashSet<>(java.util.Arrays.asList(tokens));
            if (words.contains("health") || words.contains("healing") || words.contains("absorption")
                    || words.contains("oxygen")) return VITALITY.id();
            // Resolve semantic qualifiers before generic terms: attack_speed is
            // combat, not mobility, and armor_speed is defense.
            if (words.contains("attack") || words.contains("damage") || words.contains("weapon")
                    || words.contains("critical")) return COMBAT.id();
            if (words.contains("armor") || words.contains("defense") || words.contains("resistance")
                    || words.contains("shield")) return DEFENSE.id();
            if (words.contains("speed") || words.contains("movement") || words.contains("gravity")
                    || words.contains("flight") || words.contains("jump") || words.contains("swim")) return MOBILITY.id();
            if (words.contains("scale") || words.contains("mass") || words.contains("weight")
                    || words.contains("size")) return PHYSICAL.id();
            return MODDED.id();
        }
        return UTILITY.id();
    }
    public static Set<String> numericCategories() {
        return Set.of(VITALITY.id(),MOBILITY.id(),COMBAT.id(),DEFENSE.id(),PHYSICAL.id(),UTILITY.id(),MODDED.id());
    }
}
