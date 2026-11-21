package dev.freakyfriday;

import dev.freakyfriday.core.PermanentModifier;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * Minecraft-facing permanent-modifier handling for one numerical attribute.
 *
 * Only intrinsic/permanent modifiers belong to the traded category; modifiers
 * granted by equipment or effects remain on their original entity. We reject
 * an incoming permanent ID that would overwrite a retained transient ID.
 *
 * The whole-category transaction and its rollback live in CategoryExchange;
 * these methods are also tested directly against real Mojang 26.2 classes.
 */
public final class AttributeModifierTransfer {
    private AttributeModifierTransfer() {}

    public static Set<PermanentModifier> snapshot(AttributeInstance instance) {
        Set<PermanentModifier> result = new HashSet<>();
        for (AttributeModifier modifier : instance.getPermanentModifiers()) {
            result.add(new PermanentModifier(modifier.id().toString(),
                modifier.amount(), modifier.operation().name()));
        }
        return Set.copyOf(result);
    }

    public static void validate(AttributeInstance recipient, Set<PermanentModifier> incoming,
                                String attributeId) {
        Set<String> existingPermanentIds = new HashSet<>();
        for (AttributeModifier modifier : recipient.getPermanentModifiers()) {
            existingPermanentIds.add(modifier.id().toString());
        }
        Set<String> reservedTransientIds = new HashSet<>();
        for (AttributeModifier modifier : recipient.getModifiers()) {
            if (!existingPermanentIds.contains(modifier.id().toString())) {
                reservedTransientIds.add(modifier.id().toString());
            }
        }
        Set<String> incomingIds = new HashSet<>();
        for (PermanentModifier modifier : incoming) {
            // These validations run before either side of the trade is written.
            Identifier.parse(modifier.id());
            AttributeModifier.Operation.valueOf(modifier.operation());
            if (!incomingIds.add(modifier.id())) {
                throw new IllegalArgumentException("Duplicate incoming modifier on " + attributeId
                    + ": " + modifier.id());
            }
            if (reservedTransientIds.contains(modifier.id())) {
                throw new IllegalArgumentException("Modifier collision with active equipment/effect on "
                    + attributeId + ": " + modifier.id() + "; category unchanged");
            }
        }
    }

    public static void replace(AttributeInstance recipient, double base,
                               Set<PermanentModifier> incoming, String attributeId) {
        // The transaction has preflighted every attribute on both recipients.
        // Never use removeModifiers(), which would also delete transient effects.
        for (AttributeModifier modifier : List.copyOf(recipient.getPermanentModifiers())) {
            if (!recipient.removeModifier(modifier.id())) {
                throw new IllegalStateException("Could not remove permanent modifier " + modifier.id());
            }
        }
        recipient.setBaseValue(base);
        if (Double.compare(recipient.getBaseValue(), base) != 0) {
            throw new IllegalStateException("Attribute refused exact base value: " + attributeId);
        }
        for (PermanentModifier modifier : incoming) {
            recipient.addPermanentModifier(new AttributeModifier(Identifier.parse(modifier.id()),
                modifier.amount(), AttributeModifier.Operation.valueOf(modifier.operation())));
        }
    }
}
