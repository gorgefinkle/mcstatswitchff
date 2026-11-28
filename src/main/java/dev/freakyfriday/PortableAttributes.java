package dev.freakyfriday;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * Adds a deliberately small set of generally applicable numerical attributes
 * to ALL living entity types, including third-party types registered by other mods.
 *
 * IMPORTANT: Registering an attribute is NOT the same as transferring a behavior.
 * Fly-speed does not teach a cow how to fly; attack damage does not add an attack AI.
 * Only stats whose vanilla handling is broadly species-independent are eligible.
 * Attributes from other mods are NEVER added speculatively to unrelated types.
 *
 * This runs during NeoForge's entity attribute modification lifecycle, before
 * any worlds load. It is not a per-pair special case or an after-the-fact repair.
 */
public final class PortableAttributes {
    private PortableAttributes() {}

    private static final Set<String> SHARED_NUMERIC = Set.of(
        "minecraft:max_absorption",             // LivingEntity absorption reserve
        "minecraft:oxygen_bonus",               // generic drowning calculation
        "minecraft:armor",                      // generic damage reduction
        "minecraft:armor_toughness",            // generic damage reduction
        "minecraft:knockback_resistance",       // general knockback reduction
        "minecraft:explosion_knockback_resistance", // general explosion knockback
        "minecraft:waypoint_receive_range",         // generic waypoint visibility range
        "minecraft:waypoint_transmit_range"         // generic waypoint broadcast range
    );

    public static void addToEntityTypes(EntityAttributeModificationEvent event) {
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            Identifier id = BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
            if (id == null || !SHARED_NUMERIC.contains(id.toString())) return;
            boolean waypointRange = id.toString().equals("minecraft:waypoint_receive_range")
                || id.toString().equals("minecraft:waypoint_transmit_range");
            for (EntityType<? extends LivingEntity> type : event.getTypes()) {
                if (!event.has(type, holder)) {
                    double defaultValue = type == EntityType.PLAYER && waypointRange
                        ? 6.0E7
                        : holder.value().getDefaultValue();
                    event.add(type, holder, defaultValue);
                }
            }

            // Player's waypoint ranges are normally supplied by Player's
            // vanilla attribute builder. Some dedicated/mock ServerPlayer
            // construction paths can arrive without those two instances,
            // while ServerPlayer.tick unconditionally queries the receive
            // range during chunk-view updates. Ensure the canonical player
            // defaults exist before any world/player is constructed, even
            // when the Player type was absent from the initial event type list.
            if (waypointRange && !event.has(EntityType.PLAYER, holder)) {
                event.add(EntityType.PLAYER, holder, 6.0E7);
            }
        });
    }
}
