package dev.freakyfriday;

import dev.freakyfriday.core.CategoryBundle;
import dev.freakyfriday.core.CategoryExchange;
import dev.freakyfriday.core.PermanentModifier;
import java.util.Map;
import java.util.Set;
import net.minecraft.SharedConstants;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;

/**
 * Non-mocked Mojang 26.2 runtime integration probe for the REAL production
 * AttributeModifierTransfer class and CategoryExchange engine. Does not launch
 * NeoForge and does not instantiate live entities / worlds.
 */
public final class MojangAttributeRuntimeTest {
    private MojangAttributeRuntimeTest() {}
    private static final String KEY = "minecraft:armor";
    private static int checks;

    static void check(boolean test, String description) {
        checks++;
        if (!test) throw new AssertionError(description);
    }

    static final class Actor {
        final AttributeInstance attribute;
        Actor(Holder<Attribute> holder, double base) {
            attribute = new AttributeInstance(holder, changed -> {});
            attribute.setBaseValue(base);
        }
    }

    private static AttributeModifier modifier(String id, double amount) {
        return new AttributeModifier(Identifier.parse(id), amount,
            AttributeModifier.Operation.ADD_VALUE);
    }

    private static CategoryBundle state(Actor actor) {
        var mods = AttributeModifierTransfer.snapshot(actor.attribute);
        return new CategoryBundle(Map.of(KEY, actor.attribute.getBaseValue()), Map.of(),
            mods.isEmpty() ? Map.of() : Map.of(KEY, mods));
    }

    private static final CategoryExchange.Port<Actor, CategoryBundle> PORT = new CategoryExchange.Port<>() {
        public CategoryBundle snapshot(Actor actor) { return state(actor); }
        public void validate(Actor recipient, CategoryBundle incoming) {
            double base = incoming.attributes().get(KEY);
            double sanitized = recipient.attribute.getAttribute().value().sanitizeValue(base);
            if (Double.compare(sanitized, base) != 0) throw new IllegalArgumentException("Out-of-range base");
            AttributeModifierTransfer.validate(recipient.attribute,
                incoming.permanentModifiers().getOrDefault(KEY, Set.of()), KEY);
        }
        public void replace(Actor recipient, CategoryBundle incoming) {
            AttributeModifierTransfer.replace(recipient.attribute, incoming.attributes().get(KEY),
                incoming.permanentModifiers().getOrDefault(KEY, Set.of()), KEY);
        }
        public void restore(Actor recipient, CategoryBundle original) { replace(recipient, original); }
    };

