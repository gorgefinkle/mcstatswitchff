package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * STRICT DATA-ONLY decision boundary for a future separate-process native AI
 * backend. A Frame contains VALUE OBJECTS; receiving a Frame does NOT invoke
 * donor Goal, Brain, Mob::tick, or an arbitrary decision callback.
 *
 * This protects the in-process decision-dispatch PATH only. It does NOT make
 * untrusted Java code running in this JVM safe, authenticate a frame sender,
 * or sandbox modded BodyPort executors. The frame producer must ultimately
 * run in a genuinely isolated process with an authenticated transport.
 *
 * Only the PHYSICAL recipient's BodyPort is ever invoked. No per-species
 * behavior rules appear here. This is not yet a live gameplay swap.
 */
public final class CapturedIntentBridge {
    public static final int MAX_ACTIONS = 48;
    public static final int MAX_FRAME_CHARACTERS = 32768;

    public record Frame(UUID bodyId, UUID mindId, long tick,
                        List<BodyActionGate.Intent> actions) {
        public Frame {
            Objects.requireNonNull(bodyId, "bodyId");
            Objects.requireNonNull(mindId, "mindId");
            if (tick < 0) throw new IllegalArgumentException("Negative tick");
            actions = List.copyOf(Objects.requireNonNull(actions, "actions"));
            if (actions.size() > MAX_ACTIONS)
                throw new IllegalArgumentException("Action count exceeds frame limit");
            long characters = 0;
            for (BodyActionGate.Intent action : actions) {
                characters += action.actionId().length();
                for (var pair : action.arguments().entrySet())
                    characters += pair.getKey().length() + pair.getValue().length();
                if (characters > MAX_FRAME_CHARACTERS)
                    throw new IllegalArgumentException("Frame content exceeds bound");
            }
        }
    }

    public enum Status {
        DISPATCHED,
        UNKNOWN_BODY,
        WRONG_CONTROLLER,
        DUPLICATE_OR_STALE_TICK,
        PARTIAL_DISPATCH_FAILURE
    }

    public record Receipt(UUID bodyId, UUID mindId, long tick, Status status,
                          List<BodyActionGate.Result> outcomes, String diagnostic) {
        public Receipt {
            Objects.requireNonNull(status, "status");
            outcomes = List.copyOf(Objects.requireNonNull(outcomes, "outcomes"));
            Objects.requireNonNull(diagnostic, "diagnostic");
        }
    }

    private final MindLedger assignment = new MindLedger();
    private final Map<UUID, BodyActionGate.BodyPort> bodies = new HashMap<>();
    private final Map<UUID, Long> mostRecentTick = new HashMap<>();

    public synchronized void register(BodyActionGate.BodyPort port, MindLedger.Mind mind) {
        Objects.requireNonNull(port, "port");
        Objects.requireNonNull(mind, "mind");
        UUID id = Objects.requireNonNull(port.id(), "body id");
        if (bodies.containsKey(id))
            throw new IllegalStateException("Physical body already registered");
        assignment.registerOriginal(id, mind); // immutable admission before port insertion
        bodies.put(id, port);
    }

    public synchronized void exchange(UUID left, UUID right) throws Exception {
        if (!bodies.containsKey(left) || !bodies.containsKey(right))
            throw new IllegalArgumentException("Unknown physical body in exchange");
        assignment.exchange(left, right, (body, incoming) -> {
            // Each incoming mind MUST exist on some registered body.
            // MindLedger's atomic exchange checks uniqueness and rolls back.
            if (assignment.bodyControlledBy(incoming.identity()).isEmpty())
                throw new IllegalArgumentException("Unknown mind");
        });
    }

    public synchronized MindLedger.Mind controlling(UUID physicalBody) {
        return assignment.controlling(physicalBody);
    }

    /**
     * Public receiving entrypoint for a future external isolated producer.
     * HMAC verification, session and SERVER tick admission happen BEFORE the
     * first physical recipient method is invoked. The raw Frame API below
     * is package-scoped solely for local tests and trusted internal code.
     */
    public synchronized Receipt deliverAuthenticated(byte[] message, byte[] secret,
                                                      UUID session, long serverTick) {
        var decoded = CapturedIntentWireCodec.decode(
            message, secret, session, serverTick);
        return deliver(decoded.frame());
    }

    synchronized Receipt deliver(Frame frame) {
        Objects.requireNonNull(frame, "frame");
        BodyActionGate.BodyPort recipient = bodies.get(frame.bodyId());
        if (recipient == null)
            return receipt(frame, Status.UNKNOWN_BODY, List.of(),
                "No registered physical recipient");
        UUID expected = assignment.controlling(frame.bodyId()).identity();
        if (!expected.equals(frame.mindId()))
            return receipt(frame, Status.WRONG_CONTROLLER, List.of(),
                "Frame mind is not assigned to this physical recipient");
        Long previous = mostRecentTick.get(frame.bodyId());
        if (previous != null && frame.tick() <= previous)
            return receipt(frame, Status.DUPLICATE_OR_STALE_TICK, List.of(),
                "Physical body can accept at most one frame per tick");

        // Consume the tick BEFORE any physical action, including exceptions.
        // Never retry partial world-changing dispatch within the same tick.
        mostRecentTick.put(frame.bodyId(), frame.tick());
        List<BodyActionGate.Result> outcomes = new ArrayList<>();
        for (BodyActionGate.Intent action : frame.actions()) {
            try {
                outcomes.add(BodyActionGate.dispatch(recipient, action));
            } catch (RuntimeException | LinkageError failure) {
                return receipt(frame, Status.PARTIAL_DISPATCH_FAILURE, outcomes,
                    "Execution stopped after " + outcomes.size() +
                    " completed actions; no rollback claimed: " +
                    failure.getClass().getSimpleName());
            }
        }
        return receipt(frame, Status.DISPATCHED, outcomes, "");
    }

    private static Receipt receipt(Frame frame, Status status,
                                   List<BodyActionGate.Result> results, String detail) {
        return new Receipt(frame.bodyId(), frame.mindId(), frame.tick(),
            status, results, detail);
    }
}
