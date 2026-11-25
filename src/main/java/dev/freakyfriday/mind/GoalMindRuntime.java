package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Dynamic whole-goal AI capturer. No hardcoded species names, goal subclasses,
 * recipe tables, food lists or per-species pairing exceptions.
 *
 * Captures EVERY goal and targeting goal at the instant of selection, including
 * priority, concrete class and reflected object state. Each captured Goal is
 * reconstructed with ALL of its owner-bound services redirected to the new
 * recipient body before either native goal selector is touched.
 *
 * The capturer fails CLOSED when a field, opaque callback or recipient-specific
 * dependency cannot be rebound. It does not falsely promise that arbitrary
 * opaque AI hooks, customServerAiStep or mixed Brain+Goal mobs are portable.
 *
 * No persistence across unload/restart in this experimental checkpoint.
 */
public final class GoalMindRuntime {
    private GoalMindRuntime() {}

    private static final MindLedger OWNERS = new MindLedger();

    private record Slot(int priority, Goal goal) {}
    private record Snapshot(List<Slot> goals, List<Slot> targets) {
        Snapshot {
            goals = List.copyOf(goals);
            targets = List.copyOf(targets);
        }
    }

    /**
     * A mob with a native inert Brain has its behavior in goal/target selectors.
     * Active Brain mobs use the separate Brain driver only when its full graph
     * can be transferred; never silently transfer only goals of a mixed AI.
     */
    public static boolean supports(Mob first, Mob second) {
        return first.getBrain().isBrainDead() && second.getBrain().isBrainDead();
    }

    private static List<Slot> read(GoalSelector selector) {
        List<Slot> entries = new ArrayList<>();
        for (WrappedGoal wrapped : selector.getAvailableGoals())
            entries.add(new Slot(wrapped.getPriority(), wrapped.getGoal()));
        return List.copyOf(entries);
    }

    private static Snapshot snapshot(Mob body) {
        return new Snapshot(read(body.goalSelector), read(body.targetSelector));
    }

    /** Strict identity/priority readback; same classes alone are insufficient. */
    private static boolean identical(GoalSelector selector, List<Slot> expected) {
        var actual = selector.getAvailableGoals();
        if (actual.size() != expected.size()) return false;
        List<Slot> remaining = new ArrayList<>(expected);
        for (WrappedGoal wrapped : actual) {
            boolean found = false;
            for (int i = 0; i < remaining.size(); i++) {
                Slot expectedSlot = remaining.get(i);
                if (expectedSlot.priority() == wrapped.getPriority()
                        && expectedSlot.goal() == wrapped.getGoal()) {
                    remaining.remove(i);
                    found = true;
                    break;
                }
            }
            if (!found) return false;
        }
        return remaining.isEmpty();
    }


    private record CompatibleCapture(Snapshot snapshot,int omittedGoals) {}

    private static boolean recipientTypeMismatch(DynamicObjectGraph.Uncapturable failure) {
        return failure.getMessage()!=null
            &&failure.getMessage().startsWith("AI field incompatible with recipient:");
    }

    /**
     * Keep the donor's complete transferable goal set, but do not install a
     * goal whose declared owner type is not a capability of the recipient
     * body (for example, an Animal-only breeding goal on a Monster body).
     * This is inferred from the actual reflected field types, never from
     * species names. Opaque graphs and non-type-related capture failures still
     * reject the whole exchange.
     */
    private static CompatibleCapture captureCompatibleFor(
            Mob original,Mob recipient,Snapshot donor)
            throws DynamicObjectGraph.Uncapturable {
        return captureCompatibleFor(original,recipient,donor,Set.of());
    }

