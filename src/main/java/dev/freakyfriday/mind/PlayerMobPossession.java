package dev.freakyfriday.mind;

import dev.freakyfriday.PossessionNetwork;
import dev.freakyfriday.PossessionViewPayload;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Server-authoritative, first PLAYABLE player->mob possession.
 *
 * This is intentionally named POSSESSION, not a complete two-way mind swap.
 * It moves the actual Mob body under authenticated player's WASD/jump while
 * the original player body remains in place. The mob's native AI is paused.
 * No false claim is made that NPC AI runs inside the player's original body.
 *
 * Collision/velocity belong to the physical Mob; player UUID/connection,
 * inventory, health and permissions remain with the original ServerPlayer.
 */
public final class PlayerMobPossession {
    private PlayerMobPossession() {}

    private static final class Session {
        final ServerPlayer player;
        final Mob body;
        final Vec3 anchor;
        final boolean originalNoAi;
        final boolean originalNoGravity;
        Input input = new Input(0,0,false,0,0,0);
        long lastInputTick;
        Session(ServerPlayer player, Mob body) {
            this.player = player;
            this.body = body;
            this.anchor = player.position();
            this.originalNoAi = body.isNoAi();
            this.originalNoGravity = body.isNoGravity();
            this.lastInputTick = player.level().getGameTime();
        }
    }

    public record Input(float forward, float strafe, boolean jump,
                        float yaw, float pitch, long sequence) {
        public Input {
            if (!Float.isFinite(forward) || !Float.isFinite(strafe)
                || !Float.isFinite(yaw) || !Float.isFinite(pitch)
                || Math.abs(forward)>1 || Math.abs(strafe)>1
                || Math.abs(pitch)>90)
                throw new IllegalArgumentException("Invalid possession input");
        }
    }

    /**
     * GameTest's mock ServerPlayer has NO client channel negotiation. Only
     * the real GameTest JVM sets thirdPartyFixture=true. Ordinary dedicated
     * servers ALWAYS deliver the camera packet to the real modded client.
     */
    private static void sendCamera(ServerPlayer player,PossessionViewPayload payload) {
        if (Boolean.getBoolean("freakyfriday.thirdPartyFixture")) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player,payload);
    }

    private static final Map<UUID,Session> ACTIVE = new HashMap<>();
    private static final Map<UUID,UUID> BODY_OWNERS = new HashMap<>();

    public static synchronized boolean hasSession(UUID owner) {
        return ACTIVE.containsKey(owner);
    }

    public static synchronized UUID possessedBody(UUID owner) {
        Session s=ACTIVE.get(owner);
        return s==null?null:s.body.getUUID();
    }

    public static synchronized String begin(ServerPlayer player, Mob body) {
        if (player == null || body == null || !player.isAlive() || !body.isAlive())
            return "Both bodies must be alive.";
        if (player.level()!=body.level())
            return "Both bodies must be in the same loaded dimension.";
        if (player.isRemoved() || body.isRemoved())
            return "Both bodies must be active.";
        if (ACTIVE.containsKey(player.getUUID()))
            return "Already controlling a mob. Use /freakyfriday cancel to return.";
        if (BODY_OWNERS.containsKey(body.getUUID()))
            return "That mob is already controlled by a player.";
        if (!body.getPassengers().isEmpty() || body.isPassenger())
            return "Cannot possess a mounted mob yet.";

        Session session = new Session(player,body);
        ACTIVE.put(player.getUUID(),session);
        BODY_OWNERS.put(body.getUUID(),player.getUUID());
        body.getNavigation().stop();
        body.setTarget(null);
        body.setNoAi(true);
        // No artificial flight granted by possession. Leave the native
        // body's original gravity/physics and collision in charge.
        sendCamera(player,new PossessionViewPayload(body.getId(),true));
        return null;
    }

    /**
     * Called only by a NeoForge authenticated serverbound payload handler.
     * Ignore any stale client frames; neither target ID nor entity UUID is
     * accepted from the client. The server owns the entire body mapping.
     */
    public static synchronized void input(ServerPlayer connected, Input in) {
        Session session=ACTIVE.get(connected.getUUID());
        if (session==null || session.player!=connected) return;
        if (in.sequence()<=session.input.sequence()) return;
        session.input=in;
        session.lastInputTick=connected.level().getGameTime();
    }

    public static synchronized boolean end(ServerPlayer player) {
        return end(player.getUUID(),true);
    }

    public static synchronized void clearOnLogout(UUID playerId) {
        end(playerId,false);
    }

    public static synchronized void clearOnServerStop() {
        for (UUID owner : java.util.List.copyOf(ACTIVE.keySet())) end(owner,false);
    }

    private static boolean end(UUID account,boolean notifyClient) {
        Session session=ACTIVE.remove(account);
        if (session==null) return false;
        BODY_OWNERS.remove(session.body.getUUID(),account);
        if (!session.body.isRemoved()) {
            session.body.getNavigation().stop();
            session.body.setDeltaMovement(Vec3.ZERO);
            session.body.setNoAi(session.originalNoAi);
            session.body.setNoGravity(session.originalNoGravity);
        }
        if (notifyClient && !session.player.isRemoved())
            sendCamera(session.player,new PossessionViewPayload(-1,false));
        return true;
    }

    /**
     * Real physical entity movement via vanilla Mob tick and collision.
     * Input timeout halts motion even if network packets stop arriving.
     */
    @SubscribeEvent
    public static synchronized void tick(ServerTickEvent.Post event) {
        Iterator<Map.Entry<UUID,Session>> it=ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            var entry=it.next();
            Session s=entry.getValue();
            ServerPlayer player=s.player;
            Mob body=s.body;
            if (player.isRemoved() || !player.isAlive() || !body.isAlive()
                    || body.isRemoved() || player.level()!=body.level()) {
                it.remove();
                BODY_OWNERS.remove(body.getUUID());
                if (!body.isRemoved()) {
                    body.setNoAi(s.originalNoAi);
                    body.setNoGravity(s.originalNoGravity);
                }
                if (!player.isRemoved())
                    sendCamera(player,new PossessionViewPayload(-1,false));
                continue;
            }
            // Original player's actual body remains in place; do not transfer
            // account authority or inventory to the mob. Packet movement of
            // original LocalPlayer is countered by server-owned anchor.
            player.setDeltaMovement(Vec3.ZERO);
            if (player.position().distanceToSqr(s.anchor)>0.0001)
                player.setPos(s.anchor.x,s.anchor.y,s.anchor.z);

            Input in=(player.level().getGameTime()-s.lastInputTick <= 8)
                ? s.input:new Input(0,0,false,s.input.yaw(),s.input.pitch(),s.input.sequence());
            double forward=in.forward(), strafe=in.strafe();
            double magnitude=Math.sqrt(forward*forward+strafe*strafe);
            if (magnitude>1) { forward/=magnitude; strafe/=magnitude; }
            double angle=Math.toRadians(in.yaw());
            double dx=(-Math.sin(angle)*forward + Math.cos(angle)*strafe)*0.19;
            double dz=( Math.cos(angle)*forward + Math.sin(angle)*strafe)*0.19;
            Vec3 vel=body.getDeltaMovement();
            // Maintain Y velocity/gravity from the physical body's vanilla
            // physics. Jump only when grounded.
            double dy=in.jump() && body.onGround()?0.40:vel.y;
            body.setYRot(in.yaw());
            body.setXRot(in.pitch());
            body.setYHeadRot(in.yaw());
            body.setDeltaMovement(new Vec3(dx,dy,dz));
        }
    }
}
