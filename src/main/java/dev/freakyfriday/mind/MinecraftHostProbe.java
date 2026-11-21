package dev.freakyfriday.mind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;

/**
 * REAL-MINECRAFT physical action integration probe.
 *
 * Does NOT tick or impersonate any donor mob AI. It proves that the new host
 * engine can capture one bounded, isolated synthetic decision frame and route
 * its movement/look commands to an unchanged Minecraft recipient body.
 *
 * This is a test command, not a gameplay mind swap or autonomous controller.
 * The body's own AI may immediately override the requested path.
 */
public final class MinecraftHostProbe {
    private MinecraftHostProbe() {}

    public static String run(ServerPlayer player, Mob recipient) {
        if (!recipient.isAlive() || recipient.level() != player.level())
            return "Probe rejected: the selected body must be alive and in your dimension.";

        final UUID mindId = UUID.randomUUID();
        final MinecraftBodyPort body = new MinecraftBodyPort(recipient);
        final MindHostEngine host = new MindHostEngine();
        final MindLedger.Mind mind = new MindLedger.Mind(
            mindId, MindLedger.Kind.NPC, "freakyfriday:isolated_probe_only");

        host.register(body, mind, new MindHostEngine.IsolatedMindSource() {
            @Override public UUID mindId() { return mindId; }
            @Override
            public void decide(MindHostEngine.Observation observation,
                               MindHostEngine.IntentSink sink) {
                // Preverified synthetic intent generation only. We do not
                // tick the donor mob, its Brain or any real AI Goal.
                Map<String, String> position = Map.of(
                    "x", observation.facts().get("playerX"),
                    "y", observation.facts().get("playerY"),
                    "z", observation.facts().get("playerZ"));
                sink.request(new BodyActionGate.Intent(MinecraftBodyPort.LOOK_AT, position));
                Map<String, String> movement = new LinkedHashMap<>(position);
                movement.put("speed", "0.6");
                sink.request(new BodyActionGate.Intent(MinecraftBodyPort.MOVE_TO, movement));
            }
        });

        float healthBefore = recipient.getHealth();
        String bodyType = recipient.getType().toString();
        UUID bodyId = recipient.getUUID();
        var observation = new MindHostEngine.Observation(bodyId,
            recipient.level().getGameTime(), Map.of(
                "playerX", Double.toString(player.getX()),
                "playerY", Double.toString(player.getY()),
                "playerZ", Double.toString(player.getZ())));
        MindHostEngine.TickReport result = host.tick(observation);
        boolean identityIntact = bodyId.equals(recipient.getUUID())
            && bodyType.equals(recipient.getType().toString())
            && healthBefore == recipient.getHealth();
        if (!identityIntact)
            return "PROBE FAILED: body identity/type/health changed unexpectedly. "
                + result.status() + " " + result.diagnostic();

        StringBuilder message = new StringBuilder("Host action probe (SYNTHETIC; not native mind swap): ");
        message.append(result.status()).append("; body remains ")
            .append(bodyType).append(", health=").append(healthBefore);
        for (int i = 0; i < result.outcomes().size(); i++) {
            message.append("; ").append(result.intents().get(i).actionId())
                .append("=").append(result.outcomes().get(i).status());
        }
        if (!result.diagnostic().isBlank())
            message.append("; ").append(result.diagnostic());
        message.append(". A native AI tick can override the path; isolation still unproven.");
        return message.toString();
    }
}
