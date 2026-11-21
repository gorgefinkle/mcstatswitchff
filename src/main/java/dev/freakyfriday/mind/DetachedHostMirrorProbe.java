package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Diagnostic only: a detached donor-class object mirrors a REAL recipient for
 * 40 physical ticks. The host itself is NEVER ticked or world-registered.
 * Constructor and position methods are not a sandbox for third-party mods.
 * Full correlated, per-tick telemetry is written to server latest.log.
 */
public final class DetachedHostMirrorProbe {
    private DetachedHostMirrorProbe() {}

    private static final int MAX_SESSIONS = 4, MAX_TICKS = 40;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final Mob donor, recipient, host;
        final ServerLevel level;
        final UUID requester, donorId, recipientId, hostId;
        final String trace, bodyType;
        final float initialHealth, initialMaxHealth, initialDonorHealth;
        final Vec3 firstPosition;
        final int initialDonorGoals, initialHostGoals, initialRecipientGoals;
        int ticks, mirrored, alignmentFailures, registrationViolations;
        boolean finished;
        String lastError = "";

        Session(ServerPlayer player, Mob donor, Mob recipient, Mob host, String trace) {
            this.donor = donor;
            this.recipient = recipient;
            this.host = host;
            this.level = (ServerLevel) recipient.level();
            this.requester = player.getUUID();
            this.donorId = donor.getUUID();
            this.recipientId = recipient.getUUID();
            this.hostId = host.getUUID();
            this.trace = trace;
            this.bodyType = recipient.getType().toString();
            this.initialHealth = recipient.getHealth();
            this.initialMaxHealth = recipient.getMaxHealth();
            this.initialDonorHealth = donor.getHealth();
            this.firstPosition = recipient.position();
            this.initialDonorGoals = donor.goalSelector.getAvailableGoals().size();
            this.initialHostGoals = host.goalSelector.getAvailableGoals().size();
            this.initialRecipientGoals = recipient.goalSelector.getAvailableGoals().size();
        }
    }

    public static synchronized String start(ServerPlayer player, Mob donor, Mob recipient) {
        String trace = ProbeTelemetry.newTrace();
        if (donor.level() != player.level() || recipient.level() != player.level()
                || donor == recipient || !donor.isAlive() || !recipient.isAlive()) {
            ProbeTelemetry.warn(trace, "mirror_rejected",
                "Two living NPCs in the initiating player's level are required");
            return "Mirror probe rejected (trace " + trace + "): invalid bodies/level.";
        }
        if (SESSIONS.containsKey(recipient.getUUID())) {
            ProbeTelemetry.warn(trace, "mirror_rejected",
                "bodyId=%s already undergoing an active test", recipient.getUUID());
            return "That physical body already has a detached-host mirror probe.";
        }
        if (SESSIONS.size() >= MAX_SESSIONS) {
            ProbeTelemetry.warn(trace, "mirror_rejected", "Max concurrent sessions=%d", MAX_SESSIONS);
            return "Detached-host mirror probe concurrency limit reached.";
        }

        DetachedMindHostProbe.Creation created =
            DetachedMindHostProbe.construct(donor, recipient, trace);
        if (created.host() == null) {
            ProbeTelemetry.warn(trace, "mirror_aborted", "hostFactory=%s", created.problem());
            return "Detached-host creation rejected (" + trace + "): " + created.problem();
        }

        Mob host = created.host();
        try {
            ProbeTelemetry.info(trace, "mirror_preflight",
                "hostId=%s hostClass=%s donorId=%s donorClass=%s recipientId=%s recipientType=%s hostPos=%s targetPos=%s",
                host.getUUID(), host.getClass().getName(),
                donor.getUUID(), donor.getClass().getName(),
                recipient.getUUID(), recipient.getType(), host.position(), recipient.position());
            host.setPos(recipient.getX(), recipient.getY(), recipient.getZ());
        } catch (Exception | LinkageError problem) {
            ProbeTelemetry.error(trace, "mirror_preflight_exception", problem,
                "Host positioning failed before the test began; no host tick");
            return "Host alignment failed (" + trace + "): "
                + problem.getClass().getSimpleName() + ". Full stack in latest.log.";
        }

        if (((ServerLevel) recipient.level()).getEntity(host.getUUID()) != null) {
            ProbeTelemetry.warn(trace, "registration_violation",
                "Host became world registered during construction/position. Abort.");
            return "Host was unexpectedly world-registered (" + trace
                + "); stop testing in this disposable world.";
        }

        Session session = new Session(player, donor, recipient, host, trace);
        SESSIONS.put(recipient.getUUID(), session);
        ProbeTelemetry.info(trace, "mirror_started",
            "donorId=%s donorType=%s donorClass=%s donorHealth=%.2f donorGoals=%d donorBrainActive=%s hostId=%s hostClass=%s hostGoals=%d hostBrainActive=%s recipientId=%s recipientType=%s recipientHealth=%.2f recipientMaxHealth=%.2f recipientGoals=%d recipientPos=%s ticksPlanned=%d hostRegistered=false hostAITicked=false",
            session.donorId, donor.getType(), donor.getClass().getName(),
            session.initialDonorHealth, session.initialDonorGoals, !donor.getBrain().isBrainDead(),
            session.hostId, host.getClass().getName(),
            session.initialHostGoals, !host.getBrain().isBrainDead(),
            session.recipientId, session.bodyType,
            session.initialHealth, session.initialMaxHealth,
            session.initialRecipientGoals, session.firstPosition, MAX_TICKS);
        return "Detached-host mirror started; trace=" + trace
            + ". 40 recipient ticks, ghost AI never ticked. Detailed per-tick "
            + "positions/registration and final report will be in latest.log [FF-PROBE].";
    }

    @SubscribeEvent
    public static synchronized void onPhysicalTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob body)
                || !(body.level() instanceof ServerLevel)) return;
        Session s = SESSIONS.get(body.getUUID());
        if (s == null || s.recipient != body || s.finished) return;
        if (!s.recipient.isAlive() || !s.donor.isAlive()
                || s.level != body.level()) {
            finish(s, "Donor or recipient stopped being an active live body");
            return;
        }
        try {
            boolean beforeRegistered = s.level.getEntity(s.hostId) != null;
            if (beforeRegistered) {
                s.registrationViolations++;
                ProbeTelemetry.warn(s.trace, "registration_violation",
                    "hostId=%s was registered BEFORE mirror tick=%d", s.hostId, s.ticks);
                finish(s, "Detached host registered before mirroring; unsafe abort");
                return;
            }
            Vec3 destination = s.recipient.position();
            Vec3 before = s.host.position();
            double squaredBefore = before.distanceToSqr(destination);
            boolean changed = squaredBefore > 0.000001;
            if (changed) {
                s.host.setPos(destination.x, destination.y, destination.z);
                s.mirrored++;
            }
            double squaredAfter = s.host.position().distanceToSqr(destination);
            if (squaredAfter > 0.000001) s.alignmentFailures++;
            boolean afterRegistered = s.level.getEntity(s.hostId) != null;

            s.ticks++;
            ProbeTelemetry.info(s.trace, "mirror_tick",
                "tick=%d gameTime=%d donorAlive=%s bodyAlive=%s bodyId=%s hostId=%s recipientPos=%s hostPosBefore=%s hostPosAfter=%s errorSqBefore=%.8f errorSqAfter=%.8f repositioned=%s hostRegistered=%s bodyHealth=%.2f bodyMaxHealth=%.2f recipientGoals=%d donorGoals=%d hostGoals=%d",
                s.ticks, s.level.getGameTime(), s.donor.isAlive(), s.recipient.isAlive(),
                s.recipientId, s.hostId, destination, before, s.host.position(),
                squaredBefore, squaredAfter, changed, afterRegistered,
                s.recipient.getHealth(), s.recipient.getMaxHealth(),
                s.recipient.goalSelector.getAvailableGoals().size(),
                s.donor.goalSelector.getAvailableGoals().size(),
                s.host.goalSelector.getAvailableGoals().size());

            if (afterRegistered) {
                s.registrationViolations++;
                finish(s, "Host registered after setPos; unsafe abort");
                return;
            }
            if (s.ticks >= MAX_TICKS)
                finish(s, "Completed all 40 recipient physical ticks");
        } catch (Exception | LinkageError problem) {
            s.lastError = problem.getClass().getSimpleName();
            ProbeTelemetry.error(s.trace, "mirror_tick_exception", problem,
                "Unexpected exception during detached host tick mirror; no host AI tick");
            finish(s, "Mirror tick failed");
        }
    }

    /** If an active host is ever submitted for addition, cancel that attempt. */
    @SubscribeEvent
    public static synchronized void onUnexpectedEntityJoin(EntityJoinLevelEvent event) {
        for (Session s : java.util.List.copyOf(SESSIONS.values())) {
            if (s.host == event.getEntity() && !s.finished) {
                s.registrationViolations++;
                ProbeTelemetry.warn(s.trace, "registration_violation",
                    "Detached host id=%s attempted to JOIN the world on gameTime=%d",
                    s.hostId, s.level.getGameTime());
                event.setCanceled(true);
                finish(s, "Detached host attempted world join; event cancelled");
            }
        }
    }

    @SubscribeEvent
    public static synchronized void onLeave(EntityLeaveLevelEvent event) {
        for (Session s : java.util.List.copyOf(SESSIONS.values())) {
            if (s.donor == event.getEntity() || s.recipient == event.getEntity()) {
                ProbeTelemetry.warn(s.trace, "unload",
                    "entity=%s donorLeft=%s recipientLeft=%s",
                    event.getEntity().getUUID(), event.getEntity() == s.donor,
                    event.getEntity() == s.recipient);
                finish(s, "Original donor or recipient unloaded");
            }
        }
    }

    @SubscribeEvent
    public static synchronized void onStop(ServerStoppedEvent event) {
        for (Session s : java.util.List.copyOf(SESSIONS.values())) {
            ProbeTelemetry.warn(s.trace, "server_stop",
                "Server shutdown during active detached host test; observedTicks=%d", s.ticks);
            finish(s, "Server stopped during test");
        }
        SESSIONS.clear();
    }

    private static void finish(Session s, String reason) {
        if (s.finished) return;
        s.finished = true;
        SESSIONS.remove(s.recipientId);
        boolean hostDetached = s.level.getEntity(s.hostId) == null;
        boolean recipientIdentityIntact =
            s.recipient.getUUID().equals(s.recipientId)
                && s.recipient.getType().toString().equals(s.bodyType)
                && Float.compare(s.recipient.getMaxHealth(), s.initialMaxHealth) == 0;
        double displacement = s.recipient.position().distanceTo(s.firstPosition);
        ProbeTelemetry.info(s.trace, "mirror_finished",
            "reason=%s observedTicks=%d donorUUID=%s recipientUUID=%s hostUUID=%s hostStillDetached=%s recipientIdentityMaxHealthIntact=%s originalRecipientHealth=%.2f finalRecipientHealth=%.2f originalDonorHealth=%.2f finalDonorHealth=%.2f positionMirrors=%d alignmentFailures=%d registrationViolations=%d physicalDisplacement=%.5f error=%s hostAITicked=false",
            reason, s.ticks, s.donorId, s.recipientId, s.hostId,
            hostDetached, recipientIdentityIntact,
            s.initialHealth, s.recipient.getHealth(),
            s.initialDonorHealth, s.donor.getHealth(),
            s.mirrored, s.alignmentFailures, s.registrationViolations,
            displacement, s.lastError);

        ServerPlayer requester = s.level.getServer().getPlayerList().getPlayer(s.requester);
        if (requester != null) {
            requester.sendSystemMessage(Component.literal(
                "[Freaky Friday] Dual-host test " + s.trace + ": " + reason
                + "; observedTicks=" + s.ticks
                + "; hostUnregistered=" + hostDetached
                + "; bodyIntact=" + recipientIdentityIntact
                + "; mirrorErrors=" + s.alignmentFailures
                + "; registrationViolations=" + s.registrationViolations
                + "; distance=" + String.format(java.util.Locale.ROOT, "%.3f", displacement)
                + " blocks. Detailed per-tick log: latest.log, search [FF-PROBE] "
                + "trace=" + s.trace));
        }
    }
}
