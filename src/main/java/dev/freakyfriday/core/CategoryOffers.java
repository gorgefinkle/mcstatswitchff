package dev.freakyfriday.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Server-derived category offers. An offer is DISCOVERY, not a guarantee that
 * an arbitrary future recipient can safely receive the category.
 *
 * This class is deliberately Minecraft-independent so the exact selection
 * semantics can be tested without client UI, network messages, or mocks.
 */
public final class CategoryOffers {
    private CategoryOffers() {}

    public record Offer(String categoryId, String label, Set<String> members) {
        public Offer {
            Objects.requireNonNull(categoryId, "categoryId");
            Objects.requireNonNull(label, "label");
            members = Collections.unmodifiableSet(new LinkedHashSet<>(
                Objects.requireNonNull(members, "members")));
            if (members.isEmpty()) throw new IllegalArgumentException("Cannot offer an empty category");
        }
        public int memberCount() { return members.size(); }
    }

    /**
     * Construct only categories actually populated on the selected entity.
     * Never advertise a purely planned category, such as signature abilities.
     */
    public static Map<String, Offer> discover(Map<String, ? extends Set<String>> observed) {
        Objects.requireNonNull(observed, "observed");
        Map<String, Offer> offers = new LinkedHashMap<>();
        for (var group : CategoryDefinitions.GROUPS.values()) {
            if (!CategoryDefinitions.numericCategories().contains(group.id())) continue;
            Set<String> members = observed.get(group.id());
            if (members == null || members.isEmpty()) continue;
            offers.put(group.id(), new Offer(group.id(), group.label(), members));
        }
        return Collections.unmodifiableMap(offers);
    }

    /** Dynamically check both entities' complete category schemas; never drop surplus entries. */
    public static CategorySchema compatibility(Offer first, Offer second) {
        Objects.requireNonNull(first, "first");
        Objects.requireNonNull(second, "second");
        if (!first.categoryId().equals(second.categoryId()))
            throw new IllegalArgumentException("Cannot compare different categories");
        return CategorySchema.compare(first.members(), second.members());
    }
}
