package dev.freakyfriday.mind;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * A mind proposes actions; its assigned living BODY decides which actions it
 * can execute. Nothing in this engine classifies cow/creeper/zombie species.
 *
 * IMPORTANT: This is the semantic command boundary, not a runtime replacement
 * for native Minecraft brains, goals, player movement or network packets.
 * A future Minecraft BodyPort must consult the body's real capabilities and
 * dispatch through its native code; never fake an unavailable ability.
 *
 * Body stats, health, loot, collision, inventory, species interactions and
 * entity identity are never copied into a mind and never swapped here.
 */
public final class BodyActionGate {
    private BodyActionGate() {}

    /** Registry-style action ID plus parameters chosen by the current mind. */
    public record Intent(String actionId, Map<String, String> arguments) {
        public Intent {
            Objects.requireNonNull(actionId, "actionId");
            if (!actionId.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
                throw new IllegalArgumentException("Expected a namespaced action id: " + actionId);
            arguments = Map.copyOf(Objects.requireNonNull(arguments, "arguments"));
            if (arguments.size() > 64)
                throw new IllegalArgumentException("Too many action arguments");
            for (var entry : arguments.entrySet()) {
                if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null)
                    throw new IllegalArgumentException("Invalid action argument");
            }
        }
        public Intent(String actionId) { this(actionId, Map.of()); }
    }

    public enum Status {
        /** Body successfully carried out the mind's request. */
        EXECUTED,
        /** Body has no corresponding physical mechanism. This is NOT a failed mind swap. */
        UNAVAILABLE,
        /** Body has the ability but cannot act now (cooldown, protections, state, etc.). */
        BLOCKED
    }

    public record Result(Status status, String reason) {
        public Result {
            Objects.requireNonNull(status, "status");
            reason = Objects.requireNonNull(reason, "reason");
        }
        public static Result executed() { return new Result(Status.EXECUTED, ""); }
        public static Result blocked(String reason) { return new Result(Status.BLOCKED, reason); }
    }

    /** The body's server-side native implementation, not a donor-mob disguise. */
    public interface BodyPort {
        UUID id();
        /** Consult actual body-owned abilities, never mind identity. */
        boolean supports(String actionId);
        /** Execute against THIS body's native state and limits. */
        Result perform(Intent intent);
    }

    /**
     * No species pair whitelist or goal/attribute menu. A mind may request any
     * action; missing anatomy is a normal UNAVAILABLE result, not a reason to
     * reject the complete mind assignment. Never invoke perform on another body.
     */
    public static Result dispatch(BodyPort body, Intent intention) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(intention, "intention");
        Objects.requireNonNull(body.id(), "body UUID");
        if (!body.supports(intention.actionId()))
            return new Result(Status.UNAVAILABLE,
                "Body cannot execute " + intention.actionId());
        Result result = Objects.requireNonNull(body.perform(intention),
            "BodyPort must return an execution result");
        if (result.status() == Status.UNAVAILABLE)
            throw new IllegalStateException("BodyPort claimed capability but reported unavailable: "
                + intention.actionId());
        return result;
    }

    /**
     * Body ownership of capabilities is independent from the identity of the
     * current controller. Keep this routine intentionally free of mob classes.
     */
    public static Map<String, String> snapshotArguments(Intent intent) {
        return Map.copyOf(new LinkedHashMap<>(intent.arguments()));
    }
}
