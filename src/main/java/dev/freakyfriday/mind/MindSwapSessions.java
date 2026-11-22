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
 * R0.15.3 records a LOCKED diagnostic pair only. This experimental
 * branch does not execute donor-native AI or commit a real mind swap.
 * The book is still one select-first/select-second interaction sequence.
 */
public final class MindSwapSessions {
    private MindSwapSessions() {}

    private record Pending(UUID firstBody, String dimension, long startedNanos) {}
    /** Last selected two-body candidate is a diagnostic, NOT a committed swap. */
    private record Pair(UUID donorBody, UUID recipientBody, String dimension, long createdNanos) {}
    private static final Map<UUID, Pair> PAIRS = new ConcurrentHashMap<>();
    private record Stamp(UUID target, long tick) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Stamp> LAST_INTERACTION = new ConcurrentHashMap<>();
    private static final long TIMEOUT_NANOS = 120_000_000_000L;

    private static boolean hasBook(ServerPlayer p) {
        return p.getMainHandItem().is(FreakyFriday.BOOK.get())
            || p.getOffhandItem().is(FreakyFriday.BOOK.get());
    }

    private static void message(ServerPlayer p, String text) {
        // Command and selection diagnostics must survive chat history clearing.
        // Full per-tick results have their own correlated trace identifiers.
        ProbeTelemetry.info("selection", "message", "%s", text);
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
        PAIRS.remove(playerId);
    }

    public static void cancel(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        PAIRS.remove(p.getUUID());
        // Without clearing this stamp a cancel + immediate reselect of
        // the same mob on the same game tick is silently discarded.
        LAST_INTERACTION.remove(p.getUUID());
        message(p, "Mind swap selection cancelled.");
    }

