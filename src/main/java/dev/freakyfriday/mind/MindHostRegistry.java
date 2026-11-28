package dev.freakyfriday.mind;

import com.mojang.logging.LogUtils;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    // Disabled in normal play; only operator diagnostics opt a Mob into counting.
    private static final Set<UUID> DEBUG_TICK_WATCH = new HashSet<>();
    private static final Map<UUID, Long> DEBUG_NATIVE_MOB_TICKS = new HashMap<>();
    private static boolean debugTickCountingActive;
    private static boolean hookActivationLogged;

    private MindHostRegistry() {}

    /**
     * Called at the start of Mob.serverAiStep on the logical server.
     * Hot path is O(1), performs no reflection, and allocates only on first sight.
     */
    public static void registerNativeHost(Mob host) {
        registerHost(host);
    }

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

        prepareControllerTick(controller, physicalBody);

        try (MindBodyContext.Scope ignored = MindBodyContext.bind(controller, physicalBody)) {
            // First run the donor's concrete tick override. MobTickBodyBridgeMixin
            // suppresses only this controller invocation's LivingEntity super-tick,
            // preserving subclass-owned mind state without double-ticking the body.
            controller.tick();

            // Run only the recipient's physical LivingEntity tick, not its
            // concrete Mob/subclass tick. Every Mob object is both a physical
            // entity and one controller host, so invoking physicalBody.tick()
            // here would execute that same subclass tick a second time when
            // its own entity-manager turn arrives. The LivingEntity superclass
            // still performs baseTick, effects, travel, collision and aiStep;
            // its aiStep is bridged to the donor and then to recipient body
            // capabilities by LivingEntityAiStepBodyBridgeMixin.
            ((LivingEntityTickInvoker) (Object) physicalBody)
                .freakyfriday$invokeLivingEntityTick();
        }
    }

    /**
     * Advance the original controller's AI clock and interpolation snapshots
     * from the physical body it now inhabits. Used for both Mob and Player bodies.
     */
    public static void prepareControllerTick(Mob controller, net.minecraft.world.entity.Entity physicalBody) {
        // ServerLevel increments every loaded entity's tickCount exactly once
        // before dispatch. The controller host is still an entity in that
        // manager, so do NOT increment it here or it will count twice per
        // world tick. Its own manager turn advances the same native host clock.
        controller.xo = physicalBody.xo;
        controller.yo = physicalBody.yo;
        controller.zo = physicalBody.zo;
        controller.xOld = physicalBody.xOld;
        controller.yOld = physicalBody.yOld;
        controller.zOld = physicalBody.zOld;
        controller.yRotO = physicalBody.yRotO;
        controller.xRotO = physicalBody.xRotO;
    }

    /**
     * Species-agnostic NPC-to-NPC controller assignment. No native Goal/Brain
     * object is transplanted; original Mob hosts retain their native runtimes.
     */
    public static void exchangeMobControllers(Mob first, Mob second) {
        if (first == null || second == null) throw new IllegalArgumentException("Two Mob bodies are required");
        if (first == second) throw new IllegalArgumentException("Choose two different Mob bodies");
        if (!first.isAlive() || !second.isAlive()) throw new IllegalArgumentException("Both bodies must be alive");
        if (first.level() != second.level()) throw new IllegalArgumentException("Both bodies must be in the same dimension");
        registerHost(first);
        registerHost(second);
        MindBodyBindings.exchangeBodies(first.getUUID(), second.getUUID());
    }

    /**
     * Operator-only development hook. Exchanges the original native controller
     * assignments without involving the book UI or changing body state.
     */
    public static void debugExchangeControllers(Mob first, Mob second) {
        if (first == null || second == null) throw new IllegalArgumentException("Two Mob bodies are required");
        if (first == second) throw new IllegalArgumentException("Choose two different Mob bodies");
        registerHost(first);
        registerHost(second);
        MindBodyBindings.Exchange exchange = MindBodyBindings.exchangeBodies(first.getUUID(), second.getUUID());
        UUID firstControllerNow = MindBodyBindings.controllerForBody(exchange.firstBody());
        UUID secondControllerNow = MindBodyBindings.controllerForBody(exchange.secondBody());
        LOGGER.info("Freaky Friday DEBUG exchange result: body {} ({}) now runs controller {} ({}); body {} ({}) now runs controller {} ({})",
            exchange.firstBody(), first.getType(), firstControllerNow, typeOf(firstControllerNow),
            exchange.secondBody(), second.getType(), secondControllerNow, typeOf(secondControllerNow));
    }

    private static String typeOf(UUID controllerId) {
        Mob host = resolve(controllerId);
        return host == null ? "unloaded" : host.getType().toString();
    }

    /** Invokes one real native controller/body tick from an operator command on the server thread. */
    public static void debugTickBody(Mob physicalBody) {
        if (physicalBody == null) throw new IllegalArgumentException("A Mob body is required");
        UUID controllerId = MindBodyBindings.controllerForBody(physicalBody.getUUID());
        Mob controller = resolve(controllerId);
        int before = controller == null ? -1 : controller.tickCount;
        tickAssignedController(physicalBody);
        int after = controller == null ? -1 : controller.tickCount;
        LOGGER.info("Freaky Friday DEBUG tick: body {} ({}) ran controller {} ({}) tickCount {} -> {}",
            physicalBody.getUUID(), physicalBody.getType(), controllerId, typeOf(controllerId), before, after);
    }

    /** Start a bounded diagnostic counter for one selected Mob's concrete Mob.tick calls. */
    public static void startDebugMobTickCounter(Mob mob) {
        if (mob == null) throw new IllegalArgumentException("A Mob is required");
        UUID id = mob.getUUID();
        DEBUG_TICK_WATCH.add(id);
        DEBUG_NATIVE_MOB_TICKS.put(id, 0L);
        debugTickCountingActive = true;
    }

    /** Called by the Mob.tick HEAD Mixin; normal play takes one predictable boolean branch. */
    public static void countDebugMobTick(Mob mob) {
        if (!debugTickCountingActive || mob == null) return;
        UUID id = mob.getUUID();
        if (DEBUG_TICK_WATCH.contains(id))
            DEBUG_NATIVE_MOB_TICKS.merge(id, 1L, Long::sum);
    }

    public static long debugMobTickCount(Mob mob) {
        if (mob == null) return -1L;
        return DEBUG_NATIVE_MOB_TICKS.getOrDefault(mob.getUUID(), -1L);
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
