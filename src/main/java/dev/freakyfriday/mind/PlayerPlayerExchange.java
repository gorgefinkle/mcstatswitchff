package dev.freakyfriday.mind;

import dev.freakyfriday.PossessionViewPayload;
import dev.freakyfriday.PossessionStatePayload;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * First two-player reciprocal PHYSICAL control session.
 *
 * Both ServerPlayer bodies remain real, UUID-stable, damageable player bodies.
 * A controls B, B controls A. Each real connection stays authenticated as its
 * own account; the server, never a client-selected entity ID, owns the mapping.
 *
 * This is NOT complete account/inventory/attack/GUI transfer. Live graphical
 * client behavior still requires two connected clients to validate it.
 */
public final class PlayerPlayerExchange {
    private PlayerPlayerExchange() {}

    private static final double MAX_START_DISTANCE_SQUARED = 32.0 * 32.0;

    private static final class Controller {
        final ServerPlayer account;
        final ServerPlayer occupiedBody;
        PlayerMobPossession.Input input =
            new PlayerMobPossession.Input(0,0,false,0,0,0);
        long lastInputTick;
        long lastAttackTick=Long.MIN_VALUE/4;
        long lastAttackSequence;
        Controller(ServerPlayer account, ServerPlayer occupiedBody) {
            this.account=account;
            this.occupiedBody=occupiedBody;
            lastInputTick=account.level().getGameTime();
        }
        PlayerMobPossession.Input activeInput(long now) {
            if (now-lastInputTick<=8) return input;
            return new PlayerMobPossession.Input(0,0,false,input.yaw(),
                input.pitch(),input.sequence());
        }
    }

    private static final class BodyState {
        final ServerPlayer body;
        Vec3 expected;
        double verticalVelocity;
        BodyState(ServerPlayer body) {
            this.body=body;
            this.expected=body.position();
        }
    }

    private static final class Session {
        final Controller first, second;
        final BodyState firstBody, secondBody;
        Session(ServerPlayer a, ServerPlayer b) {
            first=new Controller(a,b);
            second=new Controller(b,a);
            firstBody=new BodyState(a);
            secondBody=new BodyState(b);
        }
    }

    private static final Map<UUID,Session> ACTIVE_BY_ACCOUNT = new HashMap<>();

    public static synchronized boolean active(UUID player) {
        return ACTIVE_BY_ACCOUNT.containsKey(player);
    }

    public static synchronized UUID occupied(UUID account) {
        Session s=ACTIVE_BY_ACCOUNT.get(account);
        if (s==null) return null;
        return s.first.account.getUUID().equals(account)
            ? s.first.occupiedBody.getUUID() : s.second.occupiedBody.getUUID();
    }

    private static boolean valid(ServerPlayer a, ServerPlayer b) {
        return a.isAlive() && b.isAlive() && !a.isRemoved() && !b.isRemoved()
            && a.level()==b.level() && a.level().getServer()==b.level().getServer();
    }

    /**
     * Book-selected two-player exchange. The OTHER player does not consent:
     * this is the direct Freaky Friday mechanic, just as mob selection is.
     * Only the selecting account can request a swap involving its own body;
     * no arbitrary client-supplied target UUID is accepted.
     */
    public static synchronized String begin(ServerPlayer requester, ServerPlayer target) {
        if (requester==null || target==null || requester==target)
            return "Choose yourself and a different online player.";
        if (!valid(requester,target)
                || requester.position().distanceToSqr(target.position())>MAX_START_DISTANCE_SQUARED)
            return "Both players must be alive, nearby and in the same dimension.";
        if (PlayerMobPossession.hasSession(requester.getUUID())
                || PlayerMobPossession.hasSession(target.getUUID())
                || active(requester.getUUID()) || active(target.getUUID()))
            return "One of those players already controls another body.";
        Session session=new Session(requester,target);
        try {
            PlayerCommandBodyRouting.bindCommittedPlayerExchange(requester,target);
            ACTIVE_BY_ACCOUNT.put(requester.getUUID(),session);
            ACTIVE_BY_ACCOUNT.put(target.getUUID(),session);
            sendCamera(requester,target,session.firstBody.expected);
            sendCamera(target,requester,session.secondBody.expected);
            requester.sendSystemMessage(Component.literal(
                "[Freaky Friday] You now control "+target.getName().getString()
                +"'s physical body. /freakyfriday cancel returns both."));
            target.sendSystemMessage(Component.literal(
                "[Freaky Friday] Your body has been swapped with "
                +requester.getName().getString()
                +". You now control their physical body. /freakyfriday cancel returns both."));
            return null;
        } catch (RuntimeException error) {
            ACTIVE_BY_ACCOUNT.remove(requester.getUUID());
            ACTIVE_BY_ACCOUNT.remove(target.getUUID());
            PlayerCommandBodyRouting.clearInvolving(requester.getUUID());
            return "Player-body exchange failed before starting: "+error.getMessage();
        }
    }

    /**
     * Called from an authenticated player connection. The packet has no body
     * ID. Frames sent by other accounts cannot control this participant.
     */
    public static synchronized void input(ServerPlayer connected, PlayerMobPossession.Input in) {
        Session s=ACTIVE_BY_ACCOUNT.get(connected.getUUID());
        if (s==null) return;
        Controller c=s.first.account==connected?s.first:
            s.second.account==connected?s.second:null;
        if(c==null || in.sequence()<=c.input.sequence()) return;
        c.input=in;
        c.lastInputTick=connected.level().getGameTime();
    }