    private static CompatibleCapture captureCompatibleFor(
            Mob original,Mob recipient,Snapshot donor,Set<UUID> excludedEntities)
            throws DynamicObjectGraph.Uncapturable {
        try {
            return new CompatibleCapture(captureFor(original,recipient,donor,excludedEntities),0);
        } catch(DynamicObjectGraph.Uncapturable wholeGraphFailure) {
            if(!recipientTypeMismatch(wholeGraphFailure)) throw wholeGraphFailure;
        }

        List<Slot> compatibleGoals=new ArrayList<>();
        List<Slot> compatibleTargets=new ArrayList<>();
        int omitted=0;
        for(Slot slot:donor.goals()) {
            try {
                captureFor(original,recipient,new Snapshot(List.of(slot),List.of()),excludedEntities);
                compatibleGoals.add(slot);
            } catch(DynamicObjectGraph.Uncapturable failure) {
                if(!recipientTypeMismatch(failure)) throw failure;
                omitted++;
            }
        }
        for(Slot slot:donor.targets()) {
            try {
                captureFor(original,recipient,new Snapshot(List.of(),List.of(slot)),excludedEntities);
                compatibleTargets.add(slot);
            } catch(DynamicObjectGraph.Uncapturable failure) {
                if(!recipientTypeMismatch(failure)) throw failure;
                omitted++;
            }
        }
        if(compatibleGoals.isEmpty()&&compatibleTargets.isEmpty())
            throw new DynamicObjectGraph.Uncapturable(
                "No native goals are compatible with the recipient body's declared capabilities");
        Snapshot accepted=new Snapshot(compatibleGoals,compatibleTargets);
        return new CompatibleCapture(captureFor(original,recipient,accepted,excludedEntities),omitted);
    }

    private static Snapshot captureFor(Mob original,Mob recipient,Snapshot donor)
            throws DynamicObjectGraph.Uncapturable {
        return captureFor(original,recipient,donor,Set.of());
    }

    private static Snapshot captureFor(Mob original,Mob recipient,Snapshot donor,
            Set<UUID> excludedEntities) throws DynamicObjectGraph.Uncapturable {
        IdentityHashMap<Object, Object> bindings = new IdentityHashMap<>();
        bindings.put(original, recipient);
        bindings.put(original.getNavigation(), recipient.getNavigation());
        bindings.put(original.getMoveControl(), recipient.getMoveControl());
        bindings.put(original.getLookControl(), recipient.getLookControl());
        bindings.put(original.getJumpControl(), recipient.getJumpControl());
        bindings.put(original.getSensing(), recipient.getSensing());
        bindings.put(original.getRandom(), recipient.getRandom());

        DynamicObjectGraph.Rebinding rebinder = new DynamicObjectGraph.Rebinding() {
            @Override
            public Object substitute(Object value) {
                if(bindings.containsKey(value)) return bindings.get(value);
                if(value instanceof Entity entity&&excludedEntities.contains(entity.getUUID()))
                    return null;
                return value;
            }

            @Override
            @SuppressWarnings("unchecked")
            public Object afterCopy(Object source,Object copy) {
                if(!excludedEntities.isEmpty()&&source instanceof Predicate<?>
                        &&copy instanceof Predicate<?> predicate) {
                    return new ExcludingEntityPredicate(
                        (Predicate<Object>)predicate,excludedEntities);
                }
                return copy;
            }

            @Override
            public boolean shared(Object value) {
                // External entities are AI targets, not the source controller.
                // Levels, immutable positions and registry-owned values are
                // world-owned or immutable and never cloned.
                return value instanceof Entity || value instanceof Level
                    // Exact immutable position classes only. A mutable
                    // BlockPos subclass would otherwise leak live AI state.
                    || value.getClass() == BlockPos.class
                    || value.getClass() == Vec3.class
                    || value instanceof Identifier || value instanceof TagKey<?>
                    || value instanceof Item;
            }

            @Override
            public boolean cloneable(Class<?> type) {
                // Capture Goal implementations without a species/class table.
                // For hidden captured lambdas, inspect the enclosing Goal class
                // (their nest host) rather than rejecting every synthetic class.
                // A Goal may delegate to a lambda generated by a utility
                // class (for example EntitySelector). Any captured lambda
                // reachable from this admitted Goal graph is part of the
                // decision graph; DynamicObjectGraph still inspects and copies
                // every capture field and rejects inaccessible/unsafe state.
                if(type.isHidden() || type.isSynthetic()
                        && type.getName().contains("$Lambda")) return true;
                Class<?> owner = type;
                return Goal.class.isAssignableFrom(owner)
                    || owner.getName().startsWith("net.minecraft.world.entity.ai.targeting.")
                    || owner.getName().startsWith("net.minecraft.world.entity.ai.goal.");
            }
        };

        // Capture the entire TWO-selector graph in one traversal, not one
        // goal at a time. Nested references and shared helper instances must
        // retain their original alias relationships across all goals.
        int goalCount = donor.goals().size();
        Goal[] roots = new Goal[goalCount + donor.targets().size()];
        int index = 0;
        for (Slot entry : donor.goals()) roots[index++] = entry.goal();
        for (Slot entry : donor.targets()) roots[index++] = entry.goal();
        Goal[] rebound = DynamicObjectGraph.capture(roots, Goal[].class, rebinder);

        List<Slot> goals = new ArrayList<>(goalCount);
        for (int i = 0; i < goalCount; i++)
            goals.add(new Slot(donor.goals().get(i).priority(), rebound[i]));
        List<Slot> targets = new ArrayList<>(donor.targets().size());
        for (int i = 0; i < donor.targets().size(); i++)
            targets.add(new Slot(donor.targets().get(i).priority(), rebound[goalCount + i]));
        return new Snapshot(goals, targets);
    }

