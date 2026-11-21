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
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * R0.15: genuine dual-entity LIFECYCLE proof only.
 *
 * Keeps the donor-class detached Mob in memory while a different real Mob
 * remains visible and world-registered. Mirrors the real body position over
 * 40 physical world ticks, and verifies the host never joined the world.
 *
 * THIS NEVER TICKS THE MIND HOST'S AI. It does not swap decisions, replace
 * the visible body or sandbox entity factory/setPos side effects. Disposable
 * worlds only, especially when probing third-party mod entities.
 */
public final class DetachedHostMirrorProbe {
    private DetachedHostMirrorProbe() {}
    private static final int MAX_SESSIONS = 4, MAX_TICKS = 40;
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private static final class Session {
        final Mob donor, recipient, host;
        final ServerLevel level;
        final UUID requester, donorId, recipientId, hostId;
        final String bodyType;
        final float initialMaxHealth;
        final Vec3 firstPosition;
        int ticks, mirrored, alignmentFailures, registrationViolations;
        String lastError = "";

        Session(ServerPlayer player, Mob donor, Mob recipient, Mob host) {
            this.donor = donor; this.recipient = recipient; this.host = host;
            this.level = (ServerLevel) recipient.level();
            this.requester = player.getUUID();
            this.donorId = donor.getUUID();
            this.recipientId = recipient.getUUID();
            this.hostId = host.getUUID();
            this.bodyType = recipient.getType().toString();
            this.initialMaxHealth = recipient.getMaxHealth();
            this.firstPosition = recipient.position();
        }
    }

    public static synchronized String start(ServerPlayer player, Mob donor, Mob recipient) {
        if (donor.level() != player.level() || recipient.level() != player.level()
                || donor == recipient || !donor.isAlive() || !recipient.isAlive())
            return "Mirror probe requires two live mobs in your loaded dimension.";
        if (SESSIONS.containsKey(recipient.getUUID()))
            return "That physical body already has a detached-host mirror probe.";
        if (SESSIONS.size() >= MAX_SESSIONS)
            return "Detached-host mirror probe concurrency limit reached.";

        DetachedMindHostProbe.Creation created =
            DetachedMindHostProbe.construct(donor, recipient);
        if (created.host() == null)
            return "Detached-host creation rejected: " + created.problem();
        Mob host = created.host();
        try {
            host.setPos(recipient.getX(), recipient.getY(), recipient.getZ());
        } catch (Exception | LinkageError failure) {
            return "Host position alignment failed: " + failure.getClass().getSimpleName()
                + ". No host AI was ticked.";
        }
        if (((ServerLevel) recipient.level()).getEntity(host.getUUID()) != null)
            return "Host became world registered during construction/position. "
                + "ABORT and discard the disposable test world.";
        SESSIONS.put(recipient.getUUID(), new Session(player, donor, recipient, host));
        return "Started 40-tick detached host MIRROR: donor-class instance="
            + host.getClass().getName() + ", physical body=" + recipient.getType()
            + ". Host is NOT world-registered and will NEVER be ticked. "
            + "This tests two-instance lifecycle, NOT mind control.";
    }

    @SubscribeEvent
    public static synchronized void onPhysicalTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel)) return;
        Session s = SESSIONS.get(mob.getUUID());
        if (s == null || s.recipient != mob) return;

        if (!s.recipient.isAlive() || !s.donor.isAlive()
                || s.level != mob.level()) {
            finish(s, "Original donor/recipient left active world");
            return;
        }

        try {
            if (s.level.getEntity(s.hostId) != null) {
                s.registrationViolations++;
                finish(s, "DETACHED HOST REGISTERED IN WORLD; unsafe abort");
                return;
            }
            Vec3 destination = s.recipient.position();
            if (s.host.position().distanceToSqr(destination) > 0.000001) {
                s.host.setPos(destination.x, destination.y, destination.z);
                s.mirrored++;
            }
            if (s.host.position().distanceToSqr(destination) > 0.000001)
                s.alignmentFailures++;
            if (s.level.getEntity(s.hostId) != null) {
                s.registrationViolations++;
                finish(s, "HOST JOINED WORLD AFTER POSITION MIRROR; unsafe abort");
                return;
            }
        } catch (Exception | LinkageError failure) {
            s.lastError = failure.getClass().getSimpleName();
            finish(s, "Detached host position mirror failed");
            return;
        }

        s.ticks++;
        if (s.ticks >= MAX_TICKS) finish(s, "40 physical-body ticks completed");
    }

    @SubscribeEvent
    public static synchronized void onLeave(EntityLeaveLevelEvent event) {
        for (Session s : java.util.List.copyOf(SESSIONS.values())) {
            if (s.donor == event.getEntity() || s.recipient == event.getEntity())
                finish(s, "Original donor/recipient unloaded");
        }
    }

    @SubscribeEvent
    public static synchronized void onStop(ServerStoppedEvent event) {
        SESSIONS.clear();
    }

    private static void finish(Session s, String reason) {
        SESSIONS.remove(s.recipientId);
        boolean hostStillDetached = s.level.getEntity(s.hostId) == null;
        boolean recipientStillItself = s.recipient.getUUID().equals(s.recipientId)
            && s.recipient.getType().toString().equals(s.bodyType)
            && Float.compare(s.recipient.getMaxHealth(), s.initialMaxHealth) == 0;
        double distance = s.recipient.position().distanceTo(s.firstPosition);
        ServerPlayer requester = s.level.getServer().getPlayerList().getPlayer(s.requester);
        if (requester == null) return;
        requester.sendSystemMessage(Component.literal(
            "[Freaky Friday] Dual-host 40-tick report: " + reason
            + "; observed physical ticks=" + s.ticks
            + "; donor-class host=" + s.host.getClass().getName()
            + "; ghost remains unregistered=" + hostStillDetached
            + "; original recipient unchanged (UUID/type/max health)="
                + recipientStillItself
            + "; position mirrors=" + s.mirrored
            + "; alignment failures=" + s.alignmentFailures
            + "; world registration violations=" + s.registrationViolations
            + "; physical displacement=" + String.format(java.util.Locale.ROOT,
                "%.3f", distance) + " blocks"
            + (s.lastError.isEmpty() ? "" : "; error=" + s.lastError)
            + ". The detached entity's AI was NEVER ticked. "
            + "World-effect isolation and actual mind exchange remain unproven."));
    }
}
