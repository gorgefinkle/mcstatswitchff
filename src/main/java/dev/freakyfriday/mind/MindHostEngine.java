package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Host-independent engine for one mind controller per unchanged physical body.
 *
 * IMPORTANT: Never register an ordinary Mob::tick or Goal::tick callback here.
 * Native AI often mutates the world directly; a callback must be supplied by
 * an actual isolated interception/capture backend, not by a flag asserted by
 * a mod developer. No such backend is shipped in this checkpoint.
 *
 * The kernel only handles already-isolated decisions and native body actions.
 * It cannot sandbox arbitrary Java code by itself.
 */
public final class MindHostEngine {
    public static final int MAX_ACTIONS_PER_FRAME = 48;

    public record Observation(UUID bodyId, long tick, Map<String, String> facts) {
        public Observation {
            Objects.requireNonNull(bodyId, "body ID");
            if (tick < 0) throw new IllegalArgumentException("Negative game tick");
            facts = Map.copyOf(Objects.requireNonNull(facts, "observations"));
            if (facts.size() > 128) throw new IllegalArgumentException("Too many observations");
        }
    }

    /** Intents are collected completely BEFORE any recipient-body action runs. */
    @FunctionalInterface
    public interface IntentSink {
        void request(BodyActionGate.Intent intent);
    }

    /**
     * This is a PORT for future genuinely isolated native Minecraft drivers.
     * The backend MUST guarantee that thinking cannot directly mutate a mob,
     * world, inventory, navigation, or another controller.
     *
     * A lambda that runs donor Goal::tick or Mob::serverAiStep directly
     * DOES NOT satisfy this interface, even if it returns intents.
     */
    public interface IsolatedMindSource {
        UUID mindId();
        void decide(Observation observation, IntentSink capturedActions) throws Exception;
    }

    public enum TickStatus {
        DISPATCHED, DUPLICATE_TICK, CAPTURE_FAILED, ACTION_FAILED
    }

    public record TickReport(UUID body, UUID mind, long tick, TickStatus status,
                             List<BodyActionGate.Intent> intents,
                             List<BodyActionGate.Result> outcomes, String diagnostic) {
        public TickReport {
            Objects.requireNonNull(body, "body");
            Objects.requireNonNull(mind, "mind");
            Objects.requireNonNull(status, "status");
            intents = List.copyOf(intents);
            outcomes = List.copyOf(outcomes);
            diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    private final MindLedger owners = new MindLedger();
    private final Map<UUID, BodyActionGate.BodyPort> bodies = new HashMap<>();
    private final Map<UUID, IsolatedMindSource> sources = new HashMap<>();
    private final Map<UUID, Long> lastTickByBody = new HashMap<>();

    /**
     * Register a body and one unique mind source; identity, not species, is
     * the unit of ownership. This is not called automatically on entity spawn:
     * there must first be a verified native interception backend.
     */
    public synchronized void register(BodyActionGate.BodyPort body, MindLedger.Mind mind,
                                      IsolatedMindSource source) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(mind, "mind");
        Objects.requireNonNull(source, "source");
        UUID bodyId = Objects.requireNonNull(body.id(), "body UUID");
        if (bodies.containsKey(bodyId)) throw new IllegalStateException("Body already registered");
        if (!source.mindId().equals(mind.identity()))
            throw new IllegalArgumentException("Controller ID does not match registered mind");
        if (sources.containsKey(mind.identity()))
            throw new IllegalStateException("Controller already has a source");
        owners.registerOriginal(bodyId, mind);
        bodies.put(bodyId, body);
        sources.put(mind.identity(), source);
    }

    /**
     * Pure controller exchange. Body identity, type, stats and capabilities
     * stay in their original BodyPort, with no species-pair rules whatsoever.
     */
    public synchronized void exchange(UUID firstBody, UUID secondBody) throws Exception {
        if (!bodies.containsKey(firstBody) || !bodies.containsKey(secondBody))
            throw new IllegalArgumentException("Both physical bodies must be registered");
        owners.exchange(firstBody, secondBody, (recipient, incoming) -> {
            if (!sources.containsKey(incoming.identity()))
                throw new IllegalStateException("Incoming mind source is missing");
        });
    }

    public synchronized MindLedger.Mind controlling(UUID body) {
        return owners.controlling(body);
    }

    /**
     * The body supplies its own position/sensors as read-only facts. Intents
     * are captured in memory; overflow/decision exceptions reject the entire
     * frame before body execution. Actual physical actions aren't reversible,
     * so failures during dispatch explicitly report partial execution.
     */
    public synchronized TickReport tick(Observation observation) {
        Objects.requireNonNull(observation, "observation");
        UUID bodyId = observation.bodyId();
        BodyActionGate.BodyPort body = bodies.get(bodyId);
        if (body == null) throw new IllegalArgumentException("Body is not registered: " + bodyId);
        MindLedger.Mind mind = owners.controlling(bodyId);
        long tick = observation.tick();
        Long last = lastTickByBody.get(bodyId);
        if (last != null && tick <= last) {
            return new TickReport(bodyId, mind.identity(), tick, TickStatus.DUPLICATE_TICK,
                List.of(), List.of(), "A body is processed at most once per tick");
        }

        // Record the tick before calling the source. If it faults, it cannot
        // be retried in that tick and accidentally perform duplicate effects.
        lastTickByBody.put(bodyId, tick);
        List<BodyActionGate.Intent> planned = new ArrayList<>();
        try {
            IsolatedMindSource source = Objects.requireNonNull(
                sources.get(mind.identity()), "Bound mind source missing");
            source.decide(observation, intent -> {
                Objects.requireNonNull(intent, "Native AI may not emit null actions");
                if (planned.size() >= MAX_ACTIONS_PER_FRAME)
                    throw new IllegalStateException("Unbounded AI frame: too many actions");
                planned.add(intent);
            });
        } catch (Exception fault) {
            return new TickReport(bodyId, mind.identity(), tick, TickStatus.CAPTURE_FAILED,
                List.of(), List.of(), "All captured actions discarded: "
                    + fault.getClass().getSimpleName() + ": " + fault.getMessage());
        }

        List<BodyActionGate.Result> outcomes = new ArrayList<>(planned.size());
        for (BodyActionGate.Intent intent : planned) {
            try {
                outcomes.add(BodyActionGate.dispatch(body, intent));
            } catch (RuntimeException fault) {
                return new TickReport(bodyId, mind.identity(), tick, TickStatus.ACTION_FAILED,
                    planned, outcomes, "Native body dispatch failed after "
                        + outcomes.size() + " actions: " + fault.getMessage());
            }
        }
        return new TickReport(bodyId, mind.identity(), tick, TickStatus.DISPATCHED,
            planned, outcomes, "");
    }
}
