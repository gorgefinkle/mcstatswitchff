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
 * The book uses one select-first/select-second interaction sequence.
 * Player + Mob and Mob + Mob both require the same complete reciprocal
 * mind runtime. Until that exists, both routes reject without mutating either
 * controller. Player + Player remains a separate, supported feature.
 * Diagnostic-only pair selection is retained solely for old developer tests.
 */
public final class MindSwapSessions {
    private MindSwapSessions() {}

    private record Pending(UUID firstBody, String dimension, long startedTick) {}
    /** Last selected two-body candidate is a diagnostic, NOT a committed swap. */
    private record Pair(UUID donorBody, UUID recipientBody, String dimension, long createdNanos) {}
    private static final Map<UUID, Pair> PAIRS = new ConcurrentHashMap<>();
    private record Stamp(UUID target, long tick) {}
    private static final Map<UUID, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<UUID, Stamp> LAST_INTERACTION = new ConcurrentHashMap<>();
    private static final long TIMEOUT_NANOS = 120_000_000_000L;
    public static final long SELECTION_TIMEOUT_TICKS = 160L; // 8 seconds

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
                || p.level().getGameTime() < pending.startedTick()
                || p.level().getGameTime()-pending.startedTick() >= SELECTION_TIMEOUT_TICKS)) {
            PENDING.remove(p.getUUID());
            SelectionHudSync.clear(p);
            return null;
        }
        return pending;
    }

    /** Remaining first-body selection lifetime; read-only for server GameTests. */
    public static int selectionRemainingTicks(ServerPlayer p) {
        Pending active=current(p);
        if(active==null)return 0;
        return (int)Math.max(0,SELECTION_TIMEOUT_TICKS-
            (p.level().getGameTime()-active.startedTick()));
    }

    /** Called by SelectionHudSync each server tick to close an unused selection. */
    public static void expirePending(ServerPlayer p) {
        Pending active=current(p);
        if(active==null)return;
        if(!p.isAlive()) {
            PENDING.remove(p.getUUID());
            SelectionHudSync.clear(p);
            return;
        }
        Entity first=p.level().getEntity(active.firstBody());
        if(!(first instanceof LivingEntity living)||!living.isAlive()) {
            PENDING.remove(p.getUUID());
            SelectionHudSync.clear(p);
        }
    }

    /** Read-only command regression signal; no pair identities are exposed to clients. */
    public static boolean diagnosticPairMatches(UUID playerId, UUID donor, UUID recipient) {
        Pair pair = PAIRS.get(playerId);
        return pair != null && pair.donorBody().equals(donor)
            && pair.recipientBody().equals(recipient);
    }

    public static void clearOnLogout(UUID playerId) {
        PENDING.remove(playerId);
        LAST_INTERACTION.remove(playerId);
        PAIRS.remove(playerId);
        PlayerMobPossession.clearOnLogout(playerId);
        PlayerPlayerExchange.clearOnLogout(playerId);
    }

    public static void cancel(ServerPlayer p) {
        PENDING.remove(p.getUUID());
        PAIRS.remove(p.getUUID());
        SelectionHudSync.clear(p);
        // Without clearing this stamp a cancel + immediate reselect of
        // the same mob on the same game tick is silently discarded.
        LAST_INTERACTION.remove(p.getUUID());
        boolean returned=PlayerMobPossession.end(p);
        boolean players=PlayerPlayerExchange.cancel(p);
        message(p, players
            ? "Reciprocal player-body control ended for both accounts."
            : returned ? "Returned to your player body; mob AI restored."
            : "Mind swap selection cancelled.");
    }

    static void clearSelectionAfterReturn(UUID account) {
        PENDING.remove(account);
        PAIRS.remove(account);
        LAST_INTERACTION.remove(account);
    }

    /** No static selection state may leak into a newly launched server. */
    public static void clearOnServerStop() {
        PENDING.clear();
        PAIRS.clear();
        LAST_INTERACTION.clear();
        PlayerMobPossession.clearOnServerStop();
        PlayerPlayerExchange.clearOnServerStop();
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

    /**
     * Actual book workflow, not diagnostic pair locking. Player/Mob and
     * NPC/NPC requests share the reciprocal-runtime gate; neither may fall
     * back to one-way possession or partial Brain/Goal rebinding.
     */
    public static void select(ServerPlayer p, LivingEntity target) {
        if (!hasBook(p)) {
            message(p,"Hold the Freaky Friday book first.");
            return;
        }
        if (!target.isAlive()) {
            message(p,"The selected body is no longer alive or active.");
            return;
        }
        if (p.level()!=target.level()) {
            message(p,"Both bodies must be in the same dimension.");
            return;
        }
        if (PlayerMobPossession.hasSession(p.getUUID())
                || PlayerPlayerExchange.active(p.getUUID())) {
            message(p,"You are already controlling a mob. Use /freakyfriday cancel to return.");
            return;
        }
        Pending pending=current(p);
        if (pending==null) {
            PENDING.put(p.getUUID(),new Pending(
                target.getUUID(),p.level().dimension().identifier().toString(),
                p.level().getGameTime()));
            SelectionHudSync.show(p,target.getDisplayName().getString(),
                (int)SELECTION_TIMEOUT_TICKS);
            message(p,"First body: "+target.getDisplayName().getString()
                +". Choose the second body within 8 seconds; no swap until both are selected.");
            return;
        }
        Entity entity=p.level().getEntity(pending.firstBody());
        if (!(entity instanceof LivingEntity first) || !first.isAlive()) {
            PENDING.remove(p.getUUID());
            SelectionHudSync.clear(p);
            message(p,"First body is no longer available; select again.");
            return;
        }
        if (first==target) {
            message(p,"Choose a different second body.");
            return;
        }
        if (first instanceof ServerPlayer firstPlayer
                && target instanceof ServerPlayer secondPlayer) {
            if (firstPlayer!=p && secondPlayer!=p) {
                message(p,"A player-player exchange must include your own body.");
                return;
            }
            ServerPlayer other=firstPlayer==p?secondPlayer:firstPlayer;
            String issue=PlayerPlayerExchange.begin(p,other);
            if (issue!=null) {
                message(p,"Player exchange rejected: "+issue);
                return;
            }
            PENDING.remove(p.getUUID());
            PAIRS.remove(p.getUUID());
            SelectionHudSync.clear(p);
            message(p,"You and "+other.getDisplayName().getString()
                +" exchanged physical control immediately. Either may /freakyfriday cancel.");
            return;
        }
        if (first instanceof ServerPlayer firstPlayer && firstPlayer!=p
                || target instanceof ServerPlayer secondPlayer && secondPlayer!=p) {
            message(p,"The selected two-body pairing must include your own player.");
            return;
        }

        if ((first==p && target instanceof net.minecraft.world.entity.Mob)
                || (target==p && first instanceof net.minecraft.world.entity.Mob)) {
            PENDING.remove(p.getUUID());
            SelectionHudSync.clear(p);
            message(p,"Reciprocal player/mob mind transfer is not implemented yet. "
                +"No possession, AI, or body state was changed.");
            return;
        }

        if (first instanceof net.minecraft.world.entity.Mob mobA
                && target instanceof net.minecraft.world.entity.Mob mobB) {
            String error=NativeBrainSwap.exchange(mobA,mobB);
            PENDING.remove(p.getUUID());
            SelectionHudSync.clear(p);
            if (error!=null) {
                message(p,"Native mind exchange REJECTED: "+error);
            } else {
                message(p,"Native goal/Brain controllers exchanged between "
                    +mobA.getDisplayName().getString()+" and "
                    +mobB.getDisplayName().getString()
                    +". Watch their ACTUAL behavior; cross-species abilities are not certified.");
            }
            return;
        }
        message(p,"These body types cannot be safely paired yet.");
    }

    /**
     * LEGACY developer-only diagnostic selection. Not invoked by the book.
     * Old test fixtures still exercise the detached-host lifecycle separately
     * from playable user controls.
     */
    public static void selectDiagnosticForTest(ServerPlayer p, LivingEntity target) {
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
            // The shared Pending/current() expiry clock is world game ticks.
            // Mixing System.nanoTime here invalidates the diagnostic pair.
            PENDING.put(p.getUUID(), new Pending(target.getUUID(),
                p.level().dimension().identifier().toString(), p.level().getGameTime()));
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
