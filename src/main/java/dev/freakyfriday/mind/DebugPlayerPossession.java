package dev.freakyfriday.mind;

import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/**
 * Operator-only 26.2 live-server fixture. Uses a real ServerPlayer registered
 * through PlayerList with an embedded connection; the player's normal entity
 * tick is then driven by the server rather than being called by this fixture.
 */
public final class DebugPlayerPossession {
    private static final Map<UUID, Session> SESSIONS = new HashMap<>();

    private DebugPlayerPossession() {}

    private record Session(ServerPlayer player, Mob npcBody,
            Vec3 originalPlayerPosition, Vec3 originalNpcPosition) {}

    public static String start(MinecraftServer server, ServerLevel level, Mob npcBody) {
        if (server == null || level == null || npcBody == null)
            return "A server, level and NPC body are required.";
        if (npcBody.level() != level || !npcBody.isAlive())
            return "The NPC body must be alive and in the command source's dimension.";
        if (!SESSIONS.isEmpty())
            return "A debug ServerPlayer possession fixture is already active; end it first.";

        boolean aiCounterStarted = false;
        UUID profileId = UUID.randomUUID();
        String profileName = "FFPossess" + profileId.toString().substring(0, 8);
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
            new GameProfile(profileId, profileName), false);
        ServerPlayer player = new ServerPlayer(
            server, level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND) {
            @Override
            public void tick() {
                super.tick();
                player.resetLastActionTime();
            }

            @Override
            public boolean isMemoryConnection() {
                return true;
            }

            @Override
            public void send(Packet<?> packet, ChannelFutureListener listener, boolean flush) {
                super.send(packet, listener, flush);
                if (packet instanceof ClientboundKeepAlivePacket keepAlive) {
                    player.connection.handleKeepAlive(
                        new ServerboundKeepAlivePacket(keepAlive.getId()));
                }
            }
        };

        try {
            // Match NeoForge 26.2's ExtendedGameTestHelper ticking mock-player
            // connection so keep-alive and chunk-batch state stay valid.
            new EmbeddedChannel(connection);
            net.neoforged.neoforge.network.registration.NetworkRegistry
                .configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, player, cookie);
            server.getConnection().getConnections().add(connection);
            player.gameMode.changeGameModeForPlayer(GameType.SURVIVAL);

            // Keep the account body spatially separate and stationary. The
            // NPC physical body is the one driven by captured digital input.
            player.setNoGravity(true);
            player.snapTo(npcBody.getX() + 8.0, npcBody.getY(), npcBody.getZ() + 8.0,
                npcBody.getYRot(), npcBody.getXRot());
            player.connection.chunkSender.sendNextChunks(player);
            player.connection.chunkSender.onChunkBatchReceivedByClient(64.0f);
            player.connection.markClientLoaded();
            npcBody.setNoGravity(true);
            npcBody.setDeltaMovement(0.0, 0.0, 0.0);
            npcBody.setOnGround(true);

            Vec3 accountStart = player.position();
            Vec3 npcStart = npcBody.position();
            // Exercise the same transaction entry point called by the
            // two-click book workflow, not a test-only possession shortcut.
            String exchangeFailure = MindExchangeRuntime.exchange(player, npcBody);
            if (exchangeFailure != null) {
                throw new IllegalStateException(exchangeFailure);
            }
            MindHostRegistry.startDebugAiPhaseCounter(npcBody);
            aiCounterStarted = true;
            PlayerInputBridge.capture(player,
                new Input(true, false, false, false, false, false, false));
            SESSIONS.put(player.getUUID(),
                new Session(player, npcBody, accountStart, npcStart));
            return "DEBUG ServerPlayer possession started: player=" + player.getUUID()
                + " npc=" + npcBody.getUUID()
                + " camera=" + player.getCamera().getUUID()
                + ". Natural server ticks will now drive both bodies.";
        } catch (RuntimeException failure) {
            if (aiCounterStarted) MindHostRegistry.stopDebugAiPhaseCounter(npcBody);
            PlayerPossessionState.end(player);
            if (player.connection != null) {
                player.connection.disconnect(Component.literal("Freaky Friday debug fixture setup failed"));
            } else {
                connection.disconnect(Component.literal("Freaky Friday debug fixture setup failed"));
            }
            return "DEBUG ServerPlayer fixture failed: " + safeMessage(failure);
        }
    }

    public static String status() {
        Session session = SESSIONS.values().stream().findFirst().orElse(null);
        if (session == null) return "DEBUG no ServerPlayer possession fixture is active.";
        ServerPlayer player = session.player();
        Mob npc = session.npcBody();
        Vec3 playerNow = player.position();
        Vec3 npcNow = npc.position();
        UUID cameraId = player.getCamera().getUUID();
        return "DEBUG ServerPlayer possession active=" + PlayerPossessionState.isActive(player)
            + " player=" + player.getUUID()
            + " npc=" + npc.getUUID()
            + " camera=" + cameraId
            + " cameraMatchesNpc=" + cameraId.equals(npc.getUUID())
            + " playerDelta=" + distance(session.originalPlayerPosition(), playerNow)
            + " npcDelta=" + distance(session.originalNpcPosition(), npcNow)
            + " playerPos=" + vector(playerNow)
            + " npcPos=" + vector(npcNow)
            + " playerTicks=" + player.tickCount
            + " npcTicks=" + npc.tickCount
            + " npcAiPhases=" + MindHostRegistry.debugAiPhaseCount(npc)
            + " playerMindOnNpc=" + MindBodyBindings.controllerForBody(npc.getUUID()).equals(player.getUUID())
            + " npcMindOnPlayer=" + MindBodyBindings.controllerForBody(player.getUUID()).equals(npc.getUUID());
    }

    public static String stop() {
        Session session = SESSIONS.values().stream().findFirst().orElse(null);
        if (session == null) return "DEBUG no ServerPlayer possession fixture is active.";
        ServerPlayer player = session.player();
        PlayerPossessionState.end(player);
        MindHostRegistry.stopDebugAiPhaseCounter(session.npcBody());
        SESSIONS.remove(player.getUUID());
        if (player.connection != null)
            player.connection.disconnect(Component.literal("Freaky Friday debug possession fixture ended"));
        return "DEBUG ServerPlayer possession ended and controller bindings reversed for player="
            + player.getUUID() + " npc=" + session.npcBody().getUUID();
    }

    private static double distance(Vec3 first, Vec3 second) {
        return first.distanceTo(second);
    }

    private static String vector(Vec3 value) {
        return String.format(java.util.Locale.ROOT, "%.3f,%.3f,%.3f",
            value.x, value.y, value.z);
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank()
            ? failure.getClass().getSimpleName()
            : message;
    }
}
