package dev.freakyfriday.core;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Live category membership is not permission to transfer an unsupported trait. */
public final class CategoryOffersSelfTest {
    private static int checks;
    private static void require(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        Map<String, Set<String>> observed = new LinkedHashMap<>();
        observed.put("mobility", new LinkedHashSet<>(Set.of("minecraft:movement_speed", "example:swim_speed")));
        observed.put("defense", Set.of());
        observed.put("signature", Set.of("minecraft:flight")); // unimplemented behaviors must never appear
        observed.put("modded", Set.of("example:unclassified"));
        Map<String, CategoryOffers.Offer> first = CategoryOffers.discover(observed);
        require(first.size() == 2, "only nonempty implemented categories are offered");
        require(!first.containsKey("signature"), "unsupported abilities must not be advertised");
        require(!first.containsKey("defense"), "empty category must not be advertised");
        require(first.get("mobility").memberCount() == 2, "actual observed members drive counts");
        require(first.get("modded").members().contains("example:unclassified"), "unclassified mod stat retained");
        try { first.clear(); throw new AssertionError("offers mutable"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { first.get("mobility").members().clear(); throw new AssertionError("members mutable"); }
        catch (UnsupportedOperationException expected) { checks++; }
        CategoryOffers.Offer second = new CategoryOffers.Offer(
            "mobility", "Mobility", Set.of("minecraft:movement_speed"));
        CategorySchema compatibility = CategoryOffers.compatibility(first.get("mobility"), second);
        require(!compatibility.compatible(), "asymmetric category cannot silently drop member");
        require(compatibility.onlyOnFirst().contains("example:swim_speed"),
            "missing member diagnosed precisely");
        require(CategoryOffers.compatibility(second,
            new CategoryOffers.Offer("mobility", "Mobility", Set.of("minecraft:movement_speed")))
            .compatible(), "matching complete category schemas compatible");
        try { CategoryOffers.compatibility(first.get("modded"), second); throw new AssertionError("different groups compared"); }
        catch (IllegalArgumentException expected) { checks++; }
        System.out.println("PASS CategoryOffersSelfTest: " + checks + " checks");
    }
}