    /**
     * A swapped player's PHYSICAL body died. Eject BOTH remote minds before
     * normal death processing/respawn changes that player's entity instance.
     * A dead body stays dead; this only restores the controlling cameras.
     */
    @SubscribeEvent
    public static synchronized void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer body) {
            Session session=ACTIVE_BY_ACCOUNT.get(body.getUUID());
            if (session!=null) end(session,true);
        }
    }

    /** A player controls the OTHER player's actual physical body when striking. */
    public static synchronized void attack(ServerPlayer sender,int candidateId,long sequence) {
        Session session=ACTIVE_BY_ACCOUNT.get(sender.getUUID());
        if(session==null)return;
        Controller controller=session.first.account==sender?session.first:
            session.second.account==sender?session.second:null;
        if(controller==null||sequence<=controller.lastAttackSequence)return;
        controller.lastAttackSequence=sequence;
        ServerPlayer body=controller.occupiedBody;
        if(!valid(sender,body)
            ||!(body.level() instanceof net.minecraft.server.level.ServerLevel level))return;
        long now=level.getGameTime();
        if(now-controller.lastAttackTick<10)return;
        body.setYRot(controller.input.yaw());
        body.setXRot(controller.input.pitch());
        net.minecraft.world.entity.Entity target=
            PossessionCombat.findValidMeleeTarget(level,body,candidateId);
        if(target==null)return;
        controller.lastAttackTick=now;
        body.attack(target);
    }

    /** Any participant can end the WHOLE reciprocal session. */
    public static synchronized boolean cancel(ServerPlayer player) {
        Session s=ACTIVE_BY_ACCOUNT.get(player.getUUID());
        if (s==null) return false;
        end(s,true);
        return true;
    }

    public static synchronized void clearOnLogout(UUID account) {
        Session s=ACTIVE_BY_ACCOUNT.get(account);
        if (s!=null) end(s,true);
    }

    public static synchronized void clearOnServerStop() {
        for (Session session : java.util.Set.copyOf(ACTIVE_BY_ACCOUNT.values()))
            end(session,false);
        ACTIVE_BY_ACCOUNT.clear();
    }

    private static void sendCamera(ServerPlayer account, ServerPlayer body, Vec3 ownAnchor) {
        // Headless mock player objects have no negotiated client channel.
        if (Boolean.getBoolean("freakyfriday.thirdPartyFixture")) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            account,new PossessionViewPayload(body.getId(),true,
                ownAnchor.x,ownAnchor.y,ownAnchor.z));
    }

    private static void restoreCamera(ServerPlayer account) {
        if (Boolean.getBoolean("freakyfriday.thirdPartyFixture") || account.isRemoved()) return;
        net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
            account,new PossessionViewPayload(-1,false,0,0,0));
    }

    private static void end(Session s, boolean notify) {
        ACTIVE_BY_ACCOUNT.remove(s.first.account.getUUID());
        ACTIVE_BY_ACCOUNT.remove(s.second.account.getUUID());
        MindSwapSessions.clearSelectionAfterReturn(s.first.account.getUUID());
        MindSwapSessions.clearSelectionAfterReturn(s.second.account.getUUID());
        PlayerCommandBodyRouting.clearInvolving(s.first.account.getUUID());
        for (ServerPlayer p : new ServerPlayer[] {s.first.account,s.second.account}) {
            if (notify) restoreCamera(p);
            if (notify && !p.isRemoved())
                p.sendSystemMessage(Component.literal(
                    "[Freaky Friday] Player body exchange ended; original control restored."));
        }
    }

    private static void drive(BodyState bodyState,
                              PlayerMobPossession.Input in) {
        ServerPlayer body=bodyState.body;
        if (body.position().distanceToSqr(bodyState.expected)>0.0001) {
            body.setPos(bodyState.expected.x,bodyState.expected.y,bodyState.expected.z);
        }
        body.setDeltaMovement(new Vec3(0,bodyState.verticalVelocity,0));
        PossessionMotion.step(body,in.forward(),in.strafe(),in.jump(),
            in.yaw(),in.pitch());
        bodyState.expected=body.position();
        bodyState.verticalVelocity=body.getDeltaMovement().y;
    }

    /** Real two-player server ticks apply the opposite player's last input. */
    @SubscribeEvent
    public static synchronized void tick(ServerTickEvent.Post event) {
        for (Session s : java.util.Set.copyOf(ACTIVE_BY_ACCOUNT.values())) {
            if (!ACTIVE_BY_ACCOUNT.containsKey(s.first.account.getUUID())) continue;
            ServerPlayer a=s.first.account,b=s.second.account;
            if (!valid(a,b)) {
                end(s,true);
                continue;
            }
            long now=a.level().getGameTime();
            drive(s.secondBody,s.first.activeInput(now));
            drive(s.firstBody,s.second.activeInput(now));
            if ((now&1L)==0) {
                sendCamera(a,b,s.firstBody.expected);
                sendCamera(b,a,s.secondBody.expected);
                if(!Boolean.getBoolean("freakyfriday.thirdPartyFixture")) {
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        a,new PossessionStatePayload(b.getId(),s.first.input.sequence(),
                            b.getX(),b.getY(),b.getZ(),b.getYRot(),b.getXRot(),
                            b.getDeltaMovement().y));
                    net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(
                        b,new PossessionStatePayload(a.getId(),s.second.input.sequence(),
                            a.getX(),a.getY(),a.getZ(),a.getYRot(),a.getXRot(),
                            a.getDeltaMovement().y));
                }
            }
        }
    }
}
