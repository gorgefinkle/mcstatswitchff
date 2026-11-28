package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * Server-thread ownership/session state for one NPC ↔ authenticated Player
 * possession pair. Account identity never changes: only controller/body
 * ownership is exchanged.
 */
public final class PlayerPossessionState {
    private static final Map<UUID, Session> BY_PLAYER = new HashMap<>();
    private static final Map<UUID, UUID> PLAYER_BY_NPC_BODY = new HashMap<>();

    private PlayerPossessionState() {}

    private record Session(ServerPlayer player, Mob npcBody, UUID playerId, UUID npcBodyId) {}

    public static void begin(ServerPlayer player, Mob npcBody) {
        if (player == null || npcBody == null) throw new IllegalArgumentException("A player and NPC body are required");
        if (!player.isAlive() || !npcBody.isAlive()) throw new IllegalArgumentException("Both bodies must be alive");
        if (player.level() != npcBody.level()) throw new IllegalArgumentException("Both bodies must be in the same dimension");
        if (player.isSpectator()) throw new IllegalArgumentException("Spectator accounts cannot enter possession");
        if (BY_PLAYER.containsKey(player.getUUID()) || PLAYER_BY_NPC_BODY.containsKey(npcBody.getUUID()))
            throw new IllegalArgumentException("One of these bodies is already in a possession session");

        UUID playerId = player.getUUID();
        UUID npcId = npcBody.getUUID();
        MindHostRegistry.registerNativeHost(npcBody);
        MindBodyBindings.ensureOriginalBinding(playerId);
        MindBodyBindings.ensureOriginalBinding(npcId);

        // Exchange mappings first, then publish session state before routing the
        // camera. The camera Mixin suppresses vanilla relocation during possession.
        MindBodyBindings.exchangeBodies(playerId, npcId);
        Session session = new Session(player, npcBody, playerId, npcId);
        BY_PLAYER.put(playerId, session);
        PLAYER_BY_NPC_BODY.put(npcId, playerId);
        try {
            PlayerInputBridge.begin(player, npcBody);
            player.setCamera(npcBody);
        } catch (RuntimeException failure) {
            BY_PLAYER.remove(playerId);
            PLAYER_BY_NPC_BODY.remove(npcId);
            PlayerInputBridge.clear(playerId);
            MindBodyBindings.exchangeBodies(playerId, npcId);
            throw failure;
        }
    }

    /** Reverse the exact same pair and restore the account body's camera. */
    public static boolean end(ServerPlayer player) {
        if (player == null) return false;
        Session session = BY_PLAYER.get(player.getUUID());
        if (session == null) return false;
        endSession(session, true);
        return true;
    }

    /** Disconnect cleanup must not try to send another camera packet. */
    public static void endOnLogout(UUID playerId) {
        Session session = BY_PLAYER.get(playerId);
        if (session != null) endSession(session, false);
    }

    private static void endSession(Session session, boolean restoreCamera) {
        BY_PLAYER.remove(session.playerId());
        PLAYER_BY_NPC_BODY.remove(session.npcBodyId());
        PlayerInputBridge.clear(session.playerId());

        // The same bijective pair is reversed. No entity class/type check is
        // involved, and controller objects themselves remain untouched.
        MindBodyBindings.exchangeBodies(session.playerId(), session.npcBodyId());

        if (restoreCamera && session.player().isAlive()) {
            session.player().setCamera(session.player());
            session.player().setLastClientInput(net.minecraft.world.entity.player.Input.EMPTY);
        }
    }

    public static boolean isActive(ServerPlayer player) {
        return player != null && BY_PLAYER.containsKey(player.getUUID());
    }

    public static Mob controllerForPlayerBody(ServerPlayer player) {
        if (!isActive(player)) return null;
        UUID controllerId = MindBodyBindings.controllerForBody(player.getUUID());
        return MindHostRegistry.resolve(controllerId);
    }

    public static ServerPlayer playerForNpcBody(Mob body) {
        if (body == null) return null;
        UUID playerId = PLAYER_BY_NPC_BODY.get(body.getUUID());
        Session session = playerId == null ? null : BY_PLAYER.get(playerId);
        return session == null ? null : session.player();
    }

    public static boolean isPlayerControlledBody(Mob body) {
        return playerForNpcBody(body) != null;
    }

    public static UUID npcBodyForPlayer(UUID playerId) {
        Session session = BY_PLAYER.get(playerId);
        return session == null ? null : session.npcBodyId();
    }
}
