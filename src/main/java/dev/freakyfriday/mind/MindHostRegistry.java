package dev.freakyfriday.mind;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;

/**
 * Dynamic, species-independent registry of native Mob AI hosts.
 *
 * The registry is confined to the logical server thread. It keeps weak
 * references to original Mob hosts so later controller/body binding can
 * preserve the host's real Goals, Brain, callbacks and mod-added state.
 * Observation is passive and does not change native AI execution.
 */
public final class MindHostRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private record HostRef(WeakReference<Mob> mob, String entityType) {}

    // Mob AI phases run on the logical server thread, so concurrent collections
    // and atomic counters add overhead without providing useful safety here.
    private static final Map<UUID, HostRef> HOSTS = new HashMap<>();
    private static final Map<String, Long> AI_PHASES_BY_TYPE = new HashMap<>();
    private static boolean hookActivationLogged;

    private MindHostRegistry() {}

    /**
     * Called at the start of Mob.serverAiStep on the logical server.
     * Hot path is O(1), performs no reflection, and allocates only on first sight.
     */
    private static void registerHost(Mob host) {
        if (host == null || host.level().isClientSide()) return;
        UUID id = host.getUUID();
        HostRef reference = HOSTS.get(id);
        if (reference == null || reference.mob().get() != host) {
            String entityType = host.getType().toString();
            reference = new HostRef(new WeakReference<>(host), entityType);
            HOSTS.put(id, reference);
            MindBodyBindings.ensureOriginalBinding(id);
            LOGGER.debug("Freaky Friday registered native AI host {} ({})", id, entityType);
        }
    }

    /**
     * Called from ServerLevel.tickNonPassenger before the Mob's concrete tick.
     * The original controller's subclass tick executes under a scoped context;
     * Mob.tick/LivingEntity.tick mixins route its superclass physical tick to
     * the recipient body.
     */
    public static void tickAssignedController(Mob physicalBody) {
        if (physicalBody == null || physicalBody.level().isClientSide()) return;
        registerHost(physicalBody);

        UUID bodyId = physicalBody.getUUID();
        UUID controllerId = MindBodyBindings.controllerForBody(bodyId);
        if (bodyId.equals(controllerId)) {
            physicalBody.tick();
            return;
        }

        Mob controller = resolve(controllerId);
        if (controller == null || controller.isRemoved()) {
            // Keep the body ticking safely. The missing host is logged and
            // the ownership mapping is retained for recovery; never reset it
            // or reject the pair because the controller is temporarily absent.
            LOGGER.error("Freaky Friday cannot currently resolve controller {} for body {}; native body tick is used as a temporary fallback",
                controllerId, bodyId);
            physicalBody.tick();
            return;
        }

        try (MindBodyContext.Scope ignored = MindBodyContext.bind(controller, physicalBody)) {
            controller.tick();
        }
    }

    public static void observeServerAiPhase(Mob host) {
        if (host == null || host.level().isClientSide()) return;
        registerHost(host);

        if (!hookActivationLogged) {
            hookActivationLogged = true;
            LOGGER.info("Freaky Friday 26.2 native Mob AI observer is active");
        }

        HostRef reference = HOSTS.get(host.getUUID());
        if (reference != null) {
            AI_PHASES_BY_TYPE.merge(reference.entityType(), 1L, Long::sum);
        }
    }

    /** Resolves an original controller host while it remains strongly reachable elsewhere. */
    public static Mob resolve(UUID hostId) {
        HostRef reference = HOSTS.get(hostId);
        Mob host = reference == null ? null : reference.mob().get();
        if (host == null && reference != null) HOSTS.remove(hostId, reference);
        return host;
    }

    /** Lightweight diagnostic snapshot; no live entity objects escape. */
    public static List<TypeObservation> observedTypes() {
        List<TypeObservation> result = new ArrayList<>(AI_PHASES_BY_TYPE.size());
        AI_PHASES_BY_TYPE.forEach((type, ticks) -> result.add(new TypeObservation(type, ticks)));
        result.sort(Comparator.comparing(TypeObservation::entityType));
        return List.copyOf(result);
    }

    public static int liveHostCount() {
        int count = 0;
        var iterator = HOSTS.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().mob().get() == null) iterator.remove();
            else count++;
        }
        return count;
    }

    public record TypeObservation(String entityType, long aiPhaseCount) {}
}
