package dev.freakyfriday.mind;

import dev.freakyfriday.core.CategoryExchange;
import java.util.Set;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;

/**
 * First REAL Minecraft-facing whole-mind driver: atomically exchanges the
 * complete native Brain object of two compatible Brain-driven mobs.
 *
 * Intentionally narrow:
 *  - identical concrete mob classes and entity types;
 *  - both Brains are active;
 *  - neither mob has any native GoalSelector or targetSelector entries.
 *
 * The latter guarantees we do not swap "just part" of a goal-driven mob's AI.
 * Rebinding arbitrary cross-species Goals is unsafe because Goal instances
 * often capture their constructor's original entity, and many behaviors
 * require subclass-specific native methods. Those pairings are rejected.
 *
 * This R0.13 implementation is currently IN-MEMORY ONLY. It is not a
 * guaranteed persistent or universal whole-mind exchange.
 */
public final class NativeBrainSwap {
    private NativeBrainSwap() {}

    private static final MindLedger OWNERS = new MindLedger();

    /** Return null if the COMPLETE native Brain is safe to move right now. */
    private static String eligibility(Mob first, Mob second) {
        if (!first.isAlive() || !second.isAlive())
            return "Both bodies must be alive.";
        if (first.level() != second.level())
            return "Both bodies must be in the same loaded dimension.";
        if (first == second)
            return "A body cannot exchange its mind with itself.";
        if (first.getClass() != second.getClass() || first.getType() != second.getType())
            return "Whole-mind rebinding between different mob species is not implemented yet. "
                + "This runtime currently supports two mobs of the same concrete type with Brain-driven AI.";
        if (first.getBrain() == second.getBrain())
            return "Both bodies unexpectedly reference the same Brain; refusing duplicate ownership.";
        if (first.getBrain().isBrainDead() || second.getBrain().isBrainDead())
            return "These mobs use a goal-driven AI rather than a transferable native Brain. "
                + "Copying their Goal instances would still control the original bodies.";
        if (!first.goalSelector.getAvailableGoals().isEmpty()
                || !second.goalSelector.getAvailableGoals().isEmpty()
                || !first.targetSelector.getAvailableGoals().isEmpty()
                || !second.targetSelector.getAvailableGoals().isEmpty())
            return "The mob has additional native goal/target AI outside its Brain; "
                + "a partial swap would violate the whole-mind requirement.";
        return null;
    }

    /**
     * Called from a server-side interaction, never a packet-supplied entity ID.
     * Returns null ONLY after both Brain references and mind identities exchange.
     */
    public static synchronized String exchange(LivingEntity a, LivingEntity b) {
        if (!(a instanceof Mob first) || !(b instanceof Mob second))
            return "Player control exchange is not implemented yet; choose two NPC mobs.";
        // Complete, recipient-bound goal packages are used where available.
        // Do not copy Goal references: those are bound to their original mob.
        // This is an entire native AI recipe per species, not a per-goal UI.
        if (GoalMindRuntime.supports(first, second))
            return GoalMindRuntime.exchange(first, second);
        String unavailable = eligibility(first, second);
        if (unavailable != null) return unavailable;

        // Each native Brain owns its entire memory/sensor/behavior package.
        // Do NOT swap individual Goal, memory or attribute members here.
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
                // Move the entire logical mind identity in the same direction.
                OWNERS.exchange(first.getUUID(), second.getUUID(), (body, mind) -> {
                    if (mind.kind() != MindLedger.Kind.NPC)
                        throw new IllegalArgumentException("Expected NPC mind");
                });
            } catch (Exception ownershipFailure) {
                // The native runtime swap already succeeded; undo it if the
                // ledger cannot commit. Verify both sides before reporting.
                first.brain = originalFirst;
                second.brain = originalSecond;
                if (first.brain != originalFirst || second.brain != originalSecond)
                    ownershipFailure.addSuppressed(new IllegalStateException("Native Brain rollback failed"));
                throw ownershipFailure;
            }
            return null;
        } catch (Exception failure) {
            // The CategoryExchange port already attempts a two-sided rollback.
            // Never report success when identity and native runtime disagree.
            String message = failure.getMessage();
            if (failure.getSuppressed().length > 0)
                return "Mind exchange failed and recovery needs inspection: "
                    + (message == null ? failure.getClass().getSimpleName() : message);
            return "Mind exchange rejected: "
                + (message == null ? failure.getClass().getSimpleName() : message);
        }
    }
}
