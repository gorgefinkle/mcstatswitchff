package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Species-agnostic registry of recipient-body action capabilities.
 *
 * Providers are registered for ACTIONS, not pairings of source/target mob
 * types. Optional mod integrations can register new action capabilities
 * without adding an entry for every possible donor-recipient pair.
 */
public final class BodyCapabilityRegistry<B> {
    public interface Executor<B> {
        boolean supports(B body);
        BodyActionGate.Result perform(B body, BodyActionGate.Intent intent);
    }

    private final Map<String, List<Executor<B>>> providers = new LinkedHashMap<>();

    public synchronized void register(String actionId, Executor<B> implementation) {
        new BodyActionGate.Intent(actionId); // validates namespaced action syntax
        Objects.requireNonNull(implementation, "implementation");
        providers.computeIfAbsent(actionId, ignored -> new ArrayList<>())
            .add(implementation);
    }

    public synchronized boolean supports(B body, String actionId) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(actionId, "action ID");
        for (Executor<B> provider : providers.getOrDefault(actionId, List.of())) {
            if (provider.supports(body)) return true;
        }
        return false;
    }

    public synchronized BodyActionGate.Result execute(B body, BodyActionGate.Intent intent) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(intent, "intent");
        for (Executor<B> provider : providers.getOrDefault(intent.actionId(), List.of())) {
            if (!provider.supports(body)) continue;
            BodyActionGate.Result result = Objects.requireNonNull(provider.perform(body, intent),
                "Capability provider cannot return null");
            if (result.status() == BodyActionGate.Status.UNAVAILABLE)
                throw new IllegalStateException("A matching physical capability must report "
                    + "EXECUTED or BLOCKED, not UNAVAILABLE");
            return result;
        }
        return new BodyActionGate.Result(BodyActionGate.Status.UNAVAILABLE,
            "Body does not provide " + intent.actionId());
    }

    public synchronized Map<String, Integer> registeredActions() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        providers.forEach((action, implementations) -> counts.put(action, implementations.size()));
        return Map.copyOf(counts);
    }
}
