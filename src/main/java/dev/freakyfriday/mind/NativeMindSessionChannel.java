package dev.freakyfriday.mind;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

/**
 * Strict per-body streaming handshake for a remote native-AI worker.
 *
 * Exactly one sensory snapshot may be in flight for a body. Each reply must
 * authenticate to the same session/body/mind and exact sensory tick. A reply
 * may arrive on a later server tick; its actions are rebased to that actual
 * dispatch tick after authentication. A missed deadline or malformed reply
 * closes the session instead of replaying stale decisions.
 *
 * This class is transport/session policy only. A process supervisor and a
 * worker that continuously consumes these requests are separate integration
 * requirements; this class does not claim to provide either.
 */
public final class NativeMindSessionChannel implements AutoCloseable {
    public static final int DEFAULT_REPLY_DEADLINE_TICKS = 2;

    public enum State { ACTIVE, CLOSED, FAULTED }

    public record Request(UUID sessionId, UUID bodyId, UUID mindId,
                          long tick, long deadlineTick, byte[] sensoryWire) {
        public Request {
            Objects.requireNonNull(sessionId);
            Objects.requireNonNull(bodyId);
            Objects.requireNonNull(mindId);
            sensoryWire = sensoryWire.clone();
        }
        @Override public byte[] sensoryWire() { return sensoryWire.clone(); }
    }

    private final UUID sessionId;
    private final UUID bodyId;
    private final UUID mindId;
    private final byte[] key;
    private final int deadlineTicks;
    private State state = State.ACTIVE;
    private long nextTick;
    private long outstandingTick = -1;
    private long requestHostTick = -1;
    private long deadlineTick = -1;
    private String fault = "";

    public NativeMindSessionChannel(UUID sessionId, UUID bodyId, UUID mindId,
                                    byte[] key, long firstTick, int deadlineTicks) {
        this.sessionId = Objects.requireNonNull(sessionId);
        this.bodyId = Objects.requireNonNull(bodyId);
        this.mindId = Objects.requireNonNull(mindId);
        if (key == null || key.length != NativeMindSenseWireCodec.KEY_BYTES)
            throw new IllegalArgumentException("Expected a 256-bit session key");
        if (firstTick < 0 || deadlineTicks < 1 || deadlineTicks > 20)
            throw new IllegalArgumentException("Invalid stream tick/deadline policy");
        this.key = key.clone();
        this.nextTick = firstTick;
        this.deadlineTicks = deadlineTicks;
    }

    public synchronized State state() { return state; }
    public synchronized long nextTick() { return nextTick; }
    public synchronized boolean hasOutstandingRequest() { return outstandingTick >= 0; }
    public synchronized String fault() { return fault; }

    /** Backwards-compatible one-clock entrypoint used by codec-only tests. */
    public synchronized Request begin(NativeMindSenseFrame senses) {
        return begin(senses, senses.tick());
    }

    /**
     * Encode one body-local sensory sequence while measuring the response
     * deadline against the REAL server tick. A worker may need several server
     * ticks to boot or process the snapshot; those host ticks must not corrupt
     * the strict consecutive sensory sequence consumed by NativeMindMirrorState.
     */
    public synchronized Request begin(NativeMindSenseFrame senses,long hostTick) {
        requireActive();
        Objects.requireNonNull(senses);
        if (hostTick < 0)
            throw new IllegalArgumentException("Negative host tick");
        if (outstandingTick >= 0)
            throw new IllegalStateException("Previous native-AI tick is still outstanding");
        if (!bodyId.equals(senses.bodyId()) || !mindId.equals(senses.mindId()))
            throw new IllegalArgumentException("Sensory snapshot belongs to another body or mind");
        if (senses.tick() != nextTick)
            throw new IllegalArgumentException("Expected sensory tick " + nextTick
                + " but received " + senses.tick());
        if (!senses.body().alive()) {
            fault("Physical body died; native-AI session retired");
            throw new IllegalStateException(fault);
        }
        byte[] wire = NativeMindSenseWireCodec.encode(senses, key);
        outstandingTick = nextTick;
        requestHostTick = hostTick;
        deadlineTick = Math.addExact(hostTick, deadlineTicks);
        return new Request(sessionId, bodyId, mindId, outstandingTick,
            deadlineTick, wire);
    }

    /**
     * Admit one authenticated worker response. The caller supplies the
     * authoritative host tick at receipt; response tick must match the
     * outstanding request and arrive no later than its deadline.
     */
    public synchronized CapturedIntentBridge.Frame complete(
            byte[] responseWire, long receivedAtTick) {
        requireActive();
        if (outstandingTick < 0)
            throw new IllegalStateException("No native-AI request is outstanding");
        if (receivedAtTick < requestHostTick)
            throw new IllegalArgumentException("Response receipt tick moved backwards");
        if (receivedAtTick > deadlineTick) {
            fault("Native-AI response missed deadline at tick " + deadlineTick);
            throw new IllegalStateException(fault);
        }
        try {
            var envelope = CapturedIntentWireCodec.decode(
                responseWire, key, sessionId, outstandingTick);
            var frame = envelope.frame();
            if (!bodyId.equals(frame.bodyId()) || !mindId.equals(frame.mindId()))
                throw new IllegalArgumentException("Worker response body/mind identity mismatch");
            // The worker's signed frame names the sensory tick it consumed.
            // Dispatch must instead use the CURRENT server tick; otherwise
            // CapturedIntentBridge correctly rejects a one-tick-late result.
            // Rebase only after authentication and identity checks.
            CapturedIntentBridge.Frame dispatchFrame = new CapturedIntentBridge.Frame(
                bodyId, mindId, receivedAtTick, frame.actions());
            outstandingTick = -1;
            requestHostTick = -1;
            deadlineTick = -1;
            nextTick = Math.addExact(envelope.frame().tick(), 1);
            return dispatchFrame;
        } catch (RuntimeException invalid) {
            fault("Rejected worker response: " + invalid.getMessage());
            throw invalid;
        }
    }

    /** Called by the server tick loop even when no response file/packet arrives. */
    public synchronized boolean expire(long currentTick) {
        if (state != State.ACTIVE || outstandingTick < 0) return false;
        if (currentTick <= deadlineTick) return false;
        fault("Native-AI worker timed out at tick " + deadlineTick);
        return true;
    }

    public synchronized void fault(String reason) {
        if (state == State.ACTIVE) {
            state = State.FAULTED;
            fault = Objects.requireNonNullElse(reason, "Native-AI session faulted");
            outstandingTick = -1;
            requestHostTick = -1;
            deadlineTick = -1;
            Arrays.fill(key, (byte) 0);
        }
    }

    @Override public synchronized void close() {
        if (state == State.ACTIVE) {
            state = State.CLOSED;
            outstandingTick = -1;
            deadlineTick = -1;
            Arrays.fill(key, (byte) 0);
        }
    }

    private void requireActive() {
        if (state != State.ACTIVE)
            throw new IllegalStateException("Native-AI session is " + state
                + (fault.isBlank() ? "" : ": " + fault));
    }
}
