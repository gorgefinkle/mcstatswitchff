package dev.freakyfriday.mind;

import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Pair-level coordinator for two independent authenticated mind streams.
 *
 * <p>Each original physical body owns one {@link NativeMindSessionChannel}.
 * The channels have different session IDs and 256-bit keys, may have one
 * request in flight simultaneously, and advance their sensory clocks
 * independently. A protocol fault or timeout on either side retires the
 * complete reciprocal pair; the other mind is never left controlling a body
 * by itself.</p>
 *
 * <p>This is the in-memory coordination contract. It does not launch a worker
 * process, serialize a complete Mob brain, mirror a full Minecraft world, or
 * claim that the production book route uses this registry.</p>
 */
public final class NativeMindWorkerPairSessions implements AutoCloseable {
    public record Endpoint(UUID physicalBodyId, UUID mindId, UUID sessionId) {
        public Endpoint {
            Objects.requireNonNull(physicalBodyId, "physicalBodyId");
            Objects.requireNonNull(mindId, "mindId");
            Objects.requireNonNull(sessionId, "sessionId");
        }
    }

    public record PairHandle(UUID pairId, Endpoint first, Endpoint second) {
        public PairHandle {
            Objects.requireNonNull(pairId, "pairId");
            Objects.requireNonNull(first, "first endpoint");
            Objects.requireNonNull(second, "second endpoint");
            if (first.physicalBodyId().equals(second.physicalBodyId()))
                throw new IllegalArgumentException("A reciprocal pair needs two physical bodies");
            if (first.mindId().equals(second.mindId()))
                throw new IllegalArgumentException("A reciprocal pair needs two distinct minds");
            if (first.sessionId().equals(second.sessionId()))
                throw new IllegalArgumentException("Each mind needs its own worker session identity");
        }
    }

    private static final class ActivePair {
        final PairHandle handle;
        final NativeMindSessionChannel first;
        final NativeMindSessionChannel second;

        ActivePair(PairHandle handle, NativeMindSessionChannel first,
                   NativeMindSessionChannel second) {
            this.handle = handle;
            this.first = first;
            this.second = second;
        }

        NativeMindSessionChannel channel(UUID bodyId) {
            if (handle.first().physicalBodyId().equals(bodyId)) return first;
            if (handle.second().physicalBodyId().equals(bodyId)) return second;
            throw new IllegalArgumentException("Physical body is not a member of this worker pair");
        }

        Endpoint endpoint(UUID bodyId) {
            if (handle.first().physicalBodyId().equals(bodyId)) return handle.first();
            if (handle.second().physicalBodyId().equals(bodyId)) return handle.second();
            throw new IllegalArgumentException("Physical body is not a member of this worker pair");
        }

        boolean active() {
            return first.state() == NativeMindSessionChannel.State.ACTIVE
                && second.state() == NativeMindSessionChannel.State.ACTIVE;
        }

        void close() {
            first.close();
            second.close();
        }
    }

    private final Map<UUID, ActivePair> pairs = new HashMap<>();
    private final Map<UUID, UUID> pairByBody = new HashMap<>();
    private boolean closed;

    /**
     * Reserve both physical bodies and create two separate authenticated
     * streams. The keys are copied by the channels and must be different.
     */
    public synchronized PairHandle openPair(UUID pairId,
            UUID firstBody, UUID firstMind, byte[] firstKey,
            UUID secondBody, UUID secondMind, byte[] secondKey,
            long firstSensoryTick, int replyDeadlineTicks) {
        requireOpen();
        Objects.requireNonNull(pairId, "pairId");
        Objects.requireNonNull(firstBody, "firstBody");
        Objects.requireNonNull(firstMind, "firstMind");
        Objects.requireNonNull(secondBody, "secondBody");
        Objects.requireNonNull(secondMind, "secondMind");
        if (pairs.containsKey(pairId))
            throw new IllegalStateException("Worker pair ID is already active");
        if (firstBody.equals(secondBody))
            throw new IllegalArgumentException("A physical body cannot host both minds");
        if (firstMind.equals(secondMind))
            throw new IllegalArgumentException("Reciprocal minds must have distinct identities");
        if (pairByBody.containsKey(firstBody) || pairByBody.containsKey(secondBody))
            throw new IllegalStateException("A physical body is already reserved by another worker pair");
        requireKey(firstKey, "first mind");
        requireKey(secondKey, "second mind");
        if (MessageDigest.isEqual(firstKey, secondKey))
            throw new IllegalArgumentException("Reciprocal worker streams require independent keys");

        UUID firstSession = uniqueSessionId();
        UUID secondSession = uniqueSessionId(firstSession);
        PairHandle handle = new PairHandle(pairId,
            new Endpoint(firstBody, firstMind, firstSession),
            new Endpoint(secondBody, secondMind, secondSession));
        NativeMindSessionChannel first = new NativeMindSessionChannel(
            firstSession, firstBody, firstMind, firstKey,
            firstSensoryTick, replyDeadlineTicks);
        NativeMindSessionChannel second = new NativeMindSessionChannel(
            secondSession, secondBody, secondMind, secondKey,
            firstSensoryTick, replyDeadlineTicks);
        pairs.put(pairId, new ActivePair(handle, first, second));
        pairByBody.put(firstBody, pairId);
        pairByBody.put(secondBody, pairId);
        return handle;
    }

