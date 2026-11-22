package dev.freakyfriday.mind;

import dev.freakyfriday.PossessionViewPayload;
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

    private static final int CONSENT_TICKS = 600;
    private static final double MAX_START_DISTANCE_SQUARED = 32.0 * 32.0;
    private static final double MAX_SESSION_DISTANCE_SQUARED = 48.0 * 48.0;

    private record Request(ServerPlayer requester, ServerPlayer recipient,
                           long createdAtTick) {}

    private static final class Controller {
        final ServerPlayer account;
        final ServerPlayer occupiedBody;
        PlayerMobPossession.Input input =
            new PlayerMobPossession.Input(0,0,false,0,0,0);
        long lastInputTick;
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

    private static final Map<UUID,Request> PENDING_BY_TARGET = new HashMap<>();
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

    public static synchronized String request(ServerPlayer requester, ServerPlayer target) {
        if (requester==null || target==null || requester==target)
            return "Choose yourself and a different online player.";
        if (!valid(requester,target)
                || requester.position().distanceToSqr(target.position())>MAX_START_DISTANCE_SQUARED)
            return "Both players must be alive, nearby and in the same dimension.";
        if (PlayerMobPossession.hasSession(requester.getUUID())
                || PlayerMobPossession.hasSession(target.getUUID())
                || active(requester.getUUID()) || active(target.getUUID()))
            return "One of those players already controls another body.";
        if (PENDING_BY_TARGET.containsKey(target.getUUID())
                || PENDING_BY_TARGET.values().stream().anyMatch(
                    q -> q.requester.getUUID().equals(requester.getUUID())))
            return "A player already has a pending exchange request.";
        PENDING_BY_TARGET.put(target.getUUID(),
            new Request(requester,target,target.level().getGameTime()));
        target.sendSystemMessage(Component.literal(
            "[Freaky Friday] "+requester.getName().getString()
            +" requests a reciprocal BODY swap. Type /freakyfriday accept"
            +" or /freakyfriday decline within 30 seconds."));
        return null;
    }

    private static boolean valid(ServerPlayer a, ServerPlayer b) {
        return a.isAlive() && b.isAlive() && !a.isRemoved() && !b.isRemoved()
            && a.level()==b.level() && a.level().getServer()==b.level().getServer();
    }

    public static synchronized String accept(ServerPlayer recipient) {
        Request request=PENDING_BY_TARGET.remove(recipient.getUUID());
        if (request==null || request.recipient!=recipient)
            return "No player-body exchange invitation is waiting for you.";
        ServerPlayer requester=request.requester;
        if (!valid(requester,recipient)
                || recipient.level().getGameTime()-request.createdAtTick>CONSENT_TICKS
                || requester.position().distanceToSqr(recipient.position())>MAX_START_DISTANCE_SQUARED)
            return "Exchange request expired or a player is no longer nearby.";
        if (active(requester.getUUID()) || active(recipient.getUUID())
                || PlayerMobPossession.hasSession(requester.getUUID())
                || PlayerMobPossession.hasSession(recipient.getUUID()))
            return "Another possession session already occupies a player body.";
        Session session=new Session(requester,recipient);
        try {
            PlayerCommandBodyRouting.bindCommittedPlayerExchange(requester,recipient);
            ACTIVE_BY_ACCOUNT.put(requester.getUUID(),session);
            ACTIVE_BY_ACCOUNT.put(recipient.getUUID(),session);
            sendCamera(requester,recipient,session.firstBody.expected);
            sendCamera(recipient,requester,session.secondBody.expected);
            requester.sendSystemMessage(Component.literal(
                "[Freaky Friday] "+recipient.getName().getString()
                +" accepted. You now move their physical body. /freakyfriday cancel returns both."));
            return null;
        } catch (RuntimeException error) {
            ACTIVE_BY_ACCOUNT.remove(requester.getUUID());
            ACTIVE_BY_ACCOUNT.remove(recipient.getUUID());
            PlayerCommandBodyRouting.clearInvolving(requester.getUUID());
            return "Player-body exchange failed before starting: "+error.getMessage();
        }
    }

    public static synchronized boolean decline(ServerPlayer recipient) {
        Request request=PENDING_BY_TARGET.remove(recipient.getUUID());
        if (request==null) return false;
        request.requester.sendSystemMessage(Component.literal(
            "[Freaky Friday] Player exchange request declined."));
        return true;
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

    /** Any participant can end the WHOLE reciprocal session. */
    public static synchronized boolean cancel(ServerPlayer player) {
        PENDING_BY_TARGET.values().removeIf(r -> r.requester==player);
        decline(player);
        Session s=ACTIVE_BY_ACCOUNT.get(player.getUUID());
        if (s==null) return false;
        end(s,true);
        return true;
    }

    public static synchronized void clearOnLogout(UUID account) {
        PENDING_BY_TARGET.remove(account);
        PENDING_BY_TARGET.values().removeIf(r -> r.requester.getUUID().equals(account));
        Session s=ACTIVE_BY_ACCOUNT.get(account);
        if (s!=null) end(s,true);
    }

    public static synchronized void clearOnServerStop() {
        for (Session session : java.util.Set.copyOf(ACTIVE_BY_ACCOUNT.values()))
            end(session,false);
        ACTIVE_BY_ACCOUNT.clear();
        PENDING_BY_TARGET.clear();
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
        double forward=in.forward(), strafe=in.strafe();
        double magnitude=Math.sqrt(forward*forward+strafe*strafe);
        if(magnitude>1.0){forward/=magnitude;strafe/=magnitude;}
        double speed=body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double travel=Double.isFinite(speed)
            ? Math.max(0.0,Math.min(0.45,speed*0.76)):0.0;
        double heading=Math.toRadians(in.yaw());
        double dx=(-Math.sin(heading)*forward + Math.cos(heading)*strafe)*travel;
        double dz=(Math.cos(heading)*forward + Math.sin(heading)*strafe)*travel;
        double dy=(in.jump() && body.onGround())
            ? 0.42
            : (body.onGround() || body.isNoGravity())
                ? 0.0 : Math.max(-0.58,bodyState.verticalVelocity-0.08);
        body.setYRot(in.yaw());
        body.setXRot(in.pitch());
        body.setYHeadRot(in.yaw());
        body.move(MoverType.SELF,new Vec3(dx,dy,dz));
        bodyState.expected=body.position();
        bodyState.verticalVelocity=body.onGround()?0:dy*0.98;
        body.setDeltaMovement(new Vec3(0,bodyState.verticalVelocity,0));
    }

    /** Real two-player server ticks apply the opposite player's last input. */
    @SubscribeEvent
    public static synchronized void tick(ServerTickEvent.Post event) {
        for (Session s : java.util.Set.copyOf(ACTIVE_BY_ACCOUNT.values())) {
            if (!ACTIVE_BY_ACCOUNT.containsKey(s.first.account.getUUID())) continue;
            ServerPlayer a=s.first.account,b=s.second.account;
            if (!valid(a,b) || a.position().distanceToSqr(b.position())>MAX_SESSION_DISTANCE_SQUARED) {
                end(s,true);
                continue;
            }
            long now=a.level().getGameTime();
            drive(s.secondBody,s.first.activeInput(now));
            drive(s.firstBody,s.second.activeInput(now));
            if ((now&1L)==0) {
                sendCamera(a,b,s.firstBody.expected);
                sendCamera(b,a,s.secondBody.expected);
            }
        }
        PENDING_BY_TARGET.values().removeIf(r ->
            !valid(r.requester,r.recipient)
            || r.recipient.level().getGameTime()-r.createdAtTick>CONSENT_TICKS);
    }
}
