package dev.freakyfriday.core;

import java.util.HashMap;
import java.util.Map;

/** Executable deterministic harness without Minecraft or Gradle. Run with scripts/test-core.sh. */
public final class CategoryExchangeSelfTest {
    private static int assertions;
    private static void check(boolean expression, String name) {
        assertions++;
        if (!expression) throw new AssertionError(name);
    }
    private static final class Mob {
        Map<String, Double> stats = new HashMap<>();
        Map<String, String> abilities = new HashMap<>();
        boolean accept = true;
        int failureAt = -1;
        int writes;
        boolean silentSkip;
        boolean breakRestore;
        boolean corruptPreflight;
        boolean throwPreflight;
        int restores;
        Mob(Map<String, Double> stats, Map<String, String> abilities) {
            this.stats.putAll(stats); this.abilities.putAll(abilities);
        }
        CategoryBundle read() { return new CategoryBundle(stats, abilities); }
    }
    private static final CategoryExchange.Port<Mob, CategoryBundle> PORT = new CategoryExchange.Port<>() {
        public CategoryBundle snapshot(Mob m) { return m.read(); }
        public void validate(Mob m, CategoryBundle incoming) {
            if (m.corruptPreflight) m.stats.put("preflight_corruption", 1.0);
            if (m.throwPreflight) throw new IllegalStateException("injected preflight failure");
            if (!m.accept) throw new IllegalStateException("recipient rejected whole category");
        }
        public void replace(Mob m, CategoryBundle incoming) {
            m.writes++;
            if (m.silentSkip) { m.silentSkip = false; return; } // test silent write omission
            m.stats.clear();
            if (m.writes == m.failureAt) throw new IllegalStateException("failure after first partial write");
            m.stats.putAll(incoming.attributes());
            m.abilities.clear(); m.abilities.putAll(incoming.behaviors());
        }
        public void restore(Mob m, CategoryBundle original) {
            m.restores++;
            if (m.breakRestore) throw new IllegalStateException("injected rollback failure");
            m.stats.clear(); m.stats.putAll(original.attributes());
            m.abilities.clear(); m.abilities.putAll(original.behaviors());
        }
    };
    private static void expectFailure(Mob a, Mob b, String description) {
        try { CategoryExchange.exchange(a,b,PORT); throw new AssertionError("Expected rejection: " + description); }
        catch (IllegalStateException expected) { assertions++; }
        catch (Exception unexpected) { throw new AssertionError(description, unexpected); }
    }
    public static void main(String[] args) throws Exception {
        Mob bat = new Mob(Map.of("speed", 0.8, "flight", 0.9), Map.of("flight", "bat-movement", "sonar", "bat-ai"));
        Mob creeper = new Mob(Map.of("speed", 0.2), Map.of("fuse", "creeper-fuse"));
        CategoryBundle originalBat = bat.read(), originalCreeper = creeper.read();
        CategoryExchange.exchange(bat, creeper, PORT);
        check(bat.read().equals(originalCreeper), "whole first bundle, including removing surplus entries");
        check(creeper.read().equals(originalBat), "whole second bundle, including new attribute and two abilities");
        CategoryExchange.exchange(bat, creeper, PORT);
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper), "reversal is lossless");
        // A category is not just its base values: multiple permanent modifiers
        // must travel with it and reverse exactly, without aliasing snapshot maps.
        Mob boosted = new Mob(Map.of("armor", 4.0), Map.of());
        Mob plain = new Mob(Map.of("armor", 1.0), Map.of());
        CategoryBundle aBoost = new CategoryBundle(boosted.read().attributes(), Map.of(),
            Map.of("armor", java.util.Set.of(new PermanentModifier("ff:shield", 3.0, "ADD_VALUE"),
                new PermanentModifier("ff:perk", 0.15, "ADD_MULTIPLIED_TOTAL"))));
        CategoryBundle bPlain = plain.read();
        var modifierPort = new CategoryExchange.Port<Mob, CategoryBundle>() {
            public CategoryBundle snapshot(Mob m) { return m == boosted ? boostedSnapshot[0] : plainSnapshot[0]; }
            public void validate(Mob m, CategoryBundle b) {}
            public void replace(Mob m, CategoryBundle b) { if (m == boosted) boostedSnapshot[0]=b; else plainSnapshot[0]=b; }
            public void restore(Mob m, CategoryBundle b) { replace(m,b); }
            final CategoryBundle[] boostedSnapshot = {aBoost};
            final CategoryBundle[] plainSnapshot = {bPlain};
        };
        CategoryExchange.exchange(boosted, plain, modifierPort);
        check(modifierPort.snapshot(boosted).equals(bPlain) && modifierPort.snapshot(plain).equals(aBoost),
            "full category includes both permanent modifiers");
        CategoryExchange.exchange(boosted, plain, modifierPort);
        check(modifierPort.snapshot(boosted).equals(aBoost) && modifierPort.snapshot(plain).equals(bPlain),
            "permanent modifier bundle reversible");
        try { aBoost.permanentModifiers().get("armor").clear(); throw new AssertionError("mutable modifier set"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        try { new CategoryBundle(Map.of("armor", 5.0), Map.of(),
                Map.of("armor", java.util.Set.of(new PermanentModifier("ff:duplicate", 1, "ADD_VALUE"),
                    new PermanentModifier("ff:duplicate", 2, "ADD_VALUE"))));
            throw new AssertionError("duplicate ID allowed"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { new CategoryBundle(Map.of("armor", 1.0), Map.of(),
                Map.of("missing", java.util.Set.of(new PermanentModifier("ff:x", 1, "ADD_VALUE"))));
            throw new AssertionError("orphan modifier allowed"); }
        catch (IllegalArgumentException expected) { assertions++; }
        bat.accept=false;
        expectFailure(bat,creeper,"validation");
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper),"rejection leaves both unmodified");
        bat.accept=true;
        int noOpRestores = bat.restores + creeper.restores;
        bat.throwPreflight=true;
        expectFailure(bat, creeper, "preflight exception no mutation");
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper),
            "preflight exception doesn't change category");
        check(bat.restores + creeper.restores == noOpRestores,
            "recovery skips unnecessary writes on untouched entities");
        bat.throwPreflight=false;
        bat.corruptPreflight=true;
        expectFailure(bat, creeper, "preflight mutated category");
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper),
            "preflight corruption detected and rolled back");
        bat.corruptPreflight=false;
        bat.failureAt=bat.writes+1;
        expectFailure(bat,creeper,"first side partial failure");
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper),"first side failure rolled back");
        bat.failureAt=-1;
        creeper.failureAt=creeper.writes+1;
        expectFailure(bat,creeper,"second side partial failure");
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper),"second side failure rolled back");
        // A write can silently ignore the caller. Detect it through read-back,
        // roll back BOTH entities, and report failure instead of fake success.
        bat.silentSkip = true;
        expectFailure(bat, creeper, "silent recipient skip");
        check(bat.read().equals(originalBat) && creeper.read().equals(originalCreeper),
            "silent skip was detected and rolled back");
        creeper.failureAt=creeper.writes+1;
        bat.breakRestore = true;
        try { CategoryExchange.exchange(bat,creeper,PORT); throw new AssertionError("Expected rollback error"); }
        catch (IllegalStateException expected) {
            check(expected.getSuppressed().length > 0, "incomplete rollback must be reported");
        }
        bat.breakRestore = false;
        bat.stats.clear(); bat.stats.putAll(originalBat.attributes());
        bat.abilities.clear(); bat.abilities.putAll(originalBat.behaviors());
        creeper.stats.clear(); creeper.stats.putAll(originalCreeper.attributes());
        creeper.abilities.clear(); creeper.abilities.putAll(originalCreeper.behaviors());
        try { CategoryExchange.exchange(bat,bat,PORT); throw new AssertionError("self exchange must fail"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { new CategoryBundle(Map.of("nan", Double.NaN), Map.of()); throw new AssertionError("non-finite input accepted"); }
        catch (IllegalArgumentException expected) { assertions++; }
        try { originalBat.attributes().put("hack", 5.0); throw new AssertionError("snapshot mutable"); }
        catch (UnsupportedOperationException expected) { assertions++; }
        // Seeded fuzzing: 500 complete unequal bundles with 0..8 values and 0..4 behaviors.
        java.util.Random random = new java.util.Random(0xFEE1DEADL);
        for (int iteration = 0; iteration < 500; iteration++) {
            Map<String, Double> leftStats = new java.util.HashMap<>(), rightStats = new java.util.HashMap<>();
            Map<String, String> leftAbilities = new java.util.HashMap<>(), rightAbilities = new java.util.HashMap<>();
            for (int i = 0, n = random.nextInt(9); i < n; i++) leftStats.put("a"+i, random.nextDouble()*100);
            for (int i = 0, n = random.nextInt(9); i < n; i++) rightStats.put("b"+i, random.nextDouble()*100);
            for (int i = 0, n = random.nextInt(5); i < n; i++) leftAbilities.put("a"+i, "trigger"+i);
            for (int i = 0, n = random.nextInt(5); i < n; i++) rightAbilities.put("b"+i, "trigger"+i);
            Mob x = new Mob(leftStats, leftAbilities), y = new Mob(rightStats, rightAbilities);
            CategoryBundle oldX = x.read(), oldY = y.read();
            CategoryExchange.exchange(x,y,PORT);
            check(x.read().equals(oldY) && y.read().equals(oldX),"seeded reciprocal round " + iteration);
            CategoryExchange.exchange(x,y,PORT);
            check(x.read().equals(oldX) && y.read().equals(oldY),"seeded reversal round " + iteration);
        }
        System.out.println("PASS CategoryExchangeSelfTest: " + assertions + " assertions; multi-value, unequal bundle, behavior swap, reverse, preflight, rollback, self swap, immutability, 500 seeded fuzz rounds");
    }
}
