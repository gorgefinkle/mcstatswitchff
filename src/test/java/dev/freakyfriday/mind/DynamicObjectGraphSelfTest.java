package dev.freakyfriday.mind;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/** Real dynamic capture tests; this does NOT simulate fake mob-pair profiles. */
public final class DynamicObjectGraphSelfTest {
    private static int checks;
    private static void check(boolean valid, String message) {
        checks++;
        if (!valid) throw new AssertionError(message);
    }

    private enum Flag { MOVE, LOOK }
    private static class Body {
        final String name;
        final Navigation navigation;
        Body(String name) { this.name = name; this.navigation = new Navigation(this); }
    }
    private static final class SpecialBody extends Body {
        SpecialBody(String name) { super(name); }
    }
    private static final class Navigation {
        final Body owner;
        Navigation(Body owner) { this.owner = owner; }
    }
    private static class DynamicGoal {
        final Body mob;
        final Navigation navigation;
        final double speed;
        final EnumSet<Flag> flags;
        final List<Body> targets;
        int cooldown;
        DynamicGoal(Body mob, double speed) {
            this.mob = mob;
            this.navigation = mob.navigation;
            this.speed = speed;
            this.flags = EnumSet.of(Flag.MOVE, Flag.LOOK);
            this.targets = List.of(mob);
            this.cooldown = 37;
        }
    }
    private static final class SpecificGoal extends DynamicGoal {
        final SpecialBody requiresSpecificSubclass;
        SpecificGoal(SpecialBody mob) {
            super(mob, 2.0);
            requiresSpecificSubclass = mob;
        }
    }
    private static final class NestedGoal extends DynamicGoal {
        final DynamicGoal child;
        final DynamicGoal[] copy;
        final Map<String, Body> marks;
        NestedGoal(Body body) {
            super(body, 5.0);
            child = new DynamicGoal(body, 6.0);
            copy = new DynamicGoal[]{child};
            marks = Map.of("owner", body);
        }
    }
    private static final class ClosedGoal extends DynamicGoal {
        final Predicate<Body> predicate;
        ClosedGoal(Body body) {
            super(body, 3.0);
            predicate = item -> item == body; // Captures the original body.
        }
    }
    private static DynamicObjectGraph.Rebinding binding(Body from, Body to) {
        IdentityHashMap<Object, Object> replacements = new IdentityHashMap<>();
        replacements.put(from, to);
        replacements.put(from.navigation, to.navigation);
        return new DynamicObjectGraph.Rebinding() {
            public Object substitute(Object source) {
                return replacements.getOrDefault(source, source);
            }
            public boolean shared(Object source) { return false; }
            public boolean cloneable(Class<?> type) {
                return DynamicGoal.class.isAssignableFrom(type);
            }
        };
    }

    public static void main(String[] args) throws Exception {
        Body cow = new Body("cow"), pig = new Body("pig");
        DynamicGoal first = new DynamicGoal(cow, 1.25);
        var second = DynamicObjectGraph.capture(first, DynamicGoal.class, binding(cow, pig));
        check(second != first, "captured goal must be distinct");
        check(second.getClass() == first.getClass(), "runtime goal class preserved");
        check(second.mob == pig, "original entity rebound to recipient");
        check(second.navigation == pig.navigation, "original navigation rebound to recipient");
        check(second.speed == 1.25 && second.cooldown == 37,
            "all configuration and current state captured, not a species recipe");
        check(second.flags.equals(first.flags) && second.flags != first.flags,
            "goal scheduling flags copied without shared mutable state");
        check(second.targets.size() == 1 && second.targets.get(0) == pig,
            "container references rebound recursively");
        second.cooldown = 100;
        check(first.cooldown == 37, "captured goal state independent of donor");

        NestedGoal nested = new NestedGoal(cow);
        var cloned = DynamicObjectGraph.capture(nested, NestedGoal.class, binding(cow, pig));
        check(cloned.child != nested.child && cloned.child.mob == pig, "nested goal cloned");
        check(cloned.copy[0] == cloned.child, "alias identity preserved across arrays");
        check(cloned.marks.get("owner") == pig, "mapped members inside maps");
        check(nested.marks.get("owner") == cow, "donor graph unchanged");
        // Two native GoalSelector roots may share a helper object. One graph
        // traversal must retain that alias, not duplicate it per goal.
        DynamicGoal[] twoRoots = new DynamicGoal[]{nested, nested.child, nested.child};
        DynamicGoal[] clonedRoots =
            DynamicObjectGraph.capture(twoRoots, DynamicGoal[].class, binding(cow, pig));
        check(clonedRoots != twoRoots && clonedRoots[0] != nested,
            "complete multi-selector graph cloned once");
        check(((NestedGoal) clonedRoots[0]).child == clonedRoots[1]
                && clonedRoots[1] == clonedRoots[2],
            "shared state and repeated goal identities preserved across roots");

        var special = new SpecificGoal(new SpecialBody("original"));
        try {
            DynamicObjectGraph.capture(special, SpecificGoal.class,
                binding(special.requiresSpecificSubclass, pig));
            throw new AssertionError("cannot transplant species-specific goal");
        } catch (DynamicObjectGraph.Uncapturable expected) {
            check(expected.getMessage().contains("requiresSpecificSubclass"),
                "field-level diagnostics for incompatible subclass requirement");
        }

        try {
            DynamicObjectGraph.capture(new ClosedGoal(cow), ClosedGoal.class, binding(cow, pig));
            throw new AssertionError("captured predicate must not retain donor");
        } catch (DynamicObjectGraph.Uncapturable expected) {
            check(expected.getMessage().contains("lambda"),
                "reject owner-capturing opaque predicates instead of faking a swap");
        }

        check(first.mob == cow && nested.child.mob == cow,
            "rejected plans and successful plans never mutate source object graphs");
        System.out.println("PASS DynamicObjectGraphSelfTest: " + checks + " checks");
    }
}