    private static void install(GoalSelector selector, List<Slot> entries) {
        selector.removeAllGoals(goal -> true);
        for (Slot slot : entries) selector.addGoal(slot.priority(), slot.goal());
        if (!identical(selector, entries))
            throw new IllegalStateException("Complete AI goal set did not install exactly");
    }

    private static void apply(Mob body, Snapshot incoming) {
        install(body.goalSelector, incoming.goals());
        install(body.targetSelector, incoming.targets());
    }

    /**
     * The ONLY goal-based exchange entry point. Source discovery and both
     * complete recipient captures are finished before any selector is changed.
     */
    public static synchronized String exchange(Mob first, Mob second) {
        if (first == second || !first.isAlive() || !second.isAlive()
                || first.level() != second.level())
            return "Two different living bodies in the same level are required.";
        if (!supports(first, second))
            return "The mob has active Brain AI beyond goal selectors; "
                + "a complete dynamic transfer needs the Brain runtime as well.";

        Snapshot originalFirst = snapshot(first);
        Snapshot originalSecond = snapshot(second);
        if (originalFirst.goals().isEmpty() && originalFirst.targets().isEmpty())
            return "The first mob has no goal AI to capture.";
        if (originalSecond.goals().isEmpty() && originalSecond.targets().isEmpty())
            return "The second mob has no goal AI to capture.";

        final Snapshot firstReceiving, secondReceiving;
        final int omittedGoals;
        try {
            CompatibleCapture firstCapture=captureCompatibleFor(second,first,originalSecond);
            CompatibleCapture secondCapture=captureCompatibleFor(first,second,originalFirst);
            firstReceiving=firstCapture.snapshot();
            secondReceiving=secondCapture.snapshot();
            omittedGoals=firstCapture.omittedGoals()+secondCapture.omittedGoals();
        } catch (DynamicObjectGraph.Uncapturable failure) {
            return "Dynamic AI capture rejected the exchange: " + failure.getMessage();
        }

        UUID firstId = first.getUUID(), secondId = second.getUUID();
        try {
            if (!OWNERS.snapshot().containsKey(firstId))
                OWNERS.registerOriginal(firstId, new MindLedger.Mind(
                    firstId, MindLedger.Kind.NPC, first.getType().toString()));
            if (!OWNERS.snapshot().containsKey(secondId))
                OWNERS.registerOriginal(secondId, new MindLedger.Mind(
                    secondId, MindLedger.Kind.NPC, second.getType().toString()));
        } catch (Exception error) {
            return "Cannot register unique controller ownership: " + error.getMessage();
        }

        if (!identical(first.goalSelector, originalFirst.goals())
                || !identical(first.targetSelector, originalFirst.targets())
                || !identical(second.goalSelector, originalSecond.goals())
                || !identical(second.targetSelector, originalSecond.targets()))
            return "One mob's AI changed during capture; no swap performed.";

        try {
            apply(first, firstReceiving);
            apply(second, secondReceiving);
            OWNERS.exchange(firstId, secondId, (body, incoming) -> {
                if (incoming.kind() != MindLedger.Kind.NPC)
                    throw new IllegalArgumentException("Incoming mind is not a complete NPC controller");
            });
            if(omittedGoals>0)
                System.out.println("[Freaky Friday] Native mind exchange omitted "
                    +omittedGoals+" goal(s) whose owner types are not supported by "
                    +"the recipient physical bodies.");
            return null;
        } catch (Exception failure) {
            // Goal selectors and the mind ledger have separate ownership.
            // The ledger rolls its own transaction back; restore BOTH native
            // selectors if either installation or the ledger fails.
            try { apply(first, originalFirst); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            try { apply(second, originalSecond); }
            catch (Exception rollback) { failure.addSuppressed(rollback); }
            if (failure.getSuppressed().length != 0)
                return "Dynamic AI exchange FAILED; rollback could not be verified: "
                    + failure.getMessage();
            return "Dynamic AI exchange rejected and original goals restored: "
                + failure.getMessage();
        }
    }


    /** Prevent a detached controller proxy from sensing its own physical bodies as targets. */
    private record ExcludingEntityPredicate(Predicate<Object> delegate,Set<UUID> excluded)
            implements Predicate<Object> {
        ExcludingEntityPredicate {
            excluded=Set.copyOf(excluded);
        }
        @Override public boolean test(Object candidate) {
            return candidate instanceof Entity entity&&excluded.contains(entity.getUUID())
                ? false : delegate.test(candidate);
        }
    }

    private static final Map<UUID,DetachedController> DETACHED_BY_PROXY=new HashMap<>();

    /** One in-process native Mob mind used to pilot an original ServerPlayer body. */
    public static final class DetachedController implements AutoCloseable {
        public record Step(Vec3 displacement,float yaw,float pitch,Entity attackTarget) {}
        private final Mob proxy;
        private final Set<UUID> excluded;
        private Entity pendingAttack;
        private boolean closed;

        private DetachedController(Mob proxy,Set<UUID> excluded) {
            this.proxy=proxy;
            this.excluded=Set.copyOf(excluded);
            DETACHED_BY_PROXY.put(proxy.getUUID(),this);
        }

        public synchronized Step tick(ServerPlayer physicalBody) {
            if(closed||physicalBody==null||!physicalBody.isAlive()
                    ||physicalBody.isRemoved()||proxy.isRemoved())
                return new Step(Vec3.ZERO,physicalBody==null?0:physicalBody.getYRot(),
                    physicalBody==null?0:physicalBody.getXRot(),null);
            Vec3 origin=physicalBody.position();
            proxy.setPos(origin.x,origin.y,origin.z);
            proxy.setYRot(physicalBody.getYRot());
            proxy.setXRot(physicalBody.getXRot());
            proxy.setYHeadRot(physicalBody.getYHeadRot());
            proxy.setOnGround(physicalBody.onGround());
            proxy.setDeltaMovement(Vec3.ZERO);
            pendingAttack=null;
            proxy.tick();
            Vec3 moved=proxy.position().subtract(origin);
            Entity target=proxy.getTarget();
            if(target!=null&&excluded.contains(target.getUUID())) {
                proxy.setTarget(null);
                target=null;
            }
            float yaw=proxy.getYRot(),pitch=proxy.getXRot();
            Entity attack=pendingAttack;
            pendingAttack=null;
            // The proxy is a decision worker only; the only persistent
            // physical entity remains the original ServerPlayer.
            proxy.setPos(origin.x,origin.y,origin.z);
            return new Step(moved,yaw,pitch,attack);
        }

        private synchronized void recordAttack(Entity target) {
            if(!closed&&target!=null&&!excluded.contains(target.getUUID()))
                pendingAttack=target;
        }

        @Override public synchronized void close() {
            if(closed)return;
            closed=true;
            DETACHED_BY_PROXY.remove(proxy.getUUID(),this);
            if(!proxy.isRemoved())proxy.discard();
            pendingAttack=null;
        }
    }

    /**
     * Create an unregistered, same-server AI proxy. It runs the donor Mob's
     * native Goal graph against the real recipient surroundings, while the
     * original Mob body is free for player input. Decisions are projected onto
     * the real ServerPlayer adapter; the proxy has no independent collision,
     * health, inventory, network identity or world registration.
     */
    public static DetachedController createDetachedController(Mob donor,ServerPlayer physicalBody)
            throws DynamicObjectGraph.Uncapturable {
        if(donor==null||physicalBody==null||!donor.isAlive()||!physicalBody.isAlive()
                ||donor.isRemoved()||physicalBody.isRemoved()
                ||donor.level()!=physicalBody.level()
                ||!(donor.level() instanceof ServerLevel level))
            throw new DynamicObjectGraph.Uncapturable(
                "The donor Mob and recipient player must be alive in the same server level");
        if(!supports(donor,donor))
            throw new DynamicObjectGraph.Uncapturable(
                "The donor Mob uses active Brain AI; its complete Brain runtime is not yet transferable");
        Snapshot donorMind=snapshot(donor);
        if(donorMind.goals().isEmpty()&&donorMind.targets().isEmpty())
            throw new DynamicObjectGraph.Uncapturable("The donor Mob has no native Goal controller");
        Mob proxy=donor.getType().create(level,EntitySpawnReason.COMMAND);
        if(proxy==null)
            throw new DynamicObjectGraph.Uncapturable("The donor EntityType could not create a detached AI proxy");
        proxy.setNoAi(false);
        proxy.setNoGravity(true);
        proxy.setInvulnerable(true);
        proxy.setSilent(true);
        proxy.setPersistenceRequired();
        proxy.setPos(physicalBody.getX(),physicalBody.getY(),physicalBody.getZ());
        proxy.setYRot(physicalBody.getYRot());
        proxy.setXRot(physicalBody.getXRot());
        Set<UUID> excluded=Set.of(donor.getUUID(),physicalBody.getUUID());
        try {
            CompatibleCapture capture=captureCompatibleFor(donor,proxy,donorMind,excluded);
            apply(proxy,capture.snapshot());
            return new DetachedController(proxy,excluded);
        } catch(DynamicObjectGraph.Uncapturable|RuntimeException failure) {
            if(!proxy.isRemoved())proxy.discard();
            if(failure instanceof DynamicObjectGraph.Uncapturable unsupported) throw unsupported;
            throw new DynamicObjectGraph.Uncapturable(
                "Could not initialize the complete detached native Mob mind",failure);
        }
    }

    @SubscribeEvent
    public static synchronized void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID playerId=event.getEntity().getUUID();
        for(DetachedController controller:List.copyOf(DETACHED_BY_PROXY.values())) {
            if(controller.excluded.contains(playerId)) controller.close();
        }
    }

