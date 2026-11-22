package dev.freakyfriday;

import dev.freakyfriday.mind.PlayerMobPossession;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Common networking; the authenticated connection alone chooses the controller. */
public final class PossessionNetwork {
    private PossessionNetwork(){}
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar=event.registrar("1");
        registrar.playToServer(
            PossessionInputPayload.TYPE,PossessionInputPayload.STREAM_CODEC,
            (packet, context)-> {
                if (!(context.player() instanceof ServerPlayer connected)) return;
                try {
                    var input=new PlayerMobPossession.Input(
                        packet.forward(),packet.strafe(),packet.jump(),
                        packet.yaw(),packet.pitch(),packet.sequence());
                    if (dev.freakyfriday.mind.PlayerPlayerExchange.active(
                            connected.getUUID()))
                        dev.freakyfriday.mind.PlayerPlayerExchange.input(connected,input);
                    else PlayerMobPossession.input(connected,input);
                } catch (IllegalArgumentException rejected) {
                    // Bad client movement is dropped before touching the physical Mob.
                }
            });
        registrar.playToServer(
            PossessionAttackPayload.TYPE,PossessionAttackPayload.STREAM_CODEC,
            (packet, context)-> {
                if (!(context.player() instanceof ServerPlayer connected)) return;
                if (dev.freakyfriday.mind.PlayerPlayerExchange.active(connected.getUUID()))
                    dev.freakyfriday.mind.PlayerPlayerExchange.attack(
                        connected,packet.lookedAtEntityId(),packet.sequence());
                else PlayerMobPossession.attack(
                    connected,packet.lookedAtEntityId(),packet.sequence());
            });
        registrar.playToClient(
            PossessionViewPayload.TYPE,PossessionViewPayload.STREAM_CODEC);
        registrar.playToClient(
            PossessionStatePayload.TYPE,PossessionStatePayload.STREAM_CODEC);
        registrar.playToClient(
            SelectionHudPayload.TYPE,SelectionHudPayload.STREAM_CODEC);
    }
}
