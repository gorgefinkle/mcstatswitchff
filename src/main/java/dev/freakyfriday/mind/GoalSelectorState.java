package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/**
 * Lossless snapshot/restore for the native GoalSelector scheduler.
 *
 * The three private Minecraft fields exposed by the project's access
 * transformer are deliberately limited to scheduler bookkeeping. Goal state
 * itself is captured by DynamicObjectGraph. A mismatch between wrappers,
 * running flags and control locks rejects the exchange before mutation.
 */
public final class GoalSelectorState {
    private GoalSelectorState() {}

    public record Entry(int priority, Goal goal, boolean running) {
        public Entry {
            if (goal == null) throw new IllegalArgumentException("Goal entry cannot be null");
        }
    }

    public record Snapshot(
            List<Entry> entries,
            EnumSet<Goal.Flag> disabledFlags,
            Map<Goal.Flag, Goal> lockedGoals) {
        public Snapshot {
            entries = List.copyOf(entries);
            disabledFlags = disabledFlags.clone();
            EnumMap<Goal.Flag, Goal> lockCopy = new EnumMap<>(Goal.Flag.class);
            lockCopy.putAll(lockedGoals);
            lockedGoals = Map.copyOf(lockCopy);
        }
    }

    public static Snapshot capture(GoalSelector selector) {
        List<Entry> entries = new ArrayList<>();
        IdentityHashMap<WrappedGoal, Boolean> wrappers = new IdentityHashMap<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            entries.add(new Entry(wrapped.getPriority(), wrapped.getGoal(), wrapped.isRunning()));
            wrappers.put(wrapped, Boolean.TRUE);
        }

        EnumMap<Goal.Flag, Goal> locks = new EnumMap<>(Goal.Flag.class);
        for (Map.Entry<Goal.Flag, WrappedGoal> lock : selector.lockedFlags.entrySet()) {
            WrappedGoal owner = lock.getValue();
            if (!wrappers.containsKey(owner) || !owner.isRunning()
                    || !owner.getFlags().contains(lock.getKey())) {
                throw new IllegalStateException("GoalSelector has an orphaned or inconsistent control lock: "
                    + lock.getKey());
            }
            locks.put(lock.getKey(), owner.getGoal());
        }

        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (!wrapped.isRunning()) continue;
            for (Goal.Flag flag : wrapped.getFlags()) {
                if (selector.lockedFlags.get(flag) != wrapped) {
                    throw new IllegalStateException("Running goal is missing its selector lock: " + flag);
                }
            }
        }

        return new Snapshot(entries, selector.disabledFlags, locks);
    }

    public static boolean matches(GoalSelector selector, Snapshot expected) {
        final Snapshot actual;
        try {
            actual = capture(selector);
        } catch (RuntimeException invalid) {
            return false;
        }
        if (!actual.disabledFlags().equals(expected.disabledFlags())
                || actual.entries().size() != expected.entries().size()
                || actual.lockedGoals().size() != expected.lockedGoals().size()) return false;

        List<Entry> remaining = new ArrayList<>(expected.entries());
        for (Entry actualEntry : actual.entries()) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                Entry expectedEntry = remaining.get(i);
                if (actualEntry.priority() == expectedEntry.priority()
                        && actualEntry.goal() == expectedEntry.goal()
                        && actualEntry.running() == expectedEntry.running()) {
                    remaining.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        if (!remaining.isEmpty()) return false;
        for (Map.Entry<Goal.Flag, Goal> lock : expected.lockedGoals().entrySet()) {
            if (actual.lockedGoals().get(lock.getKey()) != lock.getValue()) return false;
        }
        return true;
    }

    public static void install(GoalSelector selector, Snapshot incoming) {
        selector.removeAllGoals(goal -> true);
        for (Entry entry : incoming.entries()) selector.addGoal(entry.priority(), entry.goal());

        IdentityHashMap<Goal, WrappedGoal> byGoal = new IdentityHashMap<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals()) {
            if (byGoal.put(wrapped.getGoal(), wrapped) != null)
                throw new IllegalStateException("Selector installed the same Goal instance more than once");
        }

        // Restore disabled controls through the public API. The AT is needed
        // only for exact running-wrapper and lock-map restoration.
        for (Goal.Flag flag : Goal.Flag.values())
            selector.setControlFlag(flag, !incoming.disabledFlags().contains(flag));

        selector.lockedFlags.clear();
        for (Entry entry : incoming.entries()) {
            if (!entry.running()) continue;
            WrappedGoal wrapped = byGoal.get(entry.goal());
            if (wrapped == null) throw new IllegalStateException("Installed running goal wrapper is missing");
            wrapped.isRunning = true; // retain copied live Goal fields; do not call start() a second time
            for (Goal.Flag flag : wrapped.getFlags()) {
                WrappedGoal prior = selector.lockedFlags.put(flag, wrapped);
                if (prior != null)
                    throw new IllegalStateException("Two running goals claim the same control flag: " + flag);
            }
        }

        if (!matches(selector, incoming))
            throw new IllegalStateException("GoalSelector scheduler state did not restore exactly");
    }
}
