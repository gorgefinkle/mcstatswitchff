package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dynamic whole-goal AI capturer. No hardcoded species names, goal subclasses,
 * recipe tables, food lists or per-species pairing exceptions.
 *
 * Captures EVERY goal and targeting goal at the instant of selection, including
 * priority, concrete class and reflected object state. Each captured Goal is
 * reconstructed with ALL of its owner-bound services redirected to the new
 * recipient body before either native goal selector is touched.
 *
 * The capturer fails CLOSED when a field, opaque callback or recipient-specific
 * dependency cannot be rebound. It does not falsely promise that arbitrary
 * opaque AI hooks, customServerAiStep or mixed Brain+Goal mobs are portable.
 *
 * No persistence across unload/restart in this experimental checkpoint.
 */
public final class GoalMindRuntime {
    private GoalMindRuntime() {}

    private static final MindLedger OWNERS = new MindLedger();

    private record Slot(int priority, Goal goal) {}
    private record Snapshot(List<Slot> goals, List<Slot> targets) {
        Snapshot {
            goals = List.copyOf(goals);
            targets = List.copyOf(targets);
        }
    }

    /**
     * A mob with a native inert Brain has its behavior in goal/target selectors.
     * Active Brain mobs use the separate Brain driver only when its full graph
     * can be transferred; never silently transfer only goals of a mixed AI.
     */
    public static boolean supports(Mob first, Mob second) {
        return first.getBrain().isBrainDead() && second.getBrain().isBrainDead();
    }

