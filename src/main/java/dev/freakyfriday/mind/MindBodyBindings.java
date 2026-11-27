package dev.freakyfriday.mind;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Server-thread ownership map separating native AI controller hosts from
 * physical Mob bodies. A normal mob is initially its own controller and body.
 *
 * Exchanging bodies swaps only the controller UUID assignments. This class
 * never copies or mutates native Goals, Brain objects, body fields, or AI
 * capabilities. The Mob serverAiStep bridge must consume this mapping before
 * the assignment can affect live gameplay.
 */
public final class MindBodyBindings {
    private static final Map<UUID, UUID> CONTROLLER_BY_BODY = new HashMap<>();
    private static final Map<UUID, UUID> BODY_BY_CONTROLLER = new HashMap<>();

    private MindBodyBindings() {}

    /**
     * Registers a newly observed body with its original controller.
     * Idempotent: it never overwrites an existing exchanged assignment.
     */
    public static synchronized void ensureOriginalBinding(UUID bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        if (CONTROLLER_BY_BODY.containsKey(bodyId)) return;
        CONTROLLER_BY_BODY.put(bodyId, bodyId);
        BODY_BY_CONTROLLER.put(bodyId, bodyId);
        verifyInvariant();
    }

    /** The native controller host assigned to this physical body. */
    public static synchronized UUID controllerForBody(UUID bodyId) {
        Objects.requireNonNull(bodyId, "bodyId");
        ensureOriginalBinding(bodyId);
        return CONTROLLER_BY_BODY.get(bodyId);
    }

    /** The physical body currently driven by this native controller host. */
    public static synchronized UUID bodyForController(UUID controllerId) {
        Objects.requireNonNull(controllerId, "controllerId");
        UUID body = BODY_BY_CONTROLLER.get(controllerId);
        return body;
    }

    /**
     * Atomically exchanges controller assignments between two physical bodies.
     * There is intentionally no species, entity-class, AI-type, or capability
     * compatibility predicate in this transaction.
     */
    public static synchronized Exchange exchangeBodies(UUID firstBody, UUID secondBody) {
        Objects.requireNonNull(firstBody, "firstBody");
        Objects.requireNonNull(secondBody, "secondBody");
        if (firstBody.equals(secondBody)) {
            throw new IllegalArgumentException("A physical body cannot exchange with itself");
        }

        ensureOriginalBinding(firstBody);
        ensureOriginalBinding(secondBody);

        UUID firstController = CONTROLLER_BY_BODY.get(firstBody);
        UUID secondController = CONTROLLER_BY_BODY.get(secondBody);

        // Prepare the new mapping before mutating either index.
        Map<UUID, UUID> nextControllerByBody = new HashMap<>(CONTROLLER_BY_BODY);
        Map<UUID, UUID> nextBodyByController = new HashMap<>(BODY_BY_CONTROLLER);
        nextControllerByBody.put(firstBody, secondController);
        nextControllerByBody.put(secondBody, firstController);
        nextBodyByController.put(firstController, secondBody);
        nextBodyByController.put(secondController, firstBody);

        CONTROLLER_BY_BODY.clear();
        CONTROLLER_BY_BODY.putAll(nextControllerByBody);
        BODY_BY_CONTROLLER.clear();
        BODY_BY_CONTROLLER.putAll(nextBodyByController);
        verifyInvariant();

        return new Exchange(firstBody, secondBody, firstController, secondController);
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(CONTROLLER_BY_BODY, BODY_BY_CONTROLLER);
    }

    private static void verifyInvariant() {
        if (CONTROLLER_BY_BODY.size() != BODY_BY_CONTROLLER.size()) {
            throw new IllegalStateException("Mind/body ownership indexes have different sizes");
        }
        Set<UUID> uniqueControllers = new HashSet<>(CONTROLLER_BY_BODY.values());
        if (uniqueControllers.size() != CONTROLLER_BY_BODY.size()) {
            throw new IllegalStateException("A native controller is assigned to more than one body");
        }
        for (Map.Entry<UUID, UUID> entry : CONTROLLER_BY_BODY.entrySet()) {
            UUID inverse = BODY_BY_CONTROLLER.get(entry.getValue());
            if (!entry.getKey().equals(inverse)) {
                throw new IllegalStateException("Mind/body ownership indexes are not inverse mappings");
            }
        }
    }

    public record Exchange(UUID firstBody, UUID secondBody,
                            UUID firstController, UUID secondController) {}

    public record Snapshot(Map<UUID, UUID> controllerByBody,
                           Map<UUID, UUID> bodyByController) {
        public Snapshot {
            controllerByBody = Collections.unmodifiableMap(new HashMap<>(controllerByBody));
            bodyByController = Collections.unmodifiableMap(new HashMap<>(bodyByController));
        }
    }
}
