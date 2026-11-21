package dev.freakyfriday.mind;

import dev.freakyfriday.FreakyFriday;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * One book, two bodies, no category choice. Everything is resolved on the
 * server; the client never provides a target UUID in a command.
 *
 * A pair is committed only if the whole-mind runtime validates and activates
 * both recipient Brains. Unsupported pairings receive an explicit diagnostic.
 */
public final class MindSwapSessions {
    private MindSwapSessions() {}

    private record Pending(UUID firstBody, String dimension, long startedNanos) {}
    private record Stamp(UUID target, long tick) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Stamp> LAST_INTERACTION = new ConcurrentHashMap<>();
    private static final long TIMEOUT_NANOS = 120_000_000_000L;

    private static boolean hasBook(ServerPlayer p) {
        return p.getMainHandItem().is(FreakyFriday.BOOK.get())
            || p.getOffhandItem().is(FreakyFriday.BOOK.get());
    }

    private static void message(ServerPlayer p, String text) {
        p.sendSystemMessage(Component.literal("[Freaky Friday] " + text));
    }

    private static Pending current(ServerPlayer p) {
        Pending pending = PENDING.get(p.getUUID());
        if (pending != null && (!pending.dimension.equals(p.level().dimension().identifier().toString())
                || System.nanoTime() - pending.startedNanos > TIMEOUT_NANOS)) {
            PENDING.remove(p.getUUID());
            return null;
        }
        return pending;
    }

    public static void clearOnLogout(UUID playerId) {
        PENDING.remove(playerId);
        LAST_INTERACTION.remove(playerId);
    }

    public static void cancel(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        message(p, "Mind swap selection cancelled.");
    }

    /**
     * Passive diagnostic for arbitrary vanilla or modded mobs. No AI is
     * ticked, no goals are mutated and no hidden entity is instantiated.
     * A snapshot cannot certify world-effect isolation.
     */
    public static void inspect(ServerPlayer p) {
        if (!hasBook(p)) {
            message(p, "Hold the Freaky Friday book first.");
            return;
        }
        Pending pending = current(p);
        if (pending == null) {
            message(p, "Select a living mob with the book before inspecting its AI.");
            return;
        }
        Entity selected = p.level().getEntity(pending.firstBody());
        if (!(selected instanceof LivingEntity living) || !living.isAlive()) {
            message(p, "Selected mob is no longer loaded or alive.");
            return;
        }
        try {
            for (String line : NativeAiSurfaceAudit.inspect(living).summary())
                message(p, line);
        } catch (IllegalArgumentException problem) {
            message(p, problem.getMessage());
        }
    }

    /**
     * This diagnostic uses a synthetic isolated source. It executes look/path
     * commands on the SELECTED Mob via the real body port; no donor AI ticks.
     */
    public static void hostProbe(ServerPlayer p) {
        if (!hasBook(p)) {
            message(p, "Hold the Freaky Friday book to use the host probe.");
            return;
        }
        Pending pending = current(p);
        if (pending == null) {
            message(p, "Select one mob with the book before running the host probe.");
            return;
        }
        Entity selected = p.level().getEntity(pending.firstBody());
        if (!(selected instanceof net.minecraft.world.entity.Mob mob)) {
            message(p, "Host probe requires a loaded NPC Mob, not a player.");
            return;
        }
        message(p, MinecraftHostProbe.run(p, mob));
    }

    public static void selectFromInteraction(ServerPlayer p, LivingEntity target) {
        if (!hasBook(p)) return;
        Stamp stamp = new Stamp(target.getUUID(), p.level().getGameTime());
        if (stamp.equals(LAST_INTERACTION.put(p.getUUID(), stamp))) return;
        select(p, target);
    }

    /** Both right clicks and Ctrl+S (self) use the exact same path. */
    public static void select(ServerPlayer p, LivingEntity target) {
        if (!hasBook(p)) {
            message(p, "Hold the Freaky Friday book first.");
            return;
        }
        if (!target.isAlive()) {
            message(p, "Only living, active bodies can be selected.");
            return;
        }
        if (p.level() != target.level()) {
            message(p, "Both bodies must be in the same dimension.");
            return;
        }
        Pending pending = current(p);
        if (pending == null) {
            PENDING.put(p.getUUID(), new Pending(target.getUUID(),
                p.level().dimension().identifier().toString(), System.nanoTime()));
            message(p, "First body: " + target.getDisplayName().getString()
                + ". Choose the SECOND body to swap complete minds. No categories.");
            return;
        }
        Entity original = p.level().getEntity(pending.firstBody());
        if (!(original instanceof LivingEntity first) || !first.isAlive()) {
            PENDING.remove(p.getUUID());
            message(p, "First body unloaded or died. Selection cleared.");
            return;
        }
        if (first == target) {
            message(p, "Choose a DIFFERENT second body.");
            return;
        }
        // R0.14 deliberately does NOT call the older R0.13 reflective
        // Goal/Brain transplant. Until we can isolate native donor AI, that
        // would be a different (unsafe/unproven) engine and could appear to
        // succeed without actually exchanging full controllers.
        message(p, "R0.14 hybrid native mind runner is not activated yet: NO swap "
            + "performed between " + first.getDisplayName().getString()
            + " and " + target.getDisplayName().getString()
            + ". Use /freakyfriday inspect or /freakyfriday hostprobe to "
            + "audit the selected first body, or /freakyfriday cancel.");
    }
}
