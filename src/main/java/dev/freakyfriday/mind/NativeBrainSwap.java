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
        if(first==null||second==null)
            return "Both physical bodies are required.";
        if(first==second||!first.isAlive()||!second.isAlive()||first.level()!=second.level())
            return "Two different living bodies in the same level are required.";
        if(!(first instanceof Mob firstMob)||!(second instanceof Mob secondMob))
            return "The current native controller rebinder supports Mob-to-Mob pairs only.";
        return GoalMindRuntime.exchange(firstMob,secondMob);
    }
}