    private static List<Slot> read(GoalSelector selector) {
        List<Slot> entries = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals())
            entries.add(new Slot(wrapped.getPriority(), wrapped.getGoal()));
        return List.copyOf(entries);
    }

    private static Snapshot snapshot(Mob body) {
        return new Snapshot(read(body.goalSelector), read(body.targetSelector));
    }

    /** Strict identity/priority readback; same classes alone are insufficient. */
    private static boolean identical(GoalSelector selector, List<Slot> expected) {
        var actual = selector.getAvailableGoals();
        if (actual.size() != expected.size()) return false;
        List<Slot> remaining = new ArrayList<>(expected);
        for (WrappedGoal wrapped : actual) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                Slot expectedSlot = remaining.get(i);
                if (expectedSlot.priority() == wrapped.getPriority()
                        && expectedSlot.goal() == wrapped.getGoal()) {
                    remaining.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }

    private static Snapshot captureFor(Mob original, Mob recipient, Snapshot donor)
            throws DynamicObjectGraph.Uncapturable {
        IdentityHashMap<Object, Object> bindings = new IdentityHashMap<>();
        bindings.put(original, recipient);
        bindings.put(original.getNavigation(), recipient.getNavigation());
        bindings.put(original.getMoveControl(), recipient.getMoveControl());
        bindings.put(original.getLookControl(), recipient.getLookControl());
        bindings.put(original.getJumpControl(), recipient.getJumpControl());
        bindings.put(original.getSensing(), recipient.getSensing());
        bindings.put(original.getRandom(), recipient.getRandom());

        DynamicObjectGraph.Rebinding rebinder = new DynamicObjectGraph.Rebinding() {
            @Override
            public Object substitute(Object value) {
                return bindings.containsKey(value) ? bindings.get(value) : value;
            }

            @Override
            public boolean shared(Object value) {
                // External entities are AI targets, not the source controller.
                // Levels, immutable positions and registry-owned values are
                // world-owned or immutable and never cloned.
                return value instanceof Entity || value instanceof Level
                    || value instanceof BlockPos || value instanceof Vec3
                    || value instanceof Identifier || value instanceof TagKey<?>
                    || value instanceof Item;
            }

            @Override
            public boolean cloneable(Class<?> type) {
                // All Goal subclasses (including modded goals) are discovered
                // without a whitelist. Auxiliary AI predicates/configurations
                // are inspected only when their internal graph is accessible.
                return Goal.class.isAssignableFrom(type)
                    || type.getName().startsWith("net.minecraft.world.entity.ai.targeting.");
            }
        };

        List<Slot> goals = new ArrayList<>(donor.goals().size());
        for (Slot entry : donor.goals()) {
            Goal rebound = DynamicObjectGraph.capture(entry.goal(), Goal.class, rebinder);
            goals.add(new Slot(entry.priority(), rebound));
        }

        List<Slot> targets = new ArrayList<>(donor.targets().size());
        for (Slot entry : donor.targets()) {
            Goal rebound = DynamicObjectGraph.capture(entry.goal(), Goal.class, rebinder);
            targets.add(new Slot(entry.priority(), rebound));
        }
        return new Snapshot(goals, targets);
    }

    private static void install(GoalSelector selector, List<Slot> entries) {
        selector.removeAllGoals(goal -> true);
        for (Slot slot : entries) selector.addGoal(slot.priority(), slot.goal());
        if (!identical(selector, entries))
            throw new IllegalStateException("Complete AI goal set did not install exactly");
    }

    private static void apply(Mob body, Snapshot incoming) {
        install(body.goalSelector, incoming.goals());
        install(body.targetSelector, incoming.targets());
    }

    /**
     * The ONLY goal-based exchange entry point. Source discovery and both
     * complete recipient captures are finished before any selector is changed.
     */
    public static synchronized String exchange(Mob first, Mob second) {
        if (first == second || !first.isAlive() || !second.isAlive()
                || first.level() != second.level())
            return "Two different living bodies in the same level are required.";
        if (!supports(first, second))
            return "The mob has active Brain AI beyond goal selectors; "
                + "a complete dynamic transfer needs the Brain runtime as well.";

        Snapshot originalFirst = snapshot(first);
        Snapshot originalSecond = snapshot(second);
        if (originalFirst.goals().isEmpty() && originalFirst.targets().isEmpty())
            return "The first mob has no goal AI to capture.";
        if (originalSecond.goals().isEmpty() && originalSecond.targets().isEmpty())
            return "The second mob has no goal AI to capture.";

        final Snapshot firstReceiving, secondReceiving;
        try {
            firstReceiving = captureFor(second, first, originalSecond);
            secondReceiving = captureFor(first, second, originalFirst);
        } catch (DynamicObjectGraph.Uncapturable failure) {
            return "Dynamic AI capture rejected the COMPLETE exchange: " + failure.getMessage();
        }

        UUID firstId = first.getUUID(), secondId = second.getUUID();
        try {
            if (!OWNERS.snapshot().containsKey(firstId))
                OWNERS.registerOriginal(firstId, new MindLedger.Mind(
                    firstId, MindLedger.Kind.NPC, first.getType().toString()));
            if (!OWNERS.snapshot().containsKey(secondId))
                OWNERS.registerOriginal(secondId, new MindLedger.Mind(
                    secondId, MindLedger.Kind.NPC, second.getType().toString()));
        } catch (Exception error) {
            return "Cannot register unique controller ownership: " + error.getMessage();
        }

        if (!identical(first.goalSelector, originalFirst.goals())
                || !identical(first.targetSelector, originalFirst.targets())
                || !identical(second.goalSelector, originalSecond.goals())
                || !identical(second.targetSelector, originalSecond.targets()))
            return "One mob's AI changed during capture; no swap performed.";

        try {
            apply(first, firstReceiving);
            apply(second, secondReceiving);
            OWNERS.exchange(firstId, secondId, (body, incoming) -> {
                if (incoming.kind() != MindLedger.Kind.NPC)
                    throw new IllegalArgumentException("Incoming mind is not a complete NPC controller");
            });
            return null;
        } catch (Exception failure) {
            // Goal selectors and the mind ledger have separate ownership.
            // The ledger rolls its own transaction back; restore BOTH native
            // selectors if either installation or the ledger fails.
            try { apply(first, originalFirst); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            try { apply(second, originalSecond); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            if (failure.getSuppressed().length != 0)
                return "Dynamic AI exchange FAILED; rollback could not be verified: "
                    + failure.getMessage();
            return "Dynamic AI exchange rejected and original goals restored: "
                + failure.getMessage();
        }
    }
}
