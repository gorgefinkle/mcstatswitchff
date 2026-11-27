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
    public static void observeServerAiPhase(Mob host) {
        if (host == null || host.level().isClientSide()) return;

        if (!hookActivationLogged) {
            hookActivationLogged = true;
            LOGGER.info("Freaky Friday 26.2 native Mob AI observer is active");
        }

        UUID id = host.getUUID();
        HostRef reference = HOSTS.get(id);
        if (reference == null || reference.mob().get() != host) {
            String entityType = host.getType().toString();
            reference = new HostRef(new WeakReference<>(host), entityType);
            HOSTS.put(id, reference);
            MindBodyBindings.ensureOriginalBinding(id);
            LOGGER.debug("Freaky Friday registered native AI host {} ({})", id, entityType);
        }

        AI_PHASES_BY_TYPE.merge(reference.entityType(), 1L, Long::sum);
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
