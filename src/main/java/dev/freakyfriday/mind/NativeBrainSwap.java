package dev.freakyfriday.mind;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * Production gate for native NPC mind exchange.
 *
 * Brain, GoalSelector, and target-selector references are only parts of a
 * living controller. Rebinding any subset can silently break native AI,
 * especially across entity classes. Keep every entry point fail-closed until
 * the complete reciprocal runtime can transfer and validate all controller
 * state and world side effects.
 */
public final class NativeBrainSwap {
    private NativeBrainSwap() {}

    public static String exchange(LivingEntity first, LivingEntity second) {
        if (first == null || second == null)
            return "Both physical bodies are required.";
        if (first == second)
            return "A body cannot exchange its mind with itself.";
        if (!first.isAlive() || !second.isAlive())
            return "Both bodies must be alive.";
        if (first.level() != second.level())
            return "Both bodies must be in the same loaded dimension.";
        if (!(first instanceof Mob) || !(second instanceof Mob))
            return "The reciprocal player/mob controller runtime is not implemented.";
        return "Universal reciprocal native mind transfer is not implemented; "
            + "no Brain, Goal, or target-selector state was changed.";
    }
}