    @SubscribeEvent
    public static synchronized void onDetachedProxyDamage(LivingIncomingDamageEvent event) {
        if(!(event.getSource().getEntity() instanceof Mob proxy))return;
        DetachedController controller=DETACHED_BY_PROXY.get(proxy.getUUID());
        if(controller==null)return;
        event.setCanceled(true);
        controller.recordAttack(event.getEntity());
    }

    /**
     * A live exchange keeps the original goal graphs so the operation is
     * reversible. The real mobs remain in the ordinary level and continue
     * ticking their newly-installed native goal selectors. No worker JVM,
     * mirror world, AI freeze or duplicate physical entity is involved.
     */
    private static final class ActiveSwap {
        final UUID requester;
        final ServerLevel level;
        final Mob first, second;
        final Snapshot firstOriginal, secondOriginal;
        final MindLedger.Mind firstMind, secondMind;
        ActiveSwap(UUID requester, ServerLevel level, Mob first, Mob second,
                Snapshot firstOriginal, Snapshot secondOriginal,
                MindLedger.Mind firstMind, MindLedger.Mind secondMind) {
            this.requester=requester;
            this.level=level;
            this.first=first;
            this.second=second;
            this.firstOriginal=firstOriginal;
            this.secondOriginal=secondOriginal;
            this.firstMind=firstMind;
            this.secondMind=secondMind;
        }
    }

