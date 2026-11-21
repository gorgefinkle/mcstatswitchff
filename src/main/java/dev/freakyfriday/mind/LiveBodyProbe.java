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
 * Server-side, time-bounded integration trace through the *real* Mob body.
 *
 * NO donor Goal, Brain, subclass AI or second physical entity is executed.
 * The controller source is a known synthetic action producer. This measures
 * real movement control uptake and recipient-body invariants across ticks.
 *
 * Normal native AI remains active and may override the requested path. We
 * record ACTUAL displacement as well as action acceptance, not just success
 * chat strings. This is a diagnostic only, not a mind-swap implementation.
 */
public final class LiveBodyProbe {
    private LiveBodyProbe() {}

    private static final int DURATION_TICKS = 40;
    private static final int MAX_ACTIVE = 8;
    private static final Map<UUID, Run> ACTIVE = new HashMap<>();

    private static final class Run {
        final Mob body;
        final UUID requestingPlayer;
        final UUID bodyUuid;
        final String bodyType;
        final Vec3 origin;
        final float healthBefore;
        final float maxHealthBefore;
        final MindHostEngine engine;
        int frames;
        int executed;
        int blocked;
        int unavailable;
        int captureFailed;
        int dispatchFailed;
        int duplicate;
        String lastDiagnostic = "";

        Run(Mob body, ServerPlayer player) {
            this.body = body;
            this.requestingPlayer = player.getUUID();
            this.bodyUuid = body.getUUID();
            this.bodyType = body.getType().toString();
            this.origin = body.position();
            this.healthBefore = body.getHealth();
            this.maxHealthBefore = body.getMaxHealth();
            this.engine = new MindHostEngine();

            UUID syntheticMind = UUID.randomUUID();
            double destinationX = origin.x + 3.0;
            double destinationY = origin.y;
            double destinationZ = origin.z;
            Map<String, String> position = Map.of(
                "x", Double.toString(destinationX),
                "y", Double.toString(destinationY),
                "z", Double.toString(destinationZ));
            Map<String, String> movement = Map.of(
                "x", Double.toString(destinationX),
                "y", Double.toString(destinationY),
                "z", Double.toString(destinationZ),
                "speed", "0.6");

            engine.register(new MinecraftBodyPort(body),
                new MindLedger.Mind(syntheticMind, MindLedger.Kind.NPC,
                    "freakyfriday:bounded_body_probe"),
                new MindHostEngine.IsolatedMindSource() {
                    @Override public UUID mindId() { return syntheticMind; }
                    @Override
                    public void decide(MindHostEngine.Observation observation,
                                       MindHostEngine.IntentSink sink) {
                        // No non-standard code: only known, safe, synthetic
                        // move/look requests. Send navigation once per five
                        // frames instead of recalculating paths each tick.
                        sink.request(new BodyActionGate.Intent(
                            MinecraftBodyPort.LOOK_AT, position));
                        if (frames % 5 == 0) {
                            sink.request(new BodyActionGate.Intent(
                                MinecraftBodyPort.MOVE_TO, movement));
                        }
                    }
                });
        }
    }

    public static synchronized String start(ServerPlayer player, Mob body) {
        if (!body.isAlive() || !(body.level() instanceof ServerLevel)
                || body.level() != player.level())
            return "Probe refused: select a live NPC body in your current dimension.";
        if (ACTIVE.containsKey(body.getUUID()))
            return "A bounded host probe is already running on that body.";
        if (ACTIVE.size() >= MAX_ACTIVE)
            return "Probe concurrency limit reached; no new body was modified.";

        ACTIVE.put(body.getUUID(), new Run(body, player));
        return "Starting 40-tick body-control probe for "
            + body.getDisplayName().getString() + ". The original AI remains active. "
            + "No donor mind is executed, no second mob is created and no swap occurs. "
            + "Result reports accepted commands AND actual displacement.";
    }

    @SubscribeEvent
    public static void afterEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Mob mob)
                || !(mob.level() instanceof ServerLevel)) return;
        Run active;
        synchronized (LiveBodyProbe.class) {
            active = ACTIVE.get(mob.getUUID());
            if (active == null || active.body != mob) return;

            if (!mob.isAlive()) {
                finish(active, "Body died during probe");
                return;
            }

            var report = active.engine.tick(new MindHostEngine.Observation(
                mob.getUUID(), mob.level().getGameTime(), Map.of()));
            active.frames++;

            switch (report.status()) {
                case DISPATCHED -> {
                    for (var outcome : report.outcomes()) {
                        switch (outcome.status()) {
                            case EXECUTED -> active.executed++;
                            case BLOCKED -> active.blocked++;
                            case UNAVAILABLE -> active.unavailable++;
                        }
                    }
                }
                case DUPLICATE_TICK -> active.duplicate++;
                case CAPTURE_FAILED -> active.captureFailed++;
                case ACTION_FAILED -> active.dispatchFailed++;
            }
            if (!report.diagnostic().isBlank())
                active.lastDiagnostic = report.diagnostic();
            if (active.frames >= DURATION_TICKS)
                finish(active, "Probe completed");
        }
    }

    @SubscribeEvent
    public static synchronized void onLeave(EntityLeaveLevelEvent event) {
        Run active = ACTIVE.get(event.getEntity().getUUID());
        if (active != null && active.body == event.getEntity())
            finish(active, "Body unloaded before the 40-tick test completed");
    }

    @SubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        ACTIVE.clear();
    }

    private static void finish(Run result, String reason) {
        ACTIVE.remove(result.bodyUuid);
        Mob body = result.body;
        double displacement = body.position().distanceTo(result.origin);
        boolean bodyUnchanged = result.bodyUuid.equals(body.getUUID())
            && result.bodyType.equals(body.getType().toString())
            && Float.compare(result.maxHealthBefore, body.getMaxHealth()) == 0;

        if (!(body.level() instanceof ServerLevel level)) return;
        ServerPlayer player = level.getServer().getPlayerList()
            .getPlayer(result.requestingPlayer);
        if (player == null) return;
        player.sendSystemMessage(Component.literal(
            "[Freaky Friday] Host probe: " + reason
            + "; ticks=" + result.frames
            + "; real displacement=" + String.format(java.util.Locale.ROOT,
                "%.3f", displacement) + " blocks"
            + "; body type/UUID/max-health intact=" + bodyUnchanged
            + "; commands executed=" + result.executed
            + ", blocked=" + result.blocked
            + ", unavailable=" + result.unavailable
            + ", failed captures=" + result.captureFailed
            + ", failed dispatches=" + result.dispatchFailed
            + ", duplicate frames=" + result.duplicate
            + "; health " + result.healthBefore + " -> " + body.getHealth()
            + (result.lastDiagnostic.isBlank() ? "" :
                "; last error=" + result.lastDiagnostic)
            + ". Navigation acceptance is NOT proof of native mind transfer."));
    }
}