    public static void main(String[] args) throws Exception {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        Holder<Attribute> attribute = Holder.direct(new RangedAttribute("attribute.test.armor", 0, 0, 100));
        Actor a = new Actor(attribute, 6);
        Actor b = new Actor(attribute, 17);
        var aPerm = modifier("test:a_permanent", 3);
        var bPerm = modifier("test:b_permanent", 7);
        var aTemp = modifier("test:a_armor_item", 5);
        var bTemp = modifier("test:b_potion", 11);
        a.attribute.addPermanentModifier(aPerm);
        b.attribute.addPermanentModifier(bPerm);
        a.attribute.addTransientModifier(aTemp);
        b.attribute.addTransientModifier(bTemp);
        CategoryBundle originalA = state(a), originalB = state(b);
        check(a.attribute.getValue() == 14, "real Minecraft computed A base+permanent+transient");
        check(b.attribute.getValue() == 35, "real Minecraft computed B base+permanent+transient");
        check(AttributeModifierTransfer.snapshot(a.attribute).size() == 1,
            "snapshot excludes transient items");
        CategoryExchange.exchange(a, b, PORT);
        check(state(a).equals(originalB), "first actor received entire B bundle");
        check(state(b).equals(originalA), "second actor received entire A bundle");
        check(a.attribute.hasModifier(aTemp.id()), "A kept its transient modifier");
        check(b.attribute.hasModifier(bTemp.id()), "B kept its transient modifier");
        check(!a.attribute.hasModifier(aPerm.id()) && a.attribute.hasModifier(bPerm.id()),
            "A permanent modifier ownership transferred, old removed");
        check(!b.attribute.hasModifier(bPerm.id()) && b.attribute.hasModifier(aPerm.id()),
            "B permanent modifier ownership transferred, old removed");
        check(a.attribute.getValue() == 29, "A effective stat includes new B permanent + A transient");
        check(b.attribute.getValue() == 20, "B effective stat includes new A permanent + B transient");
        CategoryExchange.exchange(a, b, PORT);
        check(state(a).equals(originalA) && state(b).equals(originalB), "reverse whole category restores values/modifiers");
        check(a.attribute.getValue() == 14 && b.attribute.getValue() == 35,
            "reverse restores effective stats without moving transient effects");

        // Incoming persistent ID colliding with a recipient's transient ID is
        // rejected BEFORE any entity is touched (not deleted or replaced).
        var collision = modifier("test:b_permanent", 1);
        a.attribute.addTransientModifier(collision);
        CategoryBundle collisionA = state(a), collisionB = state(b);
        try { CategoryExchange.exchange(a, b, PORT); throw new AssertionError("Expected collision rejection"); }
        catch (IllegalArgumentException expected) { check(expected.getMessage().contains("collision"), "preflight collision diagnostic"); }
        check(state(a).equals(collisionA) && state(b).equals(collisionB), "collision didn't partially exchange values");
        check(a.attribute.hasModifier(collision.id()) && a.attribute.hasModifier(aTemp.id()),
            "collision left transient modifiers untouched");
        a.attribute.removeModifier(collision.id());

        // Real RangedAttribute values reject out-of-bounds bases.
        CategoryBundle invalid = new CategoryBundle(Map.of(KEY, 999.0), Map.of());
        try { PORT.validate(a, invalid); throw new AssertionError("Expected range rejection"); }
        catch (IllegalArgumentException expected) { check(true, "real attribute bounds preflight rejected 999"); }
        check(state(a).equals(originalA), "range failure left original state intact");

        // Force a failure AFTER writing to the first actor. Production engine
        // must restore both original bundles, including permanent modifiers.
        var injecting = new CategoryExchange.Port<Actor, CategoryBundle>() {
            int writes;
            public CategoryBundle snapshot(Actor actor) throws Exception { return PORT.snapshot(actor); }
            public void validate(Actor actor, CategoryBundle incoming) throws Exception { PORT.validate(actor, incoming); }
            public void replace(Actor actor, CategoryBundle incoming) throws Exception {
                PORT.replace(actor, incoming);
                if (++writes == 1) throw new IllegalStateException("injected post-write failure");
            }
            public void restore(Actor actor, CategoryBundle old) throws Exception { PORT.restore(actor, old); }
        };
        try { CategoryExchange.exchange(a, b, injecting); throw new AssertionError("Expected injected failure"); }
        catch (IllegalStateException expected) { check(expected.getMessage().contains("injected"), "injected failure reported"); }
        check(state(a).equals(originalA) && state(b).equals(originalB),
            "real AttributeInstance rollback restored both category bundles");
        check(a.attribute.hasModifier(aTemp.id()) && b.attribute.hasModifier(bTemp.id()),
            "rollback preserved both transient modifiers");

        // Finally prove an actual two-attribute category exchange, not merely
        // a one-attribute transfer. All the values and permanent bonuses move
        // together, while transient items stay with their original actors.
        final String toughnessId = "minecraft:armor_toughness";
        Holder<Attribute> toughness = Holder.direct(
            new RangedAttribute("attribute.test.toughness", 0, 0, 100));
        Actor aToughness = new Actor(toughness, 2);
        Actor bToughness = new Actor(toughness, 9);
        aToughness.attribute.addPermanentModifier(modifier("test:a_toughness", 1));
        bToughness.attribute.addPermanentModifier(modifier("test:b_toughness", 4));
        aToughness.attribute.addTransientModifier(modifier("test:armor_item_toughness", 0.5));
        record Pair(Actor armor, Actor tough) {}
        Pair pairA = new Pair(a, aToughness);
        Pair pairB = new Pair(b, bToughness);
        CategoryExchange.Port<Pair, CategoryBundle> pairPort = new CategoryExchange.Port<>() {
            public CategoryBundle snapshot(Pair pair) {
                return new CategoryBundle(Map.of(KEY, pair.armor.attribute.getBaseValue(),
                    toughnessId, pair.tough.attribute.getBaseValue()), Map.of(),
                    Map.of(KEY, AttributeModifierTransfer.snapshot(pair.armor.attribute),
                        toughnessId, AttributeModifierTransfer.snapshot(pair.tough.attribute)));
            }
            public void validate(Pair recipient, CategoryBundle incoming) {
                AttributeModifierTransfer.validate(recipient.armor.attribute,
                    incoming.permanentModifiers().getOrDefault(KEY, Set.of()), KEY);
                AttributeModifierTransfer.validate(recipient.tough.attribute,
                    incoming.permanentModifiers().getOrDefault(toughnessId, Set.of()), toughnessId);
            }
            public void replace(Pair recipient, CategoryBundle incoming) {
                AttributeModifierTransfer.replace(recipient.armor.attribute, incoming.attributes().get(KEY),
                    incoming.permanentModifiers().getOrDefault(KEY, Set.of()), KEY);
                AttributeModifierTransfer.replace(recipient.tough.attribute, incoming.attributes().get(toughnessId),
                    incoming.permanentModifiers().getOrDefault(toughnessId, Set.of()), toughnessId);
            }
            public void restore(Pair recipient, CategoryBundle before) { replace(recipient, before); }
        };
        CategoryBundle originalPairA = pairPort.snapshot(pairA);
        CategoryBundle originalPairB = pairPort.snapshot(pairB);
        CategoryExchange.exchange(pairA, pairB, pairPort);
        check(pairPort.snapshot(pairA).equals(originalPairB), "whole two-attribute category moved to A");
        check(pairPort.snapshot(pairB).equals(originalPairA), "whole two-attribute category moved to B");
        check(aToughness.attribute.hasModifier(Identifier.parse("test:armor_item_toughness")),
            "two-attribute category preserved toughness transient effect");
        CategoryExchange.exchange(pairA, pairB, pairPort);
        check(pairPort.snapshot(pairA).equals(originalPairA)
                && pairPort.snapshot(pairB).equals(originalPairB),
            "whole two-attribute category reverses exactly");

        System.out.println("PASS MojangAttributeRuntimeTest: " + checks +
            " checks against REAL Minecraft 26.2 AttributeInstance/RangedAttribute; no game world booted");
    }
}