    private static final Map<UUID,ActiveSwap> ACTIVE_BY_BODY=new HashMap<>();
    private static final Map<UUID,Set<ActiveSwap>> ACTIVE_BY_REQUESTER=new HashMap<>();

    private static void registerOriginalIfMissing(Mob body) {
        if (!OWNERS.snapshot().containsKey(body.getUUID()))
            OWNERS.registerOriginal(body.getUUID(),new MindLedger.Mind(
                body.getUUID(),MindLedger.Kind.NPC,body.getType().toString()));
    }

    /**
     * Commit a reciprocal Mob↔Mob exchange directly in the live server.
     * Requesters are session managers, not participants; one requester may
     * hold multiple independent pairs, while a physical body is exclusive.
     */
    public static synchronized String begin(UUID requester,Mob first,Mob second) {
        if(requester==null) return "A requester identity is required.";
        if(first==null||second==null||first==second)
            return "Choose two different living Mob bodies.";
        if(!first.isAlive()||!second.isAlive()||first.isRemoved()||second.isRemoved())
            return "Both Mob bodies must be alive and loaded.";
        if(first.level()!=second.level()||!(first.level() instanceof ServerLevel level))
            return "Both Mob bodies must be in the same loaded server level.";
        if(ACTIVE_BY_BODY.containsKey(first.getUUID())
                ||ACTIVE_BY_BODY.containsKey(second.getUUID()))
            return "One of those physical bodies already belongs to another mind exchange.";
        if(!supports(first,second))
            return "This pair uses active Brain AI that the current native goal rebinder cannot fully transfer.";

        Snapshot originalFirst=snapshot(first),originalSecond=snapshot(second);
        if(originalFirst.goals().isEmpty()&&originalFirst.targets().isEmpty())
            return "The first Mob has no transferable native goal controller.";
        if(originalSecond.goals().isEmpty()&&originalSecond.targets().isEmpty())
            return "The second Mob has no transferable native goal controller.";

        final MindLedger.Mind firstMind,secondMind;
        try {
            registerOriginalIfMissing(first);
            registerOriginalIfMissing(second);
            firstMind=OWNERS.controlling(first.getUUID());
            secondMind=OWNERS.controlling(second.getUUID());
        } catch(RuntimeException failure) {
            return "Could not reserve both native mind identities: "+failure.getMessage();
        }

        String issue=exchange(first,second);
        if(issue!=null) return issue;

        ActiveSwap active=new ActiveSwap(requester,level,first,second,
            originalFirst,originalSecond,firstMind,secondMind);
        ACTIVE_BY_BODY.put(first.getUUID(),active);
        ACTIVE_BY_BODY.put(second.getUUID(),active);
        ACTIVE_BY_REQUESTER.computeIfAbsent(requester,ignored->new HashSet<>()).add(active);
        return null;
    }

