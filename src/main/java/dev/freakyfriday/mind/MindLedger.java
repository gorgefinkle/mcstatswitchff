package dev.freakyfriday.mind;

import dev.freakyfriday.core.CategoryExchange;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Logical, authoritative body-to-mind ownership. The entire controller moves
 * as ONE unit; this registry deliberately knows nothing about individual goals,
 * attributes, skills or powers.
 *
 * Runtime drivers must implement actual Minecraft behavior and suppress each
 * body's native AI. A successful ledger swap alone is NOT a gameplay swap.
 */
public final class MindLedger {
    public enum Kind { PLAYER, NPC }

    /** Stable mind identity and original controller type, not a body disguise. */
    public record Mind(UUID identity, Kind kind, String nativeProfile) {
        public Mind {
            Objects.requireNonNull(identity, "mind identity");
            Objects.requireNonNull(kind, "controller kind");
            Objects.requireNonNull(nativeProfile, "native profile");
            if (nativeProfile.isBlank()) throw new IllegalArgumentException("Mind profile cannot be blank");
        }
    }

    /**
     * Verify that the complete incoming controller can run on its recipient
     * body. Runtime adapters must reject if ANY behavior is not transferable.
     * This method must not mutate either body or ledger.
     */
    @FunctionalInterface
    public interface Compatibility {
        void validate(UUID recipientBody, Mind incoming) throws Exception;
    }

    private final Map<UUID, Mind> byBody = new LinkedHashMap<>();

    /** Called once when a body is first admitted to server-owned mind state. */
    public synchronized void registerOriginal(UUID body, Mind mind) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(mind, "mind");
        if (byBody.containsKey(body))
            throw new IllegalStateException("Body already has a mind: " + body);
        if (byBody.values().stream().anyMatch(existing -> existing.identity().equals(mind.identity())))
            throw new IllegalStateException("Mind already controls a different body: " + mind.identity());
        byBody.put(body, mind);
    }

    public synchronized Mind controlling(UUID body) {
        Mind mind = byBody.get(Objects.requireNonNull(body, "body"));
        if (mind == null) throw new IllegalArgumentException("Body has no registered mind: " + body);
        return mind;
    }

    public synchronized Optional<UUID> bodyControlledBy(UUID identity) {
        Objects.requireNonNull(identity, "mind identity");
        return byBody.entrySet().stream()
            .filter(entry -> entry.getValue().identity().equals(identity))
            .map(Map.Entry::getKey)
            .findFirst();
    }

    public synchronized Map<UUID, Mind> snapshot() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(byBody));
    }

    /**
     * Atomically exchange the whole controller assignments. This uses the same
     * preflight / read-back / rollback engine as the existing category tests.
     * Call ONLY on the logical server thread. Runtime bridge must pair it with
     * actual controller activation/deactivation and persistence atomically.
     */
    public synchronized void exchange(UUID firstBody, UUID secondBody, Compatibility compatibility)
            throws Exception {
        Objects.requireNonNull(firstBody, "first body");
        Objects.requireNonNull(secondBody, "second body");
        Objects.requireNonNull(compatibility, "compatibility");
        if (firstBody.equals(secondBody))
            throw new IllegalArgumentException("Cannot exchange a mind with the same body");
        controlling(firstBody);
        controlling(secondBody);

        CategoryExchange.exchange(firstBody, secondBody, new CategoryExchange.Port<UUID, Mind>() {
            @Override
            public Mind snapshot(UUID body) {
                return controlling(body);
            }

            @Override
            public void validate(UUID recipient, Mind incoming) throws Exception {
                compatibility.validate(recipient, incoming);
            }

            @Override
            public void replace(UUID recipient, Mind incoming) {
                byBody.put(recipient, incoming);
            }

            @Override
            public void restore(UUID recipient, Mind original) {
                byBody.put(recipient, original);
            }
        });
        // One mind is still assigned to exactly one body after a successful exchange.
        if (byBody.values().stream().map(Mind::identity).distinct().count() != byBody.size())
            throw new IllegalStateException("Duplicate controller identity after mind exchange");
    }

    /** Restore the two original identities without rolling back other active pairs. */
    public synchronized void restorePair(UUID firstBody, Mind firstMind,
                                         UUID secondBody, Mind secondMind) {
        Objects.requireNonNull(firstBody,"first body");
        Objects.requireNonNull(secondBody,"second body");
        Objects.requireNonNull(firstMind,"first mind");
        Objects.requireNonNull(secondMind,"second mind");
        if(firstBody.equals(secondBody)||firstMind.identity().equals(secondMind.identity()))
            throw new IllegalArgumentException("A restored pair needs two distinct bodies and minds");
        for(var entry:byBody.entrySet()) {
            if(entry.getKey().equals(firstBody)||entry.getKey().equals(secondBody)) continue;
            if(entry.getValue().identity().equals(firstMind.identity())
                    ||entry.getValue().identity().equals(secondMind.identity()))
                throw new IllegalStateException("A mind identity is still assigned to another body");
        }
        byBody.put(firstBody,firstMind);
        byBody.put(secondBody,secondMind);
    }
}
