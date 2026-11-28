package dev.freakyfriday;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.neoforged.neoforge.event.entity.EntityAttributeModificationEvent;

/**
 * Gives every living body a complete attribute lookup surface before worlds load.
 *
 * For each registered attribute, native entity values are preserved. Only a
 * missing slot is added, using the attribute's declared default value. This
 * includes attributes registered by other mods so a Mob goal/controller never
 * fails simply because its current physical body came from another entity type.
 *
 * Adding a numeric slot does NOT add the behavior that might read it: flying
 * speed does not make a cow fly, attack damage does not give it an attack goal,
 * and a modded attribute does not install the mod's AI or capability.
 *
 * Player is also added explicitly because it is an Avatar rather than a normal
 * Mob-backed EntityType in the attribute event's initial living-type snapshot.
 * Keep vanilla's explicit waypoint range values when those player slots are
 * absent, as ServerPlayer chunk tracking queries them.
 */
public final class PortableAttributes {
    private PortableAttributes() {}

    private static boolean isPlayerWaypointRange(Identifier id) {
        return id.toString().equals("minecraft:waypoint_receive_range")
            || id.toString().equals("minecraft:waypoint_transmit_range");
    }

    private static double defaultFor(EntityType<? extends LivingEntity> type,
            Identifier id, Holder<Attribute> attribute) {
        if (type == EntityTypes.PLAYER && isPlayerWaypointRange(id)) return 6.0E7;
        return attribute.value().getDefaultValue();
    }

    public static void addToEntityTypes(EntityAttributeModificationEvent event) {
        // Registry.listElements() is a Stream in 26.2 (not an Iterable).
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            Identifier id = BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
            if (id == null) return;

            for (EntityType<? extends LivingEntity> type : event.getTypes()) {
                if (!event.has(type, holder)) {
                    event.add(type, holder, defaultFor(type, id, holder));
                }
            }

            if (!event.has(EntityTypes.PLAYER, holder)) {
                event.add(EntityTypes.PLAYER, holder,
                    defaultFor(EntityTypes.PLAYER, id, holder));
            }
        });
    }
}