    public static synchronized int activeSessions(UUID requester) {
        return ACTIVE_BY_REQUESTER.getOrDefault(requester,Set.of()).size();
    }

    public static synchronized boolean bodyReserved(UUID body) {
        return ACTIVE_BY_BODY.containsKey(body);
    }

    /** End all independent Mob pairs managed by one player/account. */
    public static synchronized boolean cancelByAccount(UUID requester) {
        Set<ActiveSwap> sessions=new HashSet<>(
            ACTIVE_BY_REQUESTER.getOrDefault(requester,Set.of()));
        if(sessions.isEmpty()) return false;
        for(ActiveSwap session:sessions) end(session,true,
            "Mob minds returned to their original physical bodies.");
        return true;
    }

    private static void end(ActiveSwap session,boolean notify,String reason) {
        if(!ACTIVE_BY_BODY.containsValue(session)) return;
        RuntimeException failure=null;
        // Restore only bodies still present and alive. A dead or unloaded
        // physical body is never recreated or teleported as part of a return.
        if(session.first.isAlive()&&!session.first.isRemoved()) {
            try { apply(session.first,session.firstOriginal); }
            catch(RuntimeException problem) { failure=problem; }
        }
        if(session.second.isAlive()&&!session.second.isRemoved()) {
            try { apply(session.second,session.secondOriginal); }
            catch(RuntimeException problem) {
                if(failure==null) failure=problem; else failure.addSuppressed(problem);
            }
        }
        try {
            OWNERS.restorePair(session.first.getUUID(),session.firstMind,
                session.second.getUUID(),session.secondMind);
        } catch(RuntimeException problem) {
            if(failure==null) failure=problem; else failure.addSuppressed(problem);
        }

        ACTIVE_BY_BODY.remove(session.first.getUUID(),session);
        ACTIVE_BY_BODY.remove(session.second.getUUID(),session);
        Set<ActiveSwap> owned=ACTIVE_BY_REQUESTER.get(session.requester);
        if(owned!=null) {
            owned.remove(session);
            if(owned.isEmpty()) ACTIVE_BY_REQUESTER.remove(session.requester);
        }
        if(notify) {
            var player=session.level.getServer().getPlayerList().getPlayer(session.requester);
            if(player!=null&&!player.isRemoved()) {
                String message=failure==null?reason:
                    "Mob exchange ended, but a native controller restore reported: "
                        +failure.getMessage();
                player.sendSystemMessage(Component.literal("[Freaky Friday] "+message));
            }
        }
        if(failure!=null) {
            System.err.println("[Freaky Friday] Native mind restore failed for pair "
                +session.first.getUUID()+" / "+session.second.getUUID()+": "+failure);
        }
    }

