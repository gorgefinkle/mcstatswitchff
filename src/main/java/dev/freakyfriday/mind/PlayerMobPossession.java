package dev.freakyfriday.mind;

import dev.freakyfriday.PossessionNetwork;
import dev.freakyfriday.PossessionViewPayload;
import dev.freakyfriday.PossessionStatePayload;
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
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

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
        final float originalYaw,originalPitch;
        final boolean originalNoAi;
        final boolean originalNoGravity;
        final boolean reciprocal;
        Input input = new Input(0,0,false,0,0,0);
        long lastInputTick;
        long lastAttackTick=Long.MIN_VALUE/4;
        long lastAttackSequence;
        Session(ServerPlayer player, Mob body, boolean reciprocal) {
            this.player = player;
            this.body = body;
            this.reciprocal = reciprocal;
            this.anchor = player.position();
            this.originalYaw=player.getYRot();
            this.originalPitch=player.getXRot();
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

        return begin(player,body,false);
    }

    /**
     * Atomic reciprocal variant for a future full player↔mob exchange:
     * authenticated player input continues to drive the Mob body while an
     * isolated mob-mind worker drives the original ServerPlayer body.
     * This does not start that worker; it only applies the player-side half.
     */
    public static synchronized String beginReciprocal(ServerPlayer player,Mob body) {
        return begin(player,body,true);
    }

    private static synchronized String begin(ServerPlayer player,Mob body,boolean reciprocal) {
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

        Session session = new Session(player,body,reciprocal);
        ACTIVE.put(player.getUUID(),session);
        BODY_OWNERS.put(body.getUUID(),player.getUUID());
        body.getNavigation().stop();
        body.setTarget(null);
        body.setNoAi(true);
        // No artificial flight granted by possession. Leave the native
        // body's original gravity/physics and collision in charge.
        sendCamera(player,new PossessionViewPayload(body.getId(),true,
            session.anchor.x,session.anchor.y,session.anchor.z));
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

    /**
     * Attack intent is authenticated to the controlling connection; the
     * physical Mob is the attacker, not the abandoned ServerPlayer body.
     * Vanilla self-attack packets must be canceled on the client.
     */
    public static synchronized void attack(ServerPlayer sender,int candidateId,long sequence) {
        Session session=ACTIVE.get(sender.getUUID());
        if(session==null||session.player!=sender||sequence<=session.lastAttackSequence)return;
        session.lastAttackSequence=sequence;
        Mob body=session.body;
        if(!sender.isAlive()||!body.isAlive()
            ||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        long now=level.getGameTime();
        if(now-session.lastAttackTick<10)return;
        body.setYRot(session.input.yaw());
        body.setXRot(session.input.pitch());
        net.minecraft.world.entity.Entity victim=
            PossessionCombat.findValidMeleeTarget(level,body,candidateId,sender);
        if(victim==null)return;
        session.lastAttackTick=now;
        body.doHurtTarget(level,victim);
    }

    public static synchronized boolean end(ServerPlayer player) {
        return end(player.getUUID(),true);
    }

    /**
     * Return the mind as soon as its occupied physical body dies. This is a
     * gameplay return, NOT revival or teleportation of the dead mob.
     * The tick fallback below covers removal/unload and canceled death events.
     */
    @SubscribeEvent
    public static synchronized void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Mob mob) {
            UUID owner=BODY_OWNERS.get(mob.getUUID());
            if (owner!=null) {
                Session session=ACTIVE.get(owner);
                if (session!=null) {
                    end(owner,true);
                    if (!session.player.isRemoved())
                        session.player.sendSystemMessage(Component.literal(
                            "[Freaky Friday] Your occupied mob died; you returned to your own body."));
                }
            }
        } else if (event.getEntity() instanceof ServerPlayer player) {
            // The original player's body can itself be killed while its mind
            // is away. Never retain an orphaned camera/control assignment.
            end(player.getUUID(),true);
        }
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
        MindSwapSessions.clearSelectionAfterReturn(account);
        if (!session.body.isRemoved()) {
            session.body.getNavigation().stop();
            session.body.setDeltaMovement(Vec3.ZERO);
            session.body.setNoAi(session.originalNoAi);
            session.body.setNoGravity(session.originalNoGravity);
        }
        if (notifyClient && !session.player.isRemoved())
            sendCamera(session.player,new PossessionViewPayload(-1,false,0,0,0));
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
                // Safe in-place retirement; do not remove another entry
                // directly while iterating. This is also the fallback when
                // another mod suppresses the ordinary LivingDeathEvent.
                it.remove();
                BODY_OWNERS.remove(body.getUUID());
                MindSwapSessions.clearSelectionAfterReturn(player.getUUID());
                if (!body.isRemoved()) {
                    body.setNoAi(s.originalNoAi);
                    body.setNoGravity(s.originalNoGravity);
                }
                if (!player.isRemoved()) {
                    sendCamera(player,new PossessionViewPayload(-1,false,0,0,0));
                    if (!body.isAlive())
                        player.sendSystemMessage(Component.literal(
                            "[Freaky Friday] Occupied body died; returned to your original body."));
                }
                continue;
            }
            // In one-way possession the account's original player body stays
            // anchored. In reciprocal mode it is the mob mind's physical body,
            // so do not freeze or teleport it while player input drives the Mob.
            if(!s.reciprocal) {
                player.setDeltaMovement(Vec3.ZERO);
                if (player.position().distanceToSqr(s.anchor)>0.0001)
                    player.setPos(s.anchor.x,s.anchor.y,s.anchor.z);
            }

            Input in=(player.level().getGameTime()-s.lastInputTick <= 8)
                ? s.input:new Input(0,0,false,s.input.yaw(),s.input.pitch(),s.input.sequence());
            if(!s.reciprocal) {
                player.setYRot(s.originalYaw);
                player.setXRot(s.originalPitch);
                player.setYHeadRot(s.originalYaw);
            }
            PossessionMotion.step(body,in.forward(),in.strafe(),in.jump(),
                in.yaw(),in.pitch());
            if ((player.level().getGameTime()&1L)==0
                    && !Boolean.getBoolean("freakyfriday.thirdPartyFixture")) {
                if(s.reciprocal) {
                    sendCamera(player,new PossessionViewPayload(body.getId(),true,
                        player.getX(),player.getY(),player.getZ()));
                }
                net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                    player,new PossessionStatePayload(body.getId(),in.sequence(),
                        body.getX(),body.getY(),body.getZ(),
                        body.getYRot(),body.getXRot(),body.getDeltaMovement().y));
            }
        }
    }
}
