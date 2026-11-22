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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
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
    private static final long MAX_IDLE_SERVER_TICKS = 120;
    private static long serverTicks;

    private static final class Run {
        final Mob body;
        final String trace;
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
        long lastProgressServerTick;
        String lastDiagnostic = "";

        Run(Mob body, ServerPlayer player) {
            this.body = body;
            this.trace = ProbeTelemetry.newTrace();
            this.lastProgressServerTick = serverTicks;
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

        Run probe = new Run(body, player);
        ACTIVE.put(body.getUUID(), probe);
        ProbeTelemetry.info(probe.trace, "physical_probe_started",
            "bodyId=%s bodyType=%s class=%s originalPos=%s originalHealth=%.2f originalMaxHealth=%.2f syntheticMindOnly=true",
            body.getUUID(), body.getType(), body.getClass().getName(),
            body.position(), body.getHealth(), body.getMaxHealth());
        return "Starting 40-tick body-control probe trace=" + probe.trace + " for "
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
            active.lastProgressServerTick = serverTicks;
            ProbeTelemetry.info(active.trace, "physical_probe_tick",
                "tick=%d gameTime=%d bodyId=%s realPos=%s status=%s intentCount=%d outcomeCount=%d details=%s",
                active.frames, mob.level().getGameTime(), mob.getUUID(), mob.position(),
                report.status(), report.intents().size(), report.outcomes().size(),
                report.diagnostic());
            if (report.status() == MindHostEngine.TickStatus.ACTION_FAILED
                    || report.status() == MindHostEngine.TickStatus.CAPTURE_FAILED) {
                ProbeTelemetry.warn(active.trace, "physical_probe_problem",
                    "tick=%d status=%s details=%s",
                    active.frames, report.status(), report.diagnostic());
            }

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
    public static synchronized void onServerTick(ServerTickEvent.Post event) {
        serverTicks++;
        for (Run run : java.util.List.copyOf(ACTIVE.values())) {
            if (!(run.body.level() instanceof ServerLevel level)
                    || level.getServer() != event.getServer()) continue;
            if (serverTicks - run.lastProgressServerTick >= MAX_IDLE_SERVER_TICKS) {
                ProbeTelemetry.warn(run.trace, "physical_probe_watchdog_timeout",
                    "No recipient tick for %d server ticks; frames=%d",
                    serverTicks - run.lastProgressServerTick, run.frames);
                finish(run, "Physical body probe timed out: recipient stopped ticking");
            }
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
        for (Run active : ACTIVE.values()) {
            ProbeTelemetry.warn(active.trace, "physical_probe_server_stop",
                "server stopped during active test; frames=%d", active.frames);
        }
        ACTIVE.clear();
        serverTicks = 0L;
    }

    private static void finish(Run result, String reason) {
        ACTIVE.remove(result.bodyUuid);
        Mob body = result.body;
        double displacement = body.position().distanceTo(result.origin);
        boolean bodyUnchanged = result.bodyUuid.equals(body.getUUID())
            && result.bodyType.equals(body.getType().toString())
            && Float.compare(result.maxHealthBefore, body.getMaxHealth()) == 0;
        ProbeTelemetry.info(result.trace, "physical_probe_finished",
            "reason=%s frames=%d bodyId=%s bodyType=%s finalPos=%s displacement=%.5f bodyIdentityMaxHealthIntact=%s healthBefore=%.2f healthAfter=%.2f executed=%d blocked=%d unavailable=%d captureFailed=%d dispatchFailed=%d duplicate=%d diagnostic=%s",
            reason, result.frames, result.bodyUuid, result.bodyType, body.position(),
            displacement, bodyUnchanged, result.healthBefore, body.getHealth(),
            result.executed, result.blocked, result.unavailable, result.captureFailed,
            result.dispatchFailed, result.duplicate, result.lastDiagnostic);

        if (!(body.level() instanceof ServerLevel level)) return;
        ServerPlayer player = level.getServer().getPlayerList()
            .getPlayer(result.requestingPlayer);
        if (player == null) return;
        player.sendSystemMessage(Component.literal(
            "[Freaky Friday] Host probe trace=" + result.trace + ": " + reason
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
