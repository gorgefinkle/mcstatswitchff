package dev.freakyfriday.core;

import java.util.Set;

/** Deliberately tests disjoint schemas; no fake successful compatibility. */
public final class CategorySchemaSelfTest {
    private static int checks;
    private static void test(boolean ok, String label) {
        checks++; if (!ok) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        var equal = CategorySchema.compare(Set.of("gravity", "speed"), Set.of("speed", "gravity"));
        test(equal.compatible(), "matching schemas compatible");
        var unequal = CategorySchema.compare(Set.of("flight", "speed"), Set.of("detonation", "speed"));
        test(!unequal.compatible(), "disjoint schemas incompatible without functional adapter");
        test(unequal.onlyOnFirst().equals(Set.of("flight")), "first-only entry preserved");
        test(unequal.onlyOnSecond().equals(Set.of("detonation")), "second-only entry preserved");
        test(unequal.explain().contains("First entity only: flight"), "first-only properties explained");
        test(unequal.explain().contains("second entity only: detonation"), "second-only properties explained");
        var empty = CategorySchema.compare(Set.of(), Set.of());
        test(empty.compatible(), "empty schemas equal");
        test(CategorySchema.compare(Set.of("a"), Set.of()).onlyOnFirst().contains("a"), "single sided category detected");
        try { unequal.onlyOnFirst().add("oops"); throw new AssertionError("Mutable schema set"); }
        catch (UnsupportedOperationException expected) { checks++; }
        System.out.println("PASS CategorySchemaSelfTest: " + checks + " compatibility checks");
    }
}
