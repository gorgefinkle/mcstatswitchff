package dev.freakyfriday.mind;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.entity.Mob;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

/**
 * Dynamic, species-independent registry of native Mob AI hosts.
 *
 * This registry deliberately does not clone Goals or Brain objects. It keeps
 * weak references to the original Mob hosts so the next implementation stage
 * can bind each host to a different physical body without losing owner-bound
 * native AI objects. Observation is passive: it does not change AI execution.
 */
public final class MindHostRegistry {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<UUID, WeakReference<Mob>> HOSTS = new ConcurrentHashMap<>();
    private static final Map<String, AtomicLong> AI_PHASES_BY_TYPE = new ConcurrentHashMap<>();

    private MindHostRegistry() {}

    /**
     * Called at the start of Mob.serverAiStep on the logical server.
     * The operation is constant-time and does not reflect over AI objects.
     */
    public static void observeServerAiPhase(Mob host) {
        if (host == null || host.level().isClientSide()) return;

        UUID id = host.getUUID();
        WeakReference<Mob> current = HOSTS.get(id);
        if (current == null || current.get() != host) {
            HOSTS.put(id, new WeakReference<>(host));
            LOGGER.debug("Freaky Friday registered native AI host {} ({})",
                id, host.getType());
        }

        AI_PHASES_BY_TYPE.computeIfAbsent(host.getType().toString(), ignored -> new AtomicLong())
            .incrementAndGet();
    }

    /** Resolves an original controller host while it remains strongly reachable elsewhere. */
    public static Mob resolve(UUID hostId) {
        WeakReference<Mob> reference = HOSTS.get(hostId);
        Mob host = reference == null ? null : reference.get();
        if (host == null && reference != null) HOSTS.remove(hostId, reference);
        return host;
    }

    /** Lightweight diagnostic snapshot; no live entity objects escape. */
    public static List<TypeObservation> observedTypes() {
        List<TypeObservation> result = new ArrayList<>(AI_PHASES_BY_TYPE.size());
        AI_PHASES_BY_TYPE.forEach((type, ticks) ->
            result.add(new TypeObservation(type, ticks.get())));
        result.sort(Comparator.comparing(TypeObservation::entityType));
        return List.copyOf(result);
    }

    public static int liveHostCount() {
        int count = 0;
        for (Map.Entry<UUID, WeakReference<Mob>> entry : HOSTS.entrySet()) {
            if (entry.getValue().get() == null) HOSTS.remove(entry.getKey(), entry.getValue());
            else count++;
        }
        return count;
    }

    public record TypeObservation(String entityType, long aiPhaseCount) {}
}
