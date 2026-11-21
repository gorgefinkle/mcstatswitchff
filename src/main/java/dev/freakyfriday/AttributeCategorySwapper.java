package dev.freakyfriday;

import dev.freakyfriday.core.CategoryBundle;
import dev.freakyfriday.core.CategoryDefinitions;
import dev.freakyfriday.core.CategoryExchange;
import dev.freakyfriday.core.CategorySchema;
import dev.freakyfriday.core.PermanentModifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * Phase 1 implementation: a category exchanges ALL permanent numerical attribute
 * BASE VALUES and PERMANENT MODIFIERS, or the transaction is rejected. This does NOT yet transfer
 * AI/behavior abilities (Phase 3) or equipment/potion modifier ownership (transient modifiers remain with their owners).
 *
 * Registered mod attributes are discovered dynamically; no mob whitelist.
 * For safety, an attribute unavailable to the other mob causes a whole-category
 * rejection, never a partial swap or a 'successful' but nonfunctional property.
 */
public final class AttributeCategorySwapper {
    private AttributeCategorySwapper() {}
    private static final String CURRENT_HEALTH = "freakyfriday:current_health";
    private static final String ABSORPTION = "freakyfriday:current_absorption";

    private static final class Entry {
        final String id;
        final Holder<Attribute> holder;
        Entry(String id, Holder<Attribute> holder) { this.id=id; this.holder=holder; }
    }
    private static List<Entry> registered(String category) {
        List<Entry> values = new ArrayList<>();
        BuiltInRegistries.ATTRIBUTE.listElements().forEach(holder -> {
            Identifier id = BuiltInRegistries.ATTRIBUTE.getKey(holder.value());
            if (id != null && CategoryDefinitions.categoryFor(id.toString()).equals(category))
                values.add(new Entry(id.toString(), holder));
        });
        values.sort(Comparator.comparing(e -> e.id));
        return values;
    }
    public static Map<String, Integer> availableCategories(LivingEntity entity) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String category : CategoryDefinitions.GROUPS.keySet()) {
            if (!CategoryDefinitions.numericCategories().contains(category)) continue;
            int count = 0;
            for (Entry entry : registered(category)) {
                if (entity.getAttributes().getInstance(entry.holder) != null) count++;
            }
            if (CategoryDefinitions.VITALITY.id().equals(category)) count += 2; // live health + absorption
            if (count > 0) result.put(category, count);
        }
        return Collections.unmodifiableMap(result);
    }
    public static String exchange(LivingEntity first, LivingEntity second, String category) {
        if (!CategoryDefinitions.numericCategories().contains(category))
            return "Category not available for numerical swapping: " + category;
        List<Entry> entries = registered(category);
        CategoryExchange.Port<LivingEntity, CategoryBundle> port = new CategoryExchange.Port<>() {
            public CategoryBundle snapshot(LivingEntity entity) {
                Map<String, Double> values = new LinkedHashMap<>();
                Map<String, Set<PermanentModifier>> permanent = new LinkedHashMap<>();
                for (Entry entry : entries) {
                    AttributeInstance attribute = entity.getAttributes().getInstance(entry.holder);
                    if (attribute == null) continue;
                    values.put(entry.id, attribute.getBaseValue());
                    Set<PermanentModifier> owned = AttributeModifierTransfer.snapshot(attribute);
                    if (!owned.isEmpty()) permanent.put(entry.id, owned);
                }
                if (CategoryDefinitions.VITALITY.id().equals(category)) {
                    values.put(CURRENT_HEALTH, (double) entity.getHealth());
                    values.put(ABSORPTION, (double) entity.getAbsorptionAmount());
                }
                return new CategoryBundle(values, Map.of(), permanent);
            }
            public void validate(LivingEntity recipient, CategoryBundle incoming) {
                // A true category transfer cannot discard or fabricate unsupported
                // attributes. Asymmetric schemas require a future portability adapter.
                Map<String, Double> supported = snapshot(recipient).attributes();
                CategorySchema schema = CategorySchema.compare(supported.keySet(), incoming.attributes().keySet());
                if (!schema.compatible()) throw new IllegalArgumentException(schema.explain());
                for (Entry entry : entries) {
                    Double value = incoming.attributes().get(entry.id);
                    if (value == null) continue;
                    // A real AttributeInstance may clamp a value even though setBaseValue
                    // does not throw. Reject *before* touching either recipient.
                    double normalized = entry.holder.value().sanitizeValue(value);
                    if (!Double.isFinite(normalized) || Double.compare(normalized, value) != 0) {
                        throw new IllegalArgumentException("Attribute value outside valid range for " + entry.id
                            + ": " + value + "; entire category remains unchanged");
                    }
                }
                // Validate the actual production modifier transfer helper against
                // every recipient attribute before either entity is modified.
                for (Entry entry : entries) {
                    AttributeInstance instance = recipient.getAttributes().getInstance(entry.holder);
                    if (instance == null) continue;
                    AttributeModifierTransfer.validate(instance,
                        incoming.permanentModifiers().getOrDefault(entry.id, Set.of()), entry.id);
                }
                for (double value : incoming.attributes().values()) {
                    if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite attribute value");
                }
                if (CategoryDefinitions.VITALITY.id().equals(category)
                        && incoming.attributes().get(CURRENT_HEALTH) <= 0.0)
                    throw new IllegalArgumentException("Cannot transfer zero/negative live health");
            }
            public void replace(LivingEntity recipient, CategoryBundle incoming) {
                for (Entry entry : entries) {
                    Double value = incoming.attributes().get(entry.id);
                    if (value == null) continue;
                    AttributeInstance instance = recipient.getAttributes().getInstance(entry.holder);
                    if (instance == null)
                        throw new IllegalStateException("Attribute disappeared during exchange: " + entry.id);
                    AttributeModifierTransfer.replace(instance, value,
                        incoming.permanentModifiers().getOrDefault(entry.id, Set.of()), entry.id);
                    // The exchange engine verifies every modifier by readback after
                    // both entities have been written; any failure triggers rollback.
                }
                if (CategoryDefinitions.VITALITY.id().equals(category)) {
                    // These are actual live state, not permanent attribute bases.
                    // Apply after MAX_HEALTH, so a full vitality trade preserves injuries.
                    float health = incoming.attributes().get(CURRENT_HEALTH).floatValue();
                    float absorption = incoming.attributes().get(ABSORPTION).floatValue();
                    recipient.setHealth(health);
                    recipient.setAbsorptionAmount(absorption);
                    if (Float.compare(recipient.getHealth(), health) != 0 ||
                        Float.compare(recipient.getAbsorptionAmount(), absorption) != 0)
                        throw new IllegalStateException("The vitality values cannot be transferred without loss");
                }
            }
            public void restore(LivingEntity recipient, CategoryBundle original) {
                replace(recipient, original);
            }
        };
        try {
            // Both entities' full key sets are compared in one stable order.
            // This preflight is diagnostic only; each recipient is revalidated
            // inside the transaction so no incompatible transfer can slip in.
            CategorySchema schemas = CategorySchema.compare(
                port.snapshot(first).attributes().keySet(),
                port.snapshot(second).attributes().keySet());
            if (!schemas.compatible()) return schemas.explain();
            CategoryExchange.exchange(first,second,port);
        } catch (Exception failure) {
            String message = failure.getMessage();
            if (failure.getSuppressed().length > 0) {
                // Suppressed exceptions signal that some part of the attempted
                // recovery may not have succeeded. Never tell the player it was atomic.
                return "Exchange failed; recovery could not be verified on both entities. "
                    + (message != null ? message : failure.getClass().getSimpleName());
            }
            return message != null ? message : "Exchange failed: " + failure.getClass().getSimpleName();
        }
        return null;
    }
}
