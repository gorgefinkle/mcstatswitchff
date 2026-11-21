package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.pig.Pig;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Whole goal-driven mind profiles rebound to a different animal BODY.
 *
 * The unit of transfer is one entire donor goal package. The factory must
 * reproduce EVERY native goal, including duplicate-priority entries. An
 * unrecognised goal (including mod-added goals) makes the entire transaction
 * unavailable; it must not be silently dropped.
 *
 * This first tested path is cow <-> pig. Both are Animals with compatible
 * locomotion, look, breeding and temptation primitives. Vanilla body-only
 * mechanics (milking, saddles, lightning conversion) stay with the body.
 *
 * This is NOT universal AI compatibility or persistent controller state.
 * Goal timers and targets are reinitialised when a package is instantiated;
 * donor goal-object instances cannot safely be moved across species because
 * they retain a reference to their original owner.
 */
public final class GoalMindRuntime {
    private GoalMindRuntime() {}

    private static final MindLedger OWNERS = new MindLedger();

    /** Exact class + priority is important; the pig has TWO priority-4 tempt goals. */
    private record GoalEntry(int priority, Goal goal) {}

    private enum Profile {
        COW, PIG
    }

    public static boolean supports(Mob first, Mob second) {
        return nativeProfile(first) != null && nativeProfile(second) != null;
    }

    private static Profile nativeProfile(Mob entity) {
        // No subclass compatibility assumption: Mooshrooms have different
        // body behavior; they are excluded until explicitly audited.
        if (entity.getClass() == net.minecraft.world.entity.animal.cow.Cow.class) return Profile.COW;
        if (entity.getClass() == Pig.class) return Profile.PIG;
        return null;
    }

    private static List<GoalEntry> build(Profile profile, Animal recipient) {
        List<GoalEntry> result = new ArrayList<>();
        result.add(new GoalEntry(0, new FloatGoal(recipient)));
        if (profile == Profile.COW) {
            result.add(new GoalEntry(1, new PanicGoal(recipient, 2.0)));
            result.add(new GoalEntry(2, new BreedGoal(recipient, 1.0)));
            result.add(new GoalEntry(3, new TemptGoal(recipient, 1.25,
                stack -> stack.is(ItemTags.COW_FOOD), false)));
            result.add(new GoalEntry(4, new FollowParentGoal(recipient, 1.25)));
            result.add(new GoalEntry(5, new WaterAvoidingRandomStrollGoal(recipient, 1.0)));
            result.add(new GoalEntry(6, new LookAtPlayerGoal(recipient, Player.class, 6.0f)));
            result.add(new GoalEntry(7, new RandomLookAroundGoal(recipient)));
        } else {
            result.add(new GoalEntry(1, new PanicGoal(recipient, 1.25)));
            result.add(new GoalEntry(3, new BreedGoal(recipient, 1.0)));
            result.add(new GoalEntry(4, new TemptGoal(recipient, 1.2,
                stack -> stack.is(Items.CARROT_ON_A_STICK), false)));
            result.add(new GoalEntry(4, new TemptGoal(recipient, 1.2,
                stack -> stack.is(ItemTags.PIG_FOOD), false)));
            result.add(new GoalEntry(5, new FollowParentGoal(recipient, 1.1)));
            result.add(new GoalEntry(6, new WaterAvoidingRandomStrollGoal(recipient, 1.0)));
            result.add(new GoalEntry(7, new LookAtPlayerGoal(recipient, Player.class, 6.0f)));
            result.add(new GoalEntry(8, new RandomLookAroundGoal(recipient)));
        }
        return List.copyOf(result);
    }

    private static List<GoalEntry> current(Mob body) {
        List<GoalEntry> result = new ArrayList<>();
        for (WrappedGoal wrapped : body.getGoalSelector().getAvailableGoals()) {
            result.add(new GoalEntry(wrapped.getPriority(), wrapped.getGoal()));
        }
        return result;
    }