    @SubscribeEvent
    public static synchronized void onBodyDeath(LivingDeathEvent event) {
        if(!(event.getEntity() instanceof Mob body)) return;
        ActiveSwap session=ACTIVE_BY_BODY.get(body.getUUID());
        if(session!=null) end(session,true,
            "One Mob body died; the surviving body was returned to its original AI.");
    }

    @SubscribeEvent
    public static synchronized void maintain(ServerTickEvent.Post event) {
        for(ActiveSwap session:List.copyOf(new HashSet<>(ACTIVE_BY_BODY.values()))) {
            if(session.first.isRemoved()||session.second.isRemoved()
                    ||!session.first.isAlive()||!session.second.isAlive()
                    ||session.first.level()!=session.level||session.second.level()!=session.level) {
                end(session,true,
                    "A Mob body died or unloaded; remaining native AI was restored.");
            }
        }
    }

    @SubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        clearOnServerStop();
    }

    public static synchronized void clearOnServerStop() {
        for(ActiveSwap session:List.copyOf(new HashSet<>(ACTIVE_BY_BODY.values())))
            end(session,false,"Server stopped; original AI controllers restored.");
        for(DetachedController controller:List.copyOf(DETACHED_BY_PROXY.values()))
            controller.close();
        DETACHED_BY_PROXY.clear();
        ACTIVE_BY_BODY.clear();
        ACTIVE_BY_REQUESTER.clear();
    }
}