    /** Begin one mind's next request without blocking or advancing its partner. */
    public synchronized NativeMindSessionChannel.Request begin(
            UUID pairId, UUID physicalBodyId, NativeMindSenseFrame senses,
            long hostTick) {
        ActivePair pair = requirePair(pairId);
        NativeMindSessionChannel channel = pair.channel(physicalBodyId);
        try {
            return channel.begin(senses, hostTick);
        } catch (RuntimeException failure) {
            retireIfFaulted(pairId, pair, channel);
            throw failure;
        }
    }

    /**
     * Authenticate and admit one body-specific result. A stale, replayed,
     * cross-session or malformed response faults and retires both directions.
     */
    public synchronized CapturedIntentBridge.Frame complete(
            UUID pairId, UUID physicalBodyId, byte[] responseWire,
            long receivedAtHostTick) {
        ActivePair pair = requirePair(pairId);
        NativeMindSessionChannel channel = pair.channel(physicalBodyId);
        try {
            return channel.complete(responseWire, receivedAtHostTick);
        } catch (RuntimeException failure) {
            retire(pairId, pair);
            throw failure;
        }
    }

    /** Expire one stream. Its timeout retires the complete reciprocal pair. */
    public synchronized boolean expire(
            UUID pairId, UUID physicalBodyId, long currentHostTick) {
        ActivePair pair = requirePair(pairId);
        NativeMindSessionChannel channel = pair.channel(physicalBodyId);
        boolean expired = channel.expire(currentHostTick);
        if (expired) retire(pairId, pair);
        return expired;
    }

    /** Expire every outstanding direction against the same authoritative server tick. */
    public synchronized int expireAll(long currentHostTick) {
        requireOpen();
        int expiredPairs = 0;
        for (var entry : Set.copyOf(pairs.entrySet())) {
            ActivePair pair = entry.getValue();
            boolean firstExpired = pair.first.expire(currentHostTick);
            boolean secondExpired = pair.second.expire(currentHostTick);
            if (firstExpired || secondExpired) {
                retire(entry.getKey(), pair);
                expiredPairs++;
            }
        }
        return expiredPairs;
    }

    public synchronized Endpoint endpoint(UUID pairId, UUID physicalBodyId) {
        return requirePair(pairId).endpoint(physicalBodyId);
    }

    public synchronized UUID pairForBody(UUID physicalBodyId) {
        return pairByBody.get(Objects.requireNonNull(physicalBodyId, "physicalBodyId"));
    }

    public synchronized boolean isBodyReserved(UUID physicalBodyId) {
        return pairByBody.containsKey(Objects.requireNonNull(physicalBodyId, "physicalBodyId"));
    }

    public synchronized boolean hasOutstandingRequest(UUID pairId, UUID physicalBodyId) {
        return requirePair(pairId).channel(physicalBodyId).hasOutstandingRequest();
    }

    public synchronized int activePairs() {
        return pairs.size();
    }

    /** Cancel both streams together and release both original physical bodies. */
    public synchronized boolean closePair(UUID pairId) {
        ActivePair pair = pairs.get(Objects.requireNonNull(pairId, "pairId"));
        if (pair == null) return false;
        retire(pairId, pair);
        return true;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        for (var entry : Set.copyOf(pairs.entrySet()))
            retire(entry.getKey(), entry.getValue());
        closed = true;
    }

    private void retireIfFaulted(UUID pairId, ActivePair pair,
                                 NativeMindSessionChannel changed) {
        if (changed.state() == NativeMindSessionChannel.State.FAULTED)
            retire(pairId, pair);
    }

    private void retire(UUID pairId, ActivePair pair) {
        if (!pairs.remove(pairId, pair)) return;
        pair.close();
        pairByBody.remove(pair.handle.first().physicalBodyId(), pairId);
        pairByBody.remove(pair.handle.second().physicalBodyId(), pairId);
    }

    private ActivePair requirePair(UUID pairId) {
        requireOpen();
        ActivePair pair = pairs.get(Objects.requireNonNull(pairId, "pairId"));
        if (pair == null) throw new IllegalStateException("Reciprocal worker pair is not active");
        return pair;
    }

    private void requireOpen() {
        if (closed) throw new IllegalStateException("Worker pair registry is closed");
    }

    private static void requireKey(byte[] key, String label) {
        if (key == null || key.length != NativeMindSenseWireCodec.KEY_BYTES)
            throw new IllegalArgumentException(label + " key must be 256-bit");
    }

    private UUID uniqueSessionId(UUID... excluded) {
        Set<UUID> ids = new HashSet<>();
        for (UUID id : excluded) ids.add(Objects.requireNonNull(id, "excluded session ID"));
        for (ActivePair pair : pairs.values()) {
            ids.add(pair.handle.first().sessionId());
            ids.add(pair.handle.second().sessionId());
        }
        UUID generated;
        do { generated = UUID.randomUUID(); }
        while (ids.contains(generated));
        return generated;
    }
}