    /** No static selection state may leak into a newly launched server. */
    public static void clearOnServerStop() {
        PENDING.clear();
        PAIRS.clear();
        LAST_INTERACTION.clear();
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

    /** Runs a bounded, real-Minecraft action uptake trace for the selected body. */
    public static void timedHostProbe(ServerPlayer p) {
        if (!hasBook(p)) {
            message(p, "Hold the Freaky Friday book for the timed host probe.");
            return;
        }
        Pending pending = current(p);
        if (pending == null) {
            message(p, "Select a living Mob with the book before /freakyfriday hostprobe40.");
            return;
        }
        Entity selected = p.level().getEntity(pending.firstBody());
        if (!(selected instanceof net.minecraft.world.entity.Mob mob)) {
            message(p, "The selected body is not a loaded NPC Mob.");
            return;
        }
        message(p, LiveBodyProbe.start(p, mob));
    }

    /**
     * Explicit diagnostic for the TWO selected bodies. The donor factory is
     * called once to construct a detached host candidate. It is never added
     * to the level or ticked; the real recipient body is never replaced.
     *
     * Construction/position methods of third-party mobs may still have
     * side effects, so this experiment belongs in a disposable world.
     */
    public static void dualInstanceProbe(ServerPlayer p) {
        if (!hasBook(p)) {
            message(p, "Hold the Freaky Friday book for the dual-instance probe.");
            return;
        }
        Pair pair = PAIRS.get(p.getUUID());
        if (pair == null) {
            message(p, "Select TWO different NPC mobs with the book before /freakyfriday dualprobe.");
            return;
        }
        if (!pair.dimension.equals(p.level().dimension().identifier().toString())
                || System.nanoTime() - pair.createdNanos > TIMEOUT_NANOS) {
            PAIRS.remove(p.getUUID());
            message(p, "The two-body probe selection expired or changed dimension.");
            return;
        }
        Entity source = p.level().getEntity(pair.donorBody());
        Entity target = p.level().getEntity(pair.recipientBody());
        if (!(source instanceof net.minecraft.world.entity.Mob donor)
                || !(target instanceof net.minecraft.world.entity.Mob recipient)
                || !donor.isAlive() || !recipient.isAlive()) {
            PAIRS.remove(p.getUUID());
            message(p, "Both probe bodies must still be loaded, alive NPC mobs.");
            return;
        }
        message(p, DetachedMindHostProbe.inspect(donor, recipient));
    }

    /** Genuine 40-tick dual-instance LIFECYCLE test; the ghost AI is never ticked. */
    public static void timedDualInstanceProbe(ServerPlayer p) {
        if (!hasBook(p)) {
            message(p, "Hold the Freaky Friday book for the detached-host mirror probe.");
            return;
        }
        Pair pair = PAIRS.get(p.getUUID());
        if (pair == null) {
            message(p, "Select two different NPC mobs before /freakyfriday dualprobe40.");
            return;
        }
        if (!pair.dimension.equals(p.level().dimension().identifier().toString())
                || System.nanoTime() - pair.createdNanos > TIMEOUT_NANOS) {
            PAIRS.remove(p.getUUID());
            message(p, "The donor/recipient selection expired or changed dimension.");
            return;
        }
        Entity first = p.level().getEntity(pair.donorBody());
        Entity second = p.level().getEntity(pair.recipientBody());
        if (!(first instanceof net.minecraft.world.entity.Mob donor)
                || !(second instanceof net.minecraft.world.entity.Mob recipient)
                || !donor.isAlive() || !recipient.isAlive()) {
            PAIRS.remove(p.getUUID());
            message(p, "Both pair members must be loaded, living NPC mobs.");
            return;
        }
        message(p, DetachedHostMirrorProbe.start(p, donor, recipient));
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
        if (target instanceof ServerPlayer) {
            message(p, "Player mind transfer is not implemented in the R0.15.4 detached NPC-host test. "
                + "Select two NPC mobs, or use /freakyfriday cancel to start over.");
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
        // Once a two-body diagnostic pair is selected, further right-clicks
        // must NEVER silently replace its recipient. The R0.15.2 real-world
        // test exposed that exact problem (cow -> zombie overwritten by
        // subsequent clicks, resulting in an unintended cow -> cow probe).
        Pair locked = PAIRS.get(p.getUUID());
        if (locked != null) {
            if (locked.dimension().equals(p.level().dimension().identifier().toString())
                    && System.nanoTime() - locked.createdNanos() <= TIMEOUT_NANOS) {
                message(p, "Diagnostic pair is LOCKED. Further clicks cannot change it. "
                    + "Run /freakyfriday dualprobe or dualprobe40; "
                    + "use /freakyfriday cancel to choose a new pair.");
                return;
            }
            PAIRS.remove(p.getUUID(), locked);
            PENDING.remove(p.getUUID());
            ProbeTelemetry.info("selection", "pair_expired",
                "Removed expired/cross-dimension locked pair; new selection begins");
        }

        Pending pending = current(p);
        if (pending == null) {
            PAIRS.remove(p.getUUID());
            PENDING.put(p.getUUID(), new Pending(target.getUUID(),
                p.level().dimension().identifier().toString(), System.nanoTime()));
            message(p, "First body: " + target.getDisplayName().getString()
                + ". Choose the SECOND body for dual-instance host testing. No mind swap yet.");
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
        PAIRS.put(p.getUUID(), new Pair(first.getUUID(), target.getUUID(),
            p.level().dimension().identifier().toString(), System.nanoTime()));
        ProbeTelemetry.info("selection", "pair_selected",
            "donorType=%s donorId=%s donorClass=%s recipientType=%s recipientId=%s recipientClass=%s dimension=%s",
            first.getType(), first.getUUID(), first.getClass().getName(),
            target.getType(), target.getUUID(), target.getClass().getName(),
            p.level().dimension().identifier());
        message(p, "R0.15 dual-instance host experiment: NO swap performed between "
            + first.getDisplayName().getString()
            + " and " + target.getDisplayName().getString()
            + ". Pair LOCKED for /freakyfriday dualprobe and dualprobe40. "
            + "Further book clicks cannot replace either body. "
            + "Use /freakyfriday cancel before selecting another pair. "
            + "Host AI is NEVER ticked; test only in a disposable world.");
    }
}
