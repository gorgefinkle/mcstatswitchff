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
import net.minecraft.world.level.block.Block;
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

    private record Snapshot(GoalSelectorState.Snapshot goals, GoalSelectorState.Snapshot targets) {}

    /**
     * A mob with a native inert Brain has its behavior in goal/target selectors.
     * Active Brain mobs use the separate Brain driver only when its full graph
     * can be transferred; never silently transfer only goals of a mixed AI.
     */
    public static boolean supports(Mob first, Mob second) {
        return first.getBrain().isBrainDead() && second.getBrain().isBrainDead();
    }

    private static Snapshot snapshot(Mob body) {
        return new Snapshot(GoalSelectorState.capture(body.goalSelector),
            GoalSelectorState.capture(body.targetSelector));
    }

    private static boolean identical(GoalSelector selector, GoalSelectorState.Snapshot expected) {
        return GoalSelectorState.matches(selector, expected);
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
                    // Exact immutable position classes only. A mutable
                    // BlockPos subclass would otherwise leak live AI state.
                    || value.getClass() == BlockPos.class
                    || value.getClass() == Vec3.class
                    || value instanceof Identifier || value instanceof TagKey<?>
                    || value instanceof Item || value instanceof Block;
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

        // Capture the entire TWO-selector graph in one traversal, not one
        // goal at a time. Nested references and shared helper instances must
        // retain their original alias relationships across all goals.
        GoalSelectorState.Snapshot[] sources = { donor.goals(), donor.targets() };
        int goalCount = sources[0].entries().size();
        Goal[] sourceRoots = new Goal[goalCount + sources[1].entries().size()];
        int index = 0;
        for (GoalSelectorState.Snapshot source : sources)
            for (GoalSelectorState.Entry entry : source.entries()) sourceRoots[index++] = entry.goal();

        // First translate only the known species-bound goal families whose
        // intent has a well-defined recipient-native implementation. Every
        // other goal still passes through the strict whole-graph rebind and
        // will reject if it cannot be made safe.
        Goal[] roots = sourceRoots.clone();
        IdentityHashMap<Goal, Goal> portedRoots = new IdentityHashMap<>();
        for (int i = 0; i < sourceRoots.length; i++) {
            Goal sourceGoal = sourceRoots[i];
            if (!portedRoots.containsKey(sourceGoal)) {
                Goal replacement = GoalTransferAdapters.adapt(sourceGoal, recipient);
                if (replacement != null) portedRoots.put(sourceGoal, replacement);
            }
            Goal replacement = portedRoots.get(sourceGoal);
            if (replacement != null) roots[i] = replacement;
        }
        Goal[] rebound = DynamicObjectGraph.capture(roots, Goal[].class, rebinder);

        IdentityHashMap<Goal, Goal> remapped = new IdentityHashMap<>();
        for (int i = 0; i < sourceRoots.length; i++) remapped.put(sourceRoots[i], rebound[i]);
        GoalSelectorState.Snapshot[] results = new GoalSelectorState.Snapshot[2];
        int offset = 0;
        for (int selectorIndex = 0; selectorIndex < sources.length; selectorIndex++) {
            GoalSelectorState.Snapshot source = sources[selectorIndex];
            List<GoalSelectorState.Entry> entries = new ArrayList<>(source.entries().size());
            for (GoalSelectorState.Entry entry : source.entries()) {
                boolean ported = portedRoots.containsKey(entry.goal());
                // A translated driver has a different implementation and
                // must run its own start() lifecycle. Do not restore the
                // donor wrapper's active bit around a freshly-created driver.
                entries.add(new GoalSelectorState.Entry(
                    entry.priority(), rebound[offset++], entry.running() && !ported));
            }
            java.util.EnumMap<Goal.Flag, Goal> locks = new java.util.EnumMap<>(Goal.Flag.class);
            for (Map.Entry<Goal.Flag, Goal> lock : source.lockedGoals().entrySet()) {
                if (!portedRoots.containsKey(lock.getValue()))
                    locks.put(lock.getKey(), remapped.get(lock.getValue()));
            }
            results[selectorIndex] = new GoalSelectorState.Snapshot(entries, source.disabledFlags(), locks);
        }
        return new Snapshot(results[0], results[1]);
    }

    private static void apply(Mob body, Snapshot incoming) {
        GoalSelectorState.install(body.goalSelector, incoming.goals());
        GoalSelectorState.install(body.targetSelector, incoming.targets());
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

        final Snapshot originalFirst, originalSecond;
        try {
            originalFirst = snapshot(first);
            originalSecond = snapshot(second);
        } catch (RuntimeException invalidScheduler) {
            return "Native goal scheduler state is inconsistent or inaccessible; no swap performed: "
                + invalidScheduler.getMessage();
        }
        if (originalFirst.goals().entries().isEmpty() && originalFirst.targets().entries().isEmpty())
            return "The first mob has no goal AI to capture.";
        if (originalSecond.goals().entries().isEmpty() && originalSecond.targets().entries().isEmpty())
            return "The second mob has no goal AI to capture.";

        final Snapshot firstReceiving, secondReceiving, firstRollback, secondRollback;
        try {
            firstReceiving = captureFor(second, first, originalSecond);
            secondReceiving = captureFor(first, second, originalFirst);
            // removeAllGoals calls stop() on active source goals. Keep independent
            // same-body copies so rollback restores the pre-exchange live state.
            firstRollback = captureFor(first, first, originalFirst);
            secondRollback = captureFor(second, second, originalSecond);
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
            try { apply(first, firstRollback); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            try { apply(second, secondRollback); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            if (failure.getSuppressed().length != 0)
                return "Dynamic AI exchange FAILED; rollback could not be verified: "
                    + failure.getMessage();
            return "Dynamic AI exchange rejected and original goals restored: "
                + failure.getMessage();
        }
    }
}