    private static List<String> signature(List<GoalEntry> entries) {
        List<String> result = new ArrayList<>(entries.size());
        for (GoalEntry e : entries) {
            result.add(e.priority + ":" + e.goal.getClass().getName());
        }
        Collections.sort(result);
        return List.copyOf(result);
    }

    private static void apply(Mob body, List<GoalEntry> incoming) {
        body.getGoalSelector().removeAllGoals(goal -> true);
        for (GoalEntry entry : incoming) body.getGoalSelector().addGoal(entry.priority, entry.goal);
    }

    private static void restore(Mob body, List<GoalEntry> original) {
        apply(body, original);
        if (!signature(current(body)).equals(signature(original))) {
            throw new IllegalStateException("Could not restore original goal package");
        }
    }

    public static synchronized String exchange(Mob first, Mob second) {
        if (!supports(first, second))
            return "Cross-species goal adapter not available for this pair yet.";
        if (!first.isAlive() || !second.isAlive() || first.level() != second.level() || first == second)
            return "Both different bodies must be alive in the same loaded dimension.";
        if (!(first instanceof Animal firstAnimal) || !(second instanceof Animal secondAnimal))
            return "These bodies are not supported animal AI recipients.";
        if (!first.targetSelector.getAvailableGoals().isEmpty()
                || !second.targetSelector.getAvailableGoals().isEmpty())
            return "One of these mobs has unportable targeting goals; whole mind swap rejected.";

        UUID a = first.getUUID(), b = second.getUUID();
        Profile nativeA = nativeProfile(first), nativeB = nativeProfile(second);
        Profile mindA = nativeA, mindB = nativeB;
        try {
            if (!OWNERS.snapshot().containsKey(a)) {
                OWNERS.registerOriginal(a, new MindLedger.Mind(a, MindLedger.Kind.NPC, nativeA.name()));
            }
            if (!OWNERS.snapshot().containsKey(b)) {
                OWNERS.registerOriginal(b, new MindLedger.Mind(b, MindLedger.Kind.NPC, nativeB.name()));
            }
            mindA = Profile.valueOf(OWNERS.controlling(a).nativeProfile());
            mindB = Profile.valueOf(OWNERS.controlling(b).nativeProfile());
        } catch (Exception error) {
            return "Cannot establish unique mind ownership: " + error.getMessage();
        }

        // Never replace just the goals we know. Match *all* currently present
        // priorities and classes to the exact complete package we claim to own.
        List<GoalEntry> oldA = current(first), oldB = current(second);
        List<GoalEntry> expectedA = build(mindA, firstAnimal);
        List<GoalEntry> expectedB = build(mindB, secondAnimal);
        if (!signature(oldA).equals(signature(expectedA))
                || !signature(oldB).equals(signature(expectedB)))
            return "A body has extra, missing or modified AI goals. "
                + "Entire mind exchange rejected rather than dropping them.";

        List<GoalEntry> newA = build(mindB, firstAnimal);
        List<GoalEntry> newB = build(mindA, secondAnimal);
        try {
            apply(first, newA);
            apply(second, newB);
            if (!signature(current(first)).equals(signature(newA))
                    || !signature(current(second)).equals(signature(newB)))
                throw new IllegalStateException("Recipient goal package did not install completely");
            OWNERS.exchange(a, b, (recipient, incoming) -> {
                if (incoming.kind() != MindLedger.Kind.NPC)
                    throw new IllegalArgumentException("Expected entire NPC mind");
            });
            return null;
        } catch (Exception error) {
            try { restore(first, oldA); }
            catch (Exception rollback) { error.addSuppressed(rollback); }
            try { restore(second, oldB); }
            catch (Exception rollback) { error.addSuppressed(rollback); }
            if (error.getSuppressed().length > 0)
                return "Whole-mind exchange FAILED; could not verify rollback: " + error.getMessage();
            return "Whole-mind exchange rejected and restored: " + error.getMessage();
        }
    }
}
