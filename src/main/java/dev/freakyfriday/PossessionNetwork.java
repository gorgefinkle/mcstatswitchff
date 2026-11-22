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
                    PlayerMobPossession.input(connected,new PlayerMobPossession.Input(
                        packet.forward(),packet.strafe(),packet.jump(),
                        packet.yaw(),packet.pitch(),packet.sequence()));
                } catch (IllegalArgumentException rejected) {
                    // Bad client movement is dropped before touching the physical Mob.
                }
            });
        registrar.playToClient(
            PossessionViewPayload.TYPE,PossessionViewPayload.STREAM_CODEC);
    }
}
