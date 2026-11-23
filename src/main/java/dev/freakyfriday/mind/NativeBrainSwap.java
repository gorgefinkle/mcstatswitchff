package dev.freakyfriday.mind;

import dev.freakyfriday.core.CategoryExchange;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;

/**
 * Experimental native-Brain exchange seam.
 *
 * The production book workflow is fail-closed until a complete reciprocal
 * native mind runtime exists. This class must not be mistaken for that
 * runtime: swapping Brain references alone is not a universal mind transfer.
 */
public final class NativeBrainSwap {
    private NativeBrainSwap() {}

    private static final MindLedger OWNERS = new MindLedger();

    private static String eligibility(Mob first, Mob second) {
        if (!first.isAlive() || !second.isAlive())
            return "Both bodies must be alive.";
        if (first.level() != second.level())
            return "Both bodies must be in the same loaded dimension.";
        if (first == second)
            return "A body cannot exchange its mind with itself.";
        if (first.getClass() != second.getClass() || first.getType() != second.getType())
            return "Whole-mind rebinding between different mob species is not implemented yet.";
        if (first.getBrain() == second.getBrain())
            return "Both bodies unexpectedly reference the same Brain; refusing duplicate ownership.";
        if (first.getBrain().isBrainDead() || second.getBrain().isBrainDead())
            return "These mobs use goal-driven AI rather than a transferable native Brain.";
        if (!first.goalSelector.getAvailableGoals().isEmpty()
                || !second.goalSelector.getAvailableGoals().isEmpty()
                || !first.targetSelector.getAvailableGoals().isEmpty()
                || !second.targetSelector.getAvailableGoals().isEmpty())
            return "Additional native goal/target AI exists outside the Brain; partial swap rejected.";
        return null;
    }

    /**
     * Development-only experimental path. The user-facing workflow must stay
     * fail-closed until mob↔mob and player↔mob exchanges are fully reciprocal.
     */
    public static synchronized String exchange(LivingEntity a, LivingEntity b) {
        if (!Boolean.getBoolean("freakyfriday.allowExperimentalPartialMindTransfer"))
            return "Universal reciprocal native mind transfer is not implemented yet; "
                + "no partial Brain/Goal swap was performed.";

        if (!(a instanceof Mob first) || !(b instanceof Mob second))
            return "Player control exchange is not implemented yet; choose two NPC mobs.";
        if (GoalMindRuntime.supports(first, second))
            return GoalMindRuntime.exchange(first, second);
        String unavailable = eligibility(first, second);
        if (unavailable != null) return unavailable;

        Brain<?> originalFirst = first.brain;
        Brain<?> originalSecond = second.brain;
        try {
            if (!OWNERS.snapshot().containsKey(first.getUUID())) {
                OWNERS.registerOriginal(first.getUUID(), new MindLedger.Mind(
                    first.getUUID(), MindLedger.Kind.NPC, first.getType().toString()));
            }
            if (!OWNERS.snapshot().containsKey(second.getUUID())) {
                OWNERS.registerOriginal(second.getUUID(), new MindLedger.Mind(
                    second.getUUID(), MindLedger.Kind.NPC, second.getType().toString()));
            }
            var port = new CategoryExchange.Port<Mob, Brain<?>>() {
                @Override public Brain<?> snapshot(Mob mob) { return mob.brain; }
                @Override public void validate(Mob recipient, Brain<?> incoming) {
                    if (incoming == null || incoming.isBrainDead())
                        throw new IllegalArgumentException("Unsupported incomplete native Brain");
                }
                @Override public void replace(Mob recipient, Brain<?> incoming) {
                    recipient.brain = incoming;
                }
                @Override public void restore(Mob recipient, Brain<?> original) {
                    recipient.brain = original;
                }
            };
            CategoryExchange.exchange(first, second, port);
            try {
                OWNERS.exchange(first.getUUID(), second.getUUID(), (body, mind) -> {
                    if (mind.kind() != MindLedger.Kind.NPC)
                        throw new IllegalArgumentException("Expected NPC mind");
                });
            } catch (Exception ownershipFailure) {
                first.brain = originalFirst;
                second.brain = originalSecond;
                if (first.brain != originalFirst || second.brain != originalSecond)
                    ownershipFailure.addSuppressed(new IllegalStateException("Native Brain rollback failed"));
                throw ownershipFailure;
            }
            return null;
        } catch (Exception failure) {
            String message = failure.getMessage();
            if (failure.getSuppressed().length > 0)
                return "Mind exchange failed and recovery needs inspection: "
                    + (message == null ? failure.getClass().getSimpleName() : message);
            return "Mind exchange rejected: "
                + (message == null ? failure.getClass().getSimpleName() : message);
        }
    }
}
