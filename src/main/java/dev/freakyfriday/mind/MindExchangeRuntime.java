package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/** One authoritative transaction entry point for the book selection flow. */
public final class MindExchangeRuntime {
    private MindExchangeRuntime() {}

    /** Returns null only after the whole controller/body assignment is committed. */
    public static String exchange(LivingEntity first, LivingEntity second) {
        if (first == null || second == null) return "Choose two living bodies.";
        if (first == second) return "A body cannot exchange its mind with itself.";
        if (!first.isAlive() || !second.isAlive()) return "Both bodies must be alive.";
        if (first.level() != second.level()) return "Both bodies must be in the same dimension.";

        if (first instanceof ServerPlayer player && second instanceof Mob npc) {
            try {
                PlayerPossessionState.begin(player, npc);
                return null;
            } catch (RuntimeException failure) {
                return "NPC-to-player possession could not be committed: " + safeMessage(failure);
            }
        }
        if (second instanceof ServerPlayer player && first instanceof Mob npc) {
            try {
                PlayerPossessionState.begin(player, npc);
                return null;
            } catch (RuntimeException failure) {
                return "NPC-to-player possession could not be committed: " + safeMessage(failure);
            }
        }
        if (first instanceof Mob firstMob && second instanceof Mob secondMob) {
            try {
                MindHostRegistry.exchangeMobControllers(firstMob, secondMob);
                return null;
            } catch (RuntimeException failure) {
                return "Controller exchange could not be committed: " + safeMessage(failure);
            }
        }

        return "This pairing is not a Mob-to-Mob or NPC-to-authenticated-Player exchange.";
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : message;
    }
}
