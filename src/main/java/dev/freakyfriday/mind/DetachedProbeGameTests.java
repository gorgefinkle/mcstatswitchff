package dev.freakyfriday.mind;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import dev.freakyfriday.FreakyFriday;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.ai.goal.Goal;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Real NeoForge GameTest: generic detached host factory lifecycle for several
 * actual vanilla class families. No donor AI, Brain, or Goal is ever ticked.
 */
public final class DetachedProbeGameTests {
    private DetachedProbeGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, "freakyfriday");
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        DETACHED_CROSS_SPECIES = FUNCTIONS.register(
            "detached_cross_species", () -> DetachedProbeGameTests::testCrossSpecies);

    // Test cases may name specific vanilla species; the actual runtime engine
    // must never dispatch on species-pair rules.
    private record Pair(String label, EntityType<? extends Mob> donorType,
                        EntityType<? extends Mob> recipientType) {}
    private static final List<Pair> CASES = List.of(
        new Pair("zombie_to_cow", EntityTypes.ZOMBIE, EntityTypes.COW),
        new Pair("cow_to_zombie", EntityTypes.COW, EntityTypes.ZOMBIE),
        new Pair("villager_to_cow", EntityTypes.VILLAGER, EntityTypes.COW),
        new Pair("creeper_to_cow", EntityTypes.CREEPER, EntityTypes.COW),
        new Pair("cow_to_creeper", EntityTypes.COW, EntityTypes.CREEPER),
        new Pair("zombie_to_villager", EntityTypes.ZOMBIE, EntityTypes.VILLAGER)
    );

    /**
     * Intentionally isolated from the ordinary six-pair matrix. The dragon
     * has multipart anatomy and boss-specific initialization, so failures
     * here must be attributed to this pairing, not to ordinary Mob hosts.
     * This is a construction/position diagnostic, NOT a controller swap.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        DETACHED_DRAGON_CHICKEN = FUNCTIONS.register(
            "detached_dragon_chicken", () -> DetachedProbeGameTests::testDragonChicken);

    private static void testDragonChicken(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Pair[] pairs = {
            new Pair("ender_dragon_to_chicken", EntityTypes.ENDER_DRAGON, EntityTypes.CHICKEN),
            new Pair("chicken_to_ender_dragon", EntityTypes.CHICKEN, EntityTypes.ENDER_DRAGON)
        };
        for (int index = 0; index < pairs.length; index++) {
            Pair pair = pairs[index];
            String label = pair.label();
            // Run in disposable, isolated headless GameTest world. Never
            // execute the detached host's native AI.
            Mob donor = pair.donorType().create(level, EntitySpawnReason.COMMAND);
            Mob recipient = pair.recipientType().create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(donor != null && recipient != null,
                label + ": factory did not produce two Mob instances");
            BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1 + index * 3));
            donor.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
            recipient.setPos(pos.getX() + 2.5, pos.getY(), pos.getZ() + 0.5);
            helper.assertTrue(level.addFreshEntity(donor) && level.addFreshEntity(recipient),
                label + ": original donor/recipient couldn't register");
            ProbeBodySnapshot donorBefore = ProbeBodySnapshot.take(donor);
            ProbeBodySnapshot bodyBefore = ProbeBodySnapshot.take(recipient);

            DetachedMindHostProbe.Creation created =
                DetachedMindHostProbe.construct(donor, recipient,
                    "real-neoforge-gametest-" + label);
            helper.assertTrue(created.host() != null,
                label + ": host creation rejected: " + created.problem());
            Mob host = created.host();
            helper.assertTrue(host != donor && host != recipient,
                label + ": host aliases an existing mob");
            helper.assertTrue(host.getType() == donor.getType()
                    && host.getClass() == donor.getClass(),
                label + ": detached host class/type don't match donor");
            helper.assertTrue(level.getEntity(host.getUUID()) == null,
                label + ": host world-registered before mirroring");
            helper.assertTrue(donorBefore.unchanged(donor) && bodyBefore.unchanged(recipient),
                label + ": native constructor mutated an original body");

            host.setPos(recipient.getX(), recipient.getY(), recipient.getZ());
            helper.assertTrue(host.position().distanceToSqr(recipient.position()) < 0.000001,
                label + ": host positioning mismatch");
            helper.assertTrue(level.getEntity(host.getUUID()) == null,
                label + ": host world-registered after setPos");
            helper.assertTrue(donorBefore.unchanged(donor) && bodyBefore.unchanged(recipient),
                label + ": host setPos mutated original bodies");
            System.out.println("FF-GAMETEST pair " + label + " PASS"
                + " donorHealth=" + donor.getHealth()
                + " recipientHealth=" + recipient.getHealth()
                + " hostType=" + host.getType()
                + " recipientType=" + recipient.getType()
                + " hostClass=" + host.getClass().getName()
                + " hostRegistered=false hostAiTicked=false actualMindSwapped=false");
            donor.discard();
            recipient.discard();
        }
        System.out.println("FF-GAMETEST detached_dragon_chicken PASS");
        helper.succeed();
    }

    /**
     * Exercises the actual Brigadier command tree on a real NeoForge server
     * with a synthetic server-side player. No manual book, spawn or clicks.
     * The test cases use real EntityType factories but never world-register
     * those temporary diagnostic entities or tick donor AI.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        ONE_COMMAND_DIAGNOSTICS = FUNCTIONS.register(
            "one_command_diagnostics", () -> DetachedProbeGameTests::testCommands);

    @SuppressWarnings("removal")
    private static void testCommands(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer mock = helper.makeMockServerPlayerInLevel();
        var source = level.getServer().createCommandSourceStack().withEntity(mock);
        var dispatcher = level.getServer().getCommands().getDispatcher();
        try {
            int forward = dispatcher.execute(
                "freakyfriday test pair minecraft:ender_dragon minecraft:chicken", source);
            helper.assertTrue(forward == 1,
                "Command dispatcher failed Ender Dragon -> Chicken one-command test");

            int reverse = dispatcher.execute(
                "freakyfriday test pair chicken ender_dragon", source);
            helper.assertTrue(reverse == 1,
                "Command dispatcher failed Chicken -> Ender Dragon shortcut IDs");

            int invalid = dispatcher.execute(
                "freakyfriday test pair no_such_mob chicken", source);
            helper.assertTrue(invalid == 0,
                "Bad entity IDs must fail closed, not silently test a fallback mob");

            int suite = dispatcher.execute("freakyfriday test suite", source);
            helper.assertTrue(suite == 1,
                "Full automatic eight-pair suite was not successful");

            int cancel = dispatcher.execute("freakyfriday cancel", source);
            helper.assertTrue(cancel == 1,
                "Existing cancel command regressed");

            System.out.println("FF-COMMANDTEST pair ender_dragon chicken PASS");
            System.out.println("FF-COMMANDTEST pair chicken ender_dragon PASS");
            System.out.println("FF-COMMANDTEST invalid_type PASS");
            System.out.println("FF-COMMANDTEST suite PASS");
            System.out.println("FF-COMMANDTEST cancel PASS");
            System.out.println("FF-COMMANDTEST all PASS");
            helper.succeed();
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new AssertionError("Real Brigadier command dispatch failed", error);
        } finally {
            dev.freakyfriday.mind.MindSwapSessions.clearOnLogout(mock.getUUID());
        }
    }

    /**
     * REAL server simulation: a Cow body is moved with Minecraft's native
     * Entity.move collision resolver into an actual stone barrier, while an
     * unregistered Chicken-class host tracks it for forty physical ticks.
     * Sentinel blocks and original entity identities must survive unchanged.
     * Controlled movement != swapped native AI.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        PHYSICAL_WORLD_MIRROR = FUNCTIONS.register(
            "physical_world_mirror", () -> DetachedProbeGameTests::testPhysicalWorldMirror);

    private static void testPhysicalWorldMirror(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        BlockPos p = helper.absolutePos(new BlockPos(1, 1, 1));
        BlockPos barrier = p.east(4);
        BlockPos sentinel = p.south(5);
        var stone = Blocks.STONE.defaultBlockState();
        var gold = Blocks.GOLD_BLOCK.defaultBlockState();
        helper.assertTrue(level.setBlockAndUpdate(barrier, stone),
            "Failed to place a genuine Minecraft collision block");
        level.setBlockAndUpdate(barrier.above(), stone);
        helper.assertTrue(level.setBlockAndUpdate(sentinel, gold),
            "Failed to place the world integrity sentinel");

        Mob donor = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
        Mob body = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(donor != null && body != null,
            "Real world motion requires actual Chicken and Cow entities");
        // Keep the donor INSIDE the GameTest arena. Positioning it at
        // x=-0.5 outside the structure killed the real Chicken after ~30 ticks.
        donor.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 3.5);
        body.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        donor.setNoAi(true);
        body.setNoAi(true);
        donor.setNoGravity(true);
        body.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(donor) && level.addFreshEntity(body),
            "Failed to register real actors for movement regression");
        final var initialId = body.getUUID();
        final var donorId = donor.getUUID();
        final float bodyHealth = body.getHealth();
        final float donorHealth = donor.getHealth();
        final double startX = body.getX();

        String started = DetachedHostMirrorProbe.start(player, donor, body);
        helper.assertTrue(started.contains("started"),
            "Real detached-host mirror did not start: " + started);
        DetachedHostMirrorProbe.Progress initial = DetachedHostMirrorProbe.progress(initialId);
        helper.assertTrue(initial != null,
            "Started mirror had no observable active host");
        final var hostId = initial.hostId();
        helper.assertTrue(level.getEntity(hostId) == null,
            "Detached host appeared in the world at initialization");

        for (int i = 0; i < 5; i++) {
            helper.runAfterDelay(5 + 5 * i, () -> {
                // Unlike setPos(), move() uses actual collision resolution.
                body.move(MoverType.SELF, new Vec3(0.95, 0, 0));
                helper.assertTrue(level.getEntity(hostId) == null,
                    "Host registered while the physical Cow body was moving");
            });
        }

        helper.runAfterDelay(33, () -> {
            DetachedHostMirrorProbe.Progress progress =
                DetachedHostMirrorProbe.progress(initialId);
            helper.assertTrue(progress != null && progress.ticks() >= 20,
                "Mirror ended prematurely during actual body movement");
            helper.assertTrue(progress.mirrors() >= 2,
                "Host did not follow the physically moving recipient");
            helper.assertTrue(progress.alignmentFailures() == 0
                    && progress.registrationViolations() == 0,
                "Live mirror reported misalignment or world registration");
            helper.assertTrue(progress.hostPosition().distanceToSqr(
                    progress.bodyPosition()) < 0.000001,
                "Detached host did not match the moving physical body's position");
            helper.assertTrue(body.getX() > startX + 1.0,
                "Native movement did not displace the physical Cow body");
            helper.assertTrue(body.getX() < barrier.getX() - 0.15,
                "Physical body crossed a stone collision wall");
            helper.assertTrue(level.getBlockState(barrier).equals(stone)
                    && level.getBlockState(sentinel).equals(gold),
                "A real world block changed during detached-host mirroring");
            System.out.println("FF-WORLDTEST moving_body_collision PASS"
                + " progressTicks=" + progress.ticks()
                + " hostMirrors=" + progress.mirrors()
                + " blockCollision=true realMobTicks=true hostAiTicked=false");
        });

        helper.runAfterDelay(75, () -> {
            try {
                helper.assertTrue(!DetachedHostMirrorProbe.hasActiveProbe(initialId),
                    "40 real physical ticks did not retire detached host");
                helper.assertTrue(level.getEntity(hostId) == null,
                    "Detached host appeared in level after 40-tick completion");
                helper.assertTrue(level.getEntity(initialId) == body
                        && level.getEntity(donorId) == donor,
                    "An original physical mob was replaced or lost");
                helper.assertTrue(body.isAlive() && donor.isAlive()
                        && Float.compare(body.getHealth(), bodyHealth) == 0
                        && Float.compare(donor.getHealth(), donorHealth) == 0,
                    "Real server test changed original mobs' life/health state");
                helper.assertTrue(level.getBlockState(barrier).equals(stone)
                        && level.getBlockState(barrier.above()).equals(stone)
                        && level.getBlockState(sentinel).equals(gold),
                    "World integrity sentinel or collision barrier changed");
                System.out.println("FF-WORLDTEST 40tick_cleanup PASS");
                System.out.println("FF-WORLDTEST original_entities_and_blocks PASS");
                System.out.println("FF-WORLDTEST all PASS");
                helper.succeed();
            } finally {
                donor.discard();
                body.discard();
                MindSwapSessions.clearOnLogout(player.getUUID());
            }
        });
    }

    /**
     * Read-only inspection of REAL Goal/Brain classes on the real server.
     * Factory constructors may still have side effects; this is only run on
     * an ephemeral CI world. No native AI is executed by this test.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        NATIVE_AI_INVENTORY = FUNCTIONS.register(
            "native_ai_inventory", () -> DetachedProbeGameTests::testNativeAiInventory);

    private static void testNativeAiInventory(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var types = List.of(
            EntityTypes.CHICKEN, EntityTypes.COW,
            EntityTypes.ZOMBIE, EntityTypes.VILLAGER, EntityTypes.ENDER_DRAGON);
        int scanned = 0;
        int candidateCount = 0;
        for (var type : types) {
            Mob mob = type.create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(mob != null, "Native AI inventory factory returned null");
            NativeAiSurfaceAudit.Report report = NativeAiSurfaceAudit.inspect(mob);
            helper.assertTrue(report.entityType().equals(mob.getType().toString()),
                "Native AI report assigned the wrong Mob identity");
            helper.assertTrue(level.getEntity(mob.getUUID()) == null,
                "Read-only inspection unexpectedly world-registered an entity");
            helper.assertTrue(!report.goals().isEmpty()
                    || !report.targeting().isEmpty()
                    || !report.nativeOverrides().isEmpty(),
                "Native AI surface inventory was empty for " + type);
            scanned++;
            candidateCount += report.effectCandidates().size();
            System.out.println("FF-NATIVE-PROBE inventory"
                + " type=" + mob.getType() + " class=" + mob.getClass().getName()
                + " goals=" + report.goals().size()
                + " targetGoals=" + report.targeting().size()
                + " overrides=" + report.nativeOverrides().size()
                + " effectCandidates=" + report.effectCandidates().size()
                + " brainActive=" + report.brainActive()
                + " nativeAiTicked=false isolationCertified=false");
        }
        helper.assertTrue(scanned == 5 && candidateCount > 0,
            "Real classfile/Goal inventory did not report expected candidates");
        System.out.println("FF-NATIVE-PROBE inventory PASS"
            + " types=5 candidates=" + candidateCount
            + " runtimeWorldEffectIsolation=UNVERIFIED");
        helper.succeed();
    }

    /**
     * Real Minecraft AI scheduling control: the same harmless Goal class is
     * attached to a registered Chicken and an unregistered Chicken-class host.
     * The registered Chicken gets ticked by the real server and runs its Goal,
     * while the unregistered host's Goal stays dormant. This observes genuine
     * scheduler execution; it does NOT transfer a donor's actual Brain/AI.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        NATIVE_GOAL_SCHEDULER = FUNCTIONS.register(
            "native_goal_scheduler", () -> DetachedProbeGameTests::testNativeGoalScheduler);

    private static void testNativeGoalScheduler(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob live = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
        Mob recipient = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(live != null && recipient != null,
            "Scheduler probe requires real Chicken and Cow factories");

        BlockPos p = helper.absolutePos(new BlockPos(1, 1, 1));
        live.setPos(p.getX()+0.5, p.getY(), p.getZ()+0.5);
        live.setNoGravity(true);
        recipient.setPos(p.getX()+2.5, p.getY(), p.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(live),
            "Chicken must world-register to tick its native GoalSelector");
        DetachedMindHostProbe.Creation result =
            DetachedMindHostProbe.construct(live, recipient, "native-goal-scheduler-test");
        helper.assertTrue(result.host() != null,
            "Detached Chicken host creation failed: " + result.problem());
        Mob detached = result.host();
        helper.assertTrue(level.getEntity(detached.getUUID()) == null,
            "Detached Chicken must be absent from the world entity map");

        AtomicInteger liveGoalTicks = new AtomicInteger();
        AtomicInteger detachedGoalTicks = new AtomicInteger();
        Goal liveGoal = new Goal() {
            @Override public boolean canUse() { return true; }
            @Override public boolean canContinueToUse() { return true; }
            @Override public void tick() { liveGoalTicks.incrementAndGet(); }
        };
        Goal detachedGoal = new Goal() {
            @Override public boolean canUse() { return true; }
            @Override public boolean canContinueToUse() { return true; }
            @Override public void tick() { detachedGoalTicks.incrementAndGet(); }
        };
        live.goalSelector.addGoal(0, liveGoal);
        detached.goalSelector.addGoal(0, detachedGoal);

        helper.runAfterDelay(65, () -> {
            try {
                int registeredTicks = liveGoalTicks.get();
                int unregisteredTicks = detachedGoalTicks.get();
                helper.assertTrue(registeredTicks >= 5,
                    "Real native GoalSelector did not run registered Chicken's test Goal"
                        + " across 65 server ticks (observed=" + registeredTicks + ")");
                helper.assertTrue(unregisteredTicks == 0,
                    "Unregistered detached-host Goal was unexpectedly ticked by the server");
                helper.assertTrue(level.getEntity(detached.getUUID()) == null,
                    "Detached Chicken became world registered during scheduler observation");
                helper.assertTrue(level.getEntity(live.getUUID()) == live,
                    "Original registered Chicken was replaced or unloaded");
                System.out.println("FF-NATIVE-PROBE goal_scheduler PASS"
                    + " registeredGoalTicks=" + registeredTicks
                    + " detachedGoalTicks=" + unregisteredTicks
                    + " registeredNativeSchedulerTicked=true"
                    + " detachedNativeDonorAiTicked=false");
                helper.succeed();
            } finally {
                live.discard();
            }
        });
    }

    /**
     * An adversarial third-party Goal is invoked on a REAL unregistered host.
     * It deliberately writes a real level block. We EXPECT to detect the
     * escape and restore the block, proving that 'not registered' is not a
     * world effect sandbox. This does not execute a vanilla donor's native AI.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        HOST_WORLD_EFFECT_ESCAPE = FUNCTIONS.register(
            "host_world_effect_escape", () -> DetachedProbeGameTests::testHostWorldEscape);

    private static void testHostWorldEscape(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sentinel = helper.absolutePos(new BlockPos(1, 1, 1));
        var originalState = level.getBlockState(sentinel);
        Mob donor = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
        Mob body = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(donor != null && body != null,
            "Native isolation negative test needs actual vanilla actors");

        // If any assertion fails after the mutation, finally still restores
        // the world. This intentionally dangerous Goal is never callable by
        // a player/command, only the disposable GameTest server.
        try {
            helper.assertTrue(level.setBlockAndUpdate(sentinel,
                    Blocks.GOLD_BLOCK.defaultBlockState()),
                "Cannot initialize real-world sentinel block");
            ProbeBodySnapshot donorBefore = ProbeBodySnapshot.take(donor);
            ProbeBodySnapshot bodyBefore = ProbeBodySnapshot.take(body);
            DetachedMindHostProbe.Creation result =
                DetachedMindHostProbe.construct(donor, body,
                    "real-gametest-adversarial-goal");
            helper.assertTrue(result.host() != null,
                "Could not create actual detached host for isolation test: "
                    + result.problem());
            Mob host = result.host();
            helper.assertTrue(level.getEntity(host.getUUID()) == null,
                "Test host unexpectedly world-registered before adversarial goal");
            helper.assertTrue(host.level() == level,
                "Detached host has no real world pointer; test premise invalid");

            Goal adversarial = new Goal() {
                @Override public boolean canUse() { return true; }
                @Override public void tick() {
                    // This is a real Goal#tick, but a *test-only injected*
                    // adversarial implementation, not original donor behavior.
                    host.level().setBlockAndUpdate(
                        sentinel, Blocks.REDSTONE_BLOCK.defaultBlockState());
                }
            };
            host.goalSelector.addGoal(0, adversarial);
            helper.assertTrue(host.goalSelector.getAvailableGoals().stream()
                    .anyMatch(wrapper -> wrapper.getGoal() == adversarial),
                "Adversarial native Goal was not installed on detached host");
            adversarial.tick();

            boolean escaped = level.getBlockState(sentinel)
                .equals(Blocks.REDSTONE_BLOCK.defaultBlockState());
            boolean detached = level.getEntity(host.getUUID()) == null;
            boolean originalBodiesSafe =
                donorBefore.unchanged(donor) && bodyBefore.unchanged(body);
            helper.assertTrue(escaped,
                "Adversarial Goal did not reproduce unrestricted world writes");
            helper.assertTrue(detached,
                "Adversarial unregistered host unexpectedly entered real entity map");
            helper.assertTrue(originalBodiesSafe,
                "Adversarial effect also modified original Mob snapshots");
            System.out.println("FF-NATIVE-PROBE world_effect_escape DETECTED"
                + " hostRegistered=false testOnlyGoalTicked=true"
                + " nativeDonorAiTicked=false blockMutated=true"
                + " originalBodiesUnchanged=true");
        } finally {
            level.setBlockAndUpdate(sentinel, originalState);
        }
        helper.assertTrue(level.getBlockState(sentinel).equals(originalState),
            "Isolation test failed to restore its original world sentinel");
        System.out.println("FF-NATIVE-PROBE world_effect_escape PASS"
            + " isolationCertified=false unsafeToTickDetachedDonorAI=true");
        helper.succeed();
    }

    /**
     * REAL NeoForge host/recipient intent-boundary proof. Two registered
     * vanilla NPC bodies exchange only logical mind ownership. The controlling
     * mind provides immutable DATA-ONLY intent frames, never Goal/Brain Java
     * callbacks. Every accepted action goes through the recipient's existing
     * native BodyPort; unsupported physical abilities stay unavailable.
     *
     * This proves the receiving boundary and species-independent body action
     * dispatch, NOT isolation/execution of arbitrary native donor AI.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        DATA_ONLY_INTENT_BOUNDARY = FUNCTIONS.register(
            "data_only_intent_boundary", () -> DetachedProbeGameTests::testDataOnlyBoundary);

    private static void testDataOnlyBoundary(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob cow = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(cow != null && zombie != null,
            "Data-only intent boundary test requires real Cow and Zombie");
        BlockPos p = helper.absolutePos(new BlockPos(1, 1, 1));
        cow.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        zombie.setPos(p.getX() + 3.5, p.getY(), p.getZ() + 0.5);
        cow.setNoAi(true);
        zombie.setNoAi(true);
        cow.setNoGravity(true);
        zombie.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(cow) && level.addFreshEntity(zombie),
            "Actual Cow/Zombie physical bodies must be registered");

        try {
            var cowId = cow.getUUID();
            var zombieId = zombie.getUUID();
            float cowHealth = cow.getHealth(), zombieHealth = zombie.getHealth();
            var cowMind = new MindLedger.Mind(java.util.UUID.randomUUID(),
                MindLedger.Kind.NPC, "minecraft:cow_native_profile_not_executed");
            var zombieMind = new MindLedger.Mind(java.util.UUID.randomUUID(),
                MindLedger.Kind.NPC, "minecraft:zombie_native_profile_not_executed");
            var receiver = new CapturedIntentBridge();
            receiver.register(new MinecraftBodyPort(cow), cowMind);
            receiver.register(new MinecraftBodyPort(zombie), zombieMind);
            receiver.exchange(cowId, zombieId);

            long now = level.getGameTime();
            var oldOwnerFrame = new CapturedIntentBridge.Frame(cowId,
                cowMind.identity(), now,
                List.of(new BodyActionGate.Intent(MinecraftBodyPort.LOOK_AT,
                    java.util.Map.of("x", Double.toString(zombie.getX()),
                        "y", Double.toString(zombie.getY()),
                        "z", Double.toString(zombie.getZ())))));
            var denied = receiver.deliver(oldOwnerFrame);
            helper.assertTrue(denied.status() ==
                    CapturedIntentBridge.Status.WRONG_CONTROLLER
                    && denied.outcomes().isEmpty(),
                "Original mind must not control Cow after logical ownership swap");

            var frame = new CapturedIntentBridge.Frame(cowId,
                zombieMind.identity(), now,
                List.of(
                    new BodyActionGate.Intent(MinecraftBodyPort.LOOK_AT,
                        java.util.Map.of("x", Double.toString(zombie.getX()),
                            "y", Double.toString(zombie.getY()),
                            "z", Double.toString(zombie.getZ()))),
                    new BodyActionGate.Intent(MinecraftBodyPort.DETONATE)
                ));
            // Reproduce the external-worker DATA BOUNDARY using the actual
            // signed binary codec. No donor Goal/Brain enters this JVM.
            byte[] secret = CapturedIntentWireCodec.generateKey();
            java.util.UUID sessionId = java.util.UUID.randomUUID();
            byte[] signed = CapturedIntentWireCodec.encode(
                new CapturedIntentWireCodec.Envelope(sessionId, frame), secret);
            byte[] forged = signed.clone();
            forged[20] ^= 1;
            boolean tamperingRejected = false;
            try {
                receiver.deliverAuthenticated(forged, secret, sessionId, now);
            } catch (IllegalArgumentException expected) {
                tamperingRejected = true;
            }
            helper.assertTrue(tamperingRejected,
                "Real-server receiver accepted an unauthorized wire mutation");
            System.out.println("FF-ISOLATION authenticated_wire_tamper_rejected PASS");
            var result = receiver.deliverAuthenticated(
                signed, secret, sessionId, now);
            helper.assertTrue(result.status() == CapturedIntentBridge.Status.DISPATCHED
                    && result.outcomes().size() == 2,
                "Real physical Cow rejected a valid swapped, data-only action frame");
            helper.assertTrue(result.outcomes().get(0).status()
                    == BodyActionGate.Status.EXECUTED,
                "Recipient Cow did not execute its own native look control");
            helper.assertTrue(result.outcomes().get(1).status()
                    == BodyActionGate.Status.UNAVAILABLE,
                "Cow body inherited unavailable explosive anatomy");
            helper.assertTrue(receiver.deliverAuthenticated(
                signed, secret, sessionId, now).status()
                    == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
                "Replay frame was not blocked after a successful physical dispatch");

            var reverse = new CapturedIntentBridge.Frame(zombieId,
                cowMind.identity(), now,
                List.of(new BodyActionGate.Intent(MinecraftBodyPort.LOOK_AT,
                    java.util.Map.of("x", Double.toString(cow.getX()),
                        "y", Double.toString(cow.getY()),
                        "z", Double.toString(cow.getZ())))));
            var reverseResult = receiver.deliver(reverse);
            helper.assertTrue(reverseResult.status() == CapturedIntentBridge.Status.DISPATCHED
                    && reverseResult.outcomes().get(0).status()
                        == BodyActionGate.Status.EXECUTED,
                "Cow mind data frame did not route to Zombie's native body");
            helper.assertTrue(cow.getUUID().equals(cowId)
                    && zombie.getUUID().equals(zombieId)
                    && level.getEntity(cowId) == cow && level.getEntity(zombieId) == zombie
                    && Float.compare(cow.getHealth(),cowHealth) == 0
                    && Float.compare(zombie.getHealth(),zombieHealth) == 0,
                "Data-only frame exchange modified real entity identity or health");

            receiver.exchange(cowId, zombieId);
            helper.assertTrue(receiver.controlling(cowId).equals(cowMind)
                    && receiver.controlling(zombieId).equals(zombieMind),
                "Reverse exchange did not restore exact complete mind identities");
            helper.assertTrue(receiver.deliver(
                new CapturedIntentBridge.Frame(cowId, cowMind.identity(), now,
                    List.of())).status() == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
                "Reversing controllers allowed same-tick action replay");

            System.out.println("FF-ISOLATION data_only_exchange PASS"
                + " realWorld=true pairedBodies=cow,zombie"
                + " recipientNativeLook=true unavailableAnatomyBlocked=true"
                + " wrongMindRejected=true replayRejected=true"
                + " bothBodyIdentitiesPreserved=true"
                + " nativeDonorAiTicked=false noArbitraryDecisionCallback=true"
                + " authenticatedWire=true tamperedFrameRejected=true");
            helper.succeed();
        } catch (Exception failure) {
            throw new AssertionError("Data-only real-server mind assignment rejected",failure);
        } finally {
            cow.discard();
            zombie.discard();
        }
    }

    /**
     * TWO REAL JVMs: this real GameTest receiver reads a signed intent frame
     * created by a separate native NeoForge worker server. The CI runner
     * copies only the request descriptor + HMAC frame into this independent
     * receiver world's directory. No worker Java object or world is shared.
     *
     * When the fixture is absent (ordinary baseline GameTests), succeed as
     * explicitly SKIPPED; the separate CI worker job requires the PASS marker.
     */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        EXTERNAL_NATIVE_WORKER = FUNCTIONS.register(
            "external_native_worker", () -> DetachedProbeGameTests::testExternalNativeWorker);


    private static int[] arenaChunkBounds(GameTestHelper helper,
            int minX,int maxX,int minZ,int maxZ) {
        BlockPos[] corners={
            helper.absolutePos(new BlockPos(minX,1,minZ)),
            helper.absolutePos(new BlockPos(minX,1,maxZ)),
            helper.absolutePos(new BlockPos(maxX,1,minZ)),
            helper.absolutePos(new BlockPos(maxX,6,maxZ))
        };
        int minCx=Integer.MAX_VALUE,maxCx=Integer.MIN_VALUE;
        int minCz=Integer.MAX_VALUE,maxCz=Integer.MIN_VALUE;
        for(BlockPos p:corners) {
            int cx=p.getX()>>4, cz=p.getZ()>>4;
            minCx=Math.min(minCx,cx); maxCx=Math.max(maxCx,cx);
            minCz=Math.min(minCz,cz); maxCz=Math.max(maxCz,cz);
        }
        return new int[]{minCx,maxCx,minCz,maxCz};
    }

    private static void setArenaChunks(ServerLevel level,UUID owner,
            int[] bounds,boolean forced) {
        if(bounds==null)return;
        for(int cx=bounds[0];cx<=bounds[1];cx++)
            for(int cz=bounds[2];cz<=bounds[3];cz++)
                NativeAiWorkerServer.setEntityTickingChunk(level,owner,cx,cz,forced);
    }

    private static void testExternalNativeWorker(GameTestHelper helper) {
        java.nio.file.Path requestPath=java.nio.file.Path.of("ff-worker-request.properties");
        java.nio.file.Path packetPath=java.nio.file.Path.of("ff-worker-frame.bin");
        if (!java.nio.file.Files.exists(requestPath)
                && !java.nio.file.Files.exists(packetPath)) {
            System.out.println("FF-NATIVE-WORKER E2E_SKIPPED_NO_EXTERNAL_FIXTURE");
            helper.succeed();
            return;
        }
        helper.assertTrue(java.nio.file.Files.isRegularFile(requestPath)
                && java.nio.file.Files.isRegularFile(packetPath),
            "A partially present worker fixture must never be accepted");
        ServerLevel level=helper.getLevel();
        Mob receiverBody=null, originalDonorBody=null, targetBody=null;
        UUID ticketOwner=UUID.randomUUID();
        int[] ticketBounds=arenaChunkBounds(helper,1,12,1,12);
        setArenaChunks(level,ticketOwner,ticketBounds,true);
        // The GameTest structure may straddle a chunk edge. Release this
        // isolated fixture's tickets even if a later assertion fails.
        helper.runAfterDelay(60,()->setArenaChunks(level,ticketOwner,ticketBounds,false));
        try {
            java.util.Properties request=new java.util.Properties();
            try(var in=java.nio.file.Files.newInputStream(requestPath)){request.load(in);}
            String donorType=request.getProperty("donor");
            String recipientType=request.getProperty("recipient");
            String stimulusType=request.getProperty("stimulus");
            boolean requireAttack=Boolean.parseBoolean(
                request.getProperty("requireAttack","false"));
            helper.assertTrue(donorType!=null && recipientType!=null && stimulusType!=null,
                "A native worker must identify donor, recipient and mapped target types");
            java.util.UUID session=java.util.UUID.fromString(request.getProperty("session"));
            java.util.UUID recipientId=java.util.UUID.fromString(request.getProperty("recipientBody"));
            java.util.UUID donorMind=java.util.UUID.fromString(request.getProperty("sourceMind"));
            java.util.UUID stimulusId=java.util.UUID.fromString(request.getProperty("stimulusId"));
            long tick=Long.parseLong(request.getProperty("recipientTick"));
            byte[] secret=java.util.HexFormat.of().parseHex(System.getenv("FF_WORKER_KEY_HEX"));
            helper.assertTrue(secret.length==CapturedIntentWireCodec.KEY_BYTES,
                "Missing real-worker HMAC verification key");

            // The receiver is driven by registration IDs, not a whitelist of
            // permitted donor/recipient species, Java classes or goal types.
            net.minecraft.resources.Identifier rId=
                net.minecraft.resources.Identifier.tryParse(recipientType);
            net.minecraft.resources.Identifier dId=
                net.minecraft.resources.Identifier.tryParse(donorType);
            net.minecraft.resources.Identifier tId=
                net.minecraft.resources.Identifier.tryParse(stimulusType);
            helper.assertTrue(rId!=null && dId!=null && tId!=null
                    && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(rId)
                    && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(dId)
                    && net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.containsKey(tId),
                "Native donor, recipient or mapped target entity type not registered");
            var factoryR=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(rId);
            var factoryD=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(dId);
            var factoryT=net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getValue(tId);
            var entityR=factoryR.create(level,EntitySpawnReason.COMMAND);
            var entityD=factoryD.create(level,EntitySpawnReason.COMMAND);
            var entityT=factoryT.create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(entityR instanceof Mob && entityD instanceof Mob
                    && entityT instanceof Mob,
                "Native donor, recipient and target must be genuine Mob entities");
            receiverBody=(Mob)entityR;
            originalDonorBody=(Mob)entityD;
            targetBody=(Mob)entityT;
            var start=helper.absolutePos(new BlockPos(6,1,6));
            for(int x=1;x<=12;x++)for(int z=1;z<=12;z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,6,z)),
                    net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            }
            receiverBody.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
            originalDonorBody.setPos(start.getX()+4.5,start.getY(),start.getZ()+4.5);
            targetBody.setPos(start.getX()+2.5,start.getY(),start.getZ()+0.5);
            receiverBody.setUUID(recipientId);
            targetBody.setUUID(stimulusId);
            targetBody.setNoAi(true);
            targetBody.setNoGravity(true);
            targetBody.setPersistenceRequired();
            // This is an isolated GameTest, not a normal mob spawn. Keep
            // the actual Mob bodies alive while the server registers their
            // chunks; the native AI action must then move THAT same entity.
            receiverBody.setPersistenceRequired();
            originalDonorBody.setPersistenceRequired();
            ServerPlayer spectatorAnchor=helper.makeMockServerPlayerInLevel();
            var spectatorPos=helper.absolutePos(new BlockPos(2,1,2));
            spectatorAnchor.setPos(spectatorPos.getX()+0.5,
                spectatorPos.getY(),spectatorPos.getZ()+0.5);
            // Disable only the recipient's own goals in this disposable test
            // fixture, while keeping its ACTUAL native navigation/controller
            // running to obey the donor's observed relative movement.
            receiverBody.goalSelector.removeAllGoals(goal->true);
            receiverBody.targetSelector.removeAllGoals(goal->true);
            receiverBody.setNoAi(false);
            originalDonorBody.setNoAi(true);
            // Recipient must use its real gravity/ground contact and native
            // navigation. The original-donor fixture is the only body held.
            receiverBody.setNoGravity(false);
            originalDonorBody.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(receiverBody)
                    && level.addFreshEntity(originalDonorBody)
                    && level.addFreshEntity(targetBody),
                "Genuine receiver/target Mob entities not registered");
            helper.assertTrue(level.getEntity(stimulusId)==targetBody,
                "Mapped native attack target did not register in receiver world");
            helper.assertTrue(level.getEntity(recipientId)==receiverBody,
                "Signed frame must refer to the actual physical recipient");
            float healthBefore=receiverBody.getHealth();
            float donorHealthBefore=originalDonorBody.getHealth();
            java.util.UUID originalMind=java.util.UUID.randomUUID();
            var bridge=new CapturedIntentBridge();
            bridge.register(new MinecraftBodyPort(receiverBody),
                new MindLedger.Mind(originalMind,MindLedger.Kind.NPC,recipientType));
            bridge.register(new MinecraftBodyPort(originalDonorBody),
                new MindLedger.Mind(donorMind,MindLedger.Kind.NPC,donorType));
            bridge.exchange(recipientId,originalDonorBody.getUUID());

            byte[] packet=java.nio.file.Files.readAllBytes(packetPath);
            helper.assertTrue(packet.length>0
                    && packet.length<=CapturedIntentWireCodec.MAX_WIRE_BYTES,
                "Worker packet invalid");
            var decoded=CapturedIntentWireCodec.decode(packet,secret,session,tick);
            int expectedActions=requireAttack?3:2;
            helper.assertTrue(decoded.frame().bodyId().equals(recipientId)
                    && decoded.frame().mindId().equals(donorMind)
                    && decoded.frame().actions().size()==expectedActions
                    && decoded.frame().actions().get(0).actionId()
                        .equals(MinecraftBodyPort.LOOK_OFFSET)
                    && decoded.frame().actions().get(1).actionId()
                        .equals(MinecraftBodyPort.MOVE_OFFSET),
                "Native worker did not emit portable recipient-relative actions");
            if(requireAttack) {
                var attack=decoded.frame().actions().get(2);
                helper.assertTrue(attack.actionId().equals(MinecraftBodyPort.ATTACK_ENTITY)
                        && stimulusId.toString().equals(attack.arguments().get("targetId")),
                    "Native worker attack did not retain mapped target identity");
            }
            var pos=decoded.frame().actions().get(1).arguments();
            double dx=Double.parseDouble(pos.get("dx"));
            double dz=Double.parseDouble(pos.get("dz"));
            helper.assertTrue(Math.hypot(dx,dz)>3.0
                    && Math.hypot(dx,dz)<4.0,
                "Donor native motion missing: offset="+dx+","+dz);

            byte[] corrupt=packet.clone();
            corrupt[20]^=1;
            boolean tamperRejected=false;
            try{bridge.deliverAuthenticated(corrupt,secret,session,tick);}
            catch(IllegalArgumentException expected){tamperRejected=true;}
            helper.assertTrue(tamperRejected,"Receiver admitted tampered native worker frame");
            final Mob recipient=receiverBody, donor=originalDonorBody,
                mappedTarget=targetBody;
            final double originalX=recipient.getX(), originalZ=recipient.getZ();
            final float mappedTargetHealthBefore=mappedTarget.getHealth();
            final String donorName=donorType, recipientName=recipientType;
            // Minecraft entity/nav initialization runs in subsequent server
            // ticks after addFreshEntity. Do NOT dispatch at construction
            // tick: that can report a false "no path" for a real Mob body.
            helper.runAfterDelay(5,()->{
                helper.assertTrue(level.getEntity(recipientId)==recipient
                        && recipient.isAlive(),
                    "Real recipient not registered/alive at native dispatch tick");
                var result=bridge.deliverAuthenticated(packet,secret,session,tick);
                helper.assertTrue(result.status()==CapturedIntentBridge.Status.DISPATCHED
                        && result.outcomes().size()==expectedActions
                        && result.outcomes().get(0).status()==BodyActionGate.Status.EXECUTED
                        && result.outcomes().get(1).status()==BodyActionGate.Status.EXECUTED
                        && (!requireAttack || result.outcomes().get(2).status()
                            ==BodyActionGate.Status.UNAVAILABLE),
                    "Recipient body rejected captured native movement/attack actions"
                    +" receiptStatus="+result.status()+" diagnostic="+result.diagnostic()
                    +" donor="+donorName+" recipient="+recipientName
                    +" path="+recipient.getNavigation().getPath()
                    +" onGround="+recipient.onGround()
                    +" requestedDx="+dx+" requestedDz="+dz
                    +" outcomes="+result.outcomes());
                helper.assertTrue(bridge.deliverAuthenticated(packet,secret,session,tick)
                        .status()==CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
                    "Signed native worker frame replay not rejected");
                helper.assertTrue(recipient.getUUID().equals(recipientId)
                        && Float.compare(recipient.getHealth(),healthBefore)==0
                        && Float.compare(donor.getHealth(),donorHealthBefore)==0
                        && mappedTarget.isAlive(),
                    "Worker frame replaced physical body or modified its mapped target");
                if(requireAttack)
                    System.out.println("FF-NATIVE-WORKER RECIPIENT_ATTACK_DISPATCH PASS"
                        +" recipient="+recipientName
                        +" mappedTarget="+stimulusId
                        +" targetHealthBefore="+mappedTargetHealthBefore
                        +" targetHealthAfter="+mappedTarget.getHealth()
                        +" outcome="+result.outcomes().get(2).status()
                        +" reason="+result.outcomes().get(2).reason());

                helper.runAfterDelay(50,()->{
                    try {
                        double moved=Math.hypot(recipient.getX()-originalX,
                            recipient.getZ()-originalZ);
                        helper.assertTrue(moved>0.20,
                            "REAL recipient did not physically follow donor-native navigation"
                            +" donor="+donorName+" recipient="+recipientName
                            +" moved="+moved
                            +" onGround="+recipient.onGround()
                            +" nav="+recipient.getNavigation().isInProgress());
                        helper.assertTrue(recipient.getUUID().equals(recipientId)
                                && Float.compare(recipient.getHealth(),healthBefore)==0
                                && Float.compare(donor.getHealth(),donorHealthBefore)==0,
                            "Native host moved substitute or altered physical body");
                        System.out.println("FF-NATIVE-WORKER TWO_JVM_E2E PASS"
                            +" donor="+donorName+" recipient="+recipientName
                            +" nativeAIExecutedInSeparateNeoForgeProcess=true"
                            +" authenticatedRelativeMovement=true"
                            +" realRecipientMoved="+moved
                            +" signedFrameReplayRejected=true"
                            +" completeNativeMindTransfer=false");
                        helper.succeed();
                    } finally {
                        recipient.discard();
                        donor.discard();
                        mappedTarget.discard();
                        setArenaChunks(level,ticketOwner,ticketBounds,false);
                    }
                });
            });
        } catch(Exception failure) {
            if(receiverBody!=null)receiverBody.discard();
            if(originalDonorBody!=null)originalDonorBody.discard();
            if(targetBody!=null)targetBody.discard();
            setArenaChunks(level,ticketOwner,ticketBounds,false);
            throw new AssertionError("External native AI worker receiver failure",failure);
        }
    }

    /** Real command lifecycle through Brigadier, live mobs and 40 physical ticks. */
    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        FULL_COMMAND_WORKFLOW = FUNCTIONS.register(
            "full_command_workflow", () -> DetachedProbeGameTests::testFullCommandWorkflow);

    @SuppressWarnings("removal")
    private static void testFullCommandWorkflow(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(FreakyFriday.BOOK.get()));

        Mob donor = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        Mob recipient = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(donor != null && recipient != null,
            "Command workflow requires real Cow and Zombie mobs");
        BlockPos p = helper.absolutePos(new BlockPos(1, 1, 1));
        donor.setPos(p.getX()+0.5, p.getY(), p.getZ()+0.5);
        recipient.setPos(p.getX()+3.5, p.getY(), p.getZ()+0.5);
        donor.setNoAi(true);
        recipient.setNoAi(true);
        donor.setNoGravity(true);
        recipient.setNoGravity(true);
        helper.assertTrue(level.addFreshEntity(donor) && level.addFreshEntity(recipient),
            "Command workflow requires registered physical actors");

        var source = level.getServer().createCommandSourceStack().withEntity(player);
        var dispatcher = level.getServer().getCommands().getDispatcher();
        try {
            // Exactly the same selection procedure called from BookInteractions;
            // no mouse clicks or manual mob summons required.
            MindSwapSessions.selectDiagnosticForTest(player, donor);
            helper.assertTrue(dispatcher.execute("freakyfriday inspect", source) == 1,
                "Real inspect command was rejected");
            helper.assertTrue(dispatcher.execute("freakyfriday hostprobe", source) == 1,
                "Real hostprobe command was rejected");
            helper.assertTrue(dispatcher.execute("freakyfriday hostprobe40", source) == 1,
                "Real hostprobe40 command was rejected");

            MindSwapSessions.selectDiagnosticForTest(player, recipient);
            helper.assertTrue(MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                    donor.getUUID(), recipient.getUUID()),
                "Book event selections did not lock Cow -> Zombie");
            MindSwapSessions.selectDiagnosticForTest(player, donor);
            helper.assertTrue(MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                    donor.getUUID(), recipient.getUUID()),
                "Third book interaction silently replaced the locked pair");
            helper.assertTrue(dispatcher.execute("freakyfriday dualprobe", source) == 1,
                "Real dualprobe command was rejected");
            helper.assertTrue(dispatcher.execute("freakyfriday dualprobe40", source) == 1,
                "Real dualprobe40 command was rejected");
            helper.assertTrue(DetachedHostMirrorProbe.hasActiveProbe(recipient.getUUID()),
                "dualprobe40 command did not create an active 40-tick host session");
            // Exercise unsupported player diagnostic selection DIRECTLY;
            // the public /self route now begins real player/mob possession.
            MindSwapSessions.selectDiagnosticForTest(player, player);
            helper.assertTrue(MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                    donor.getUUID(), recipient.getUUID()),
                "Unsupported player selection changed the locked NPC pair");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
            throw new AssertionError("Real command workflow rejected by Brigadier", error);
        }
        System.out.println("FF-COMMANDTEST book_selection_lock PASS");
        System.out.println("FF-COMMANDTEST inspect_hostprobe_hostprobe40 PASS");
        System.out.println("FF-COMMANDTEST dualprobe_dualprobe40 PASS");
        // Do not claim completion merely because the command accepted the
        // request. The recipient must actually tick 40 times and be removed.
        helper.runAfterDelay(65, () -> {
            try {
                helper.assertTrue(!DetachedHostMirrorProbe.hasActiveProbe(recipient.getUUID()),
                    "40-tick detached-host command did not terminate");
                helper.assertTrue(MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                        donor.getUUID(), recipient.getUUID()),
                    "Selected diagnostic pair changed during physical ticking");
                helper.assertTrue(dispatcher.execute("freakyfriday cancel", source) == 1,
                    "Real cancel command was rejected");
                helper.assertTrue(!MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                        donor.getUUID(), recipient.getUUID()),
                    "Cancel left the locked diagnostic pair active");
                MindSwapSessions.selectDiagnosticForTest(player, donor);
                helper.assertTrue(!MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                        donor.getUUID(), recipient.getUUID()),
                    "Cancel + same-tick reselect incorrectly recreated a locked pair");
                System.out.println("FF-COMMANDTEST mirror40_completion PASS");
                System.out.println("FF-COMMANDTEST cancel_reselect PASS");
                System.out.println("FF-COMMANDTEST full_workflow PASS");
                helper.succeed();
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException error) {
                throw new AssertionError("Cancel command failed", error);
            } finally {
                MindSwapSessions.clearOnLogout(player.getUUID());
                donor.discard();
                recipient.discard();
            }
        });
    }

    private static void testCrossSpecies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int index = 0;
        for (Pair pair : CASES) {
            Mob donor = pair.donorType().create(level, EntitySpawnReason.COMMAND);
            Mob body = pair.recipientType().create(level, EntitySpawnReason.COMMAND);
            helper.assertTrue(donor != null && body != null,
                pair.label() + ": registered factories must create living Mobs");

            // Place both actual world actors in the loaded GameTest structure,
            // but never add the third, detached donor-class host.
            BlockPos p = helper.absolutePos(new BlockPos(
                1 + (index % 2) * 3, 1, 1 + ((index / 2) % 2) * 3));
            donor.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
            body.setPos(p.getX() + 1.5, p.getY(), p.getZ() + 0.5);
            helper.assertTrue(level.addFreshEntity(donor) && level.addFreshEntity(body),
                pair.label() + ": physical donor and recipient must both be registered");

            ProbeBodySnapshot donorBefore = ProbeBodySnapshot.take(donor);
            ProbeBodySnapshot bodyBefore = ProbeBodySnapshot.take(body);
            DetachedMindHostProbe.Creation result =
                DetachedMindHostProbe.construct(donor, body, "real-neoforge-gametest-"+pair.label());
            helper.assertTrue(result.host() != null,
                pair.label() + ": detached construction failed: " + result.problem());

            Mob host = result.host();
            helper.assertTrue(host != donor && host != body,
                pair.label() + ": detached host is not distinct");
            helper.assertTrue(host.getType() == pair.donorType(),
                pair.label() + ": host type must be donor type");
            helper.assertTrue(host.getClass() == donor.getClass(),
                pair.label() + ": host Java subclass must be donor class");
            helper.assertTrue(level.getEntity(host.getUUID()) == null,
                pair.label() + ": host unexpectedly registered");
            helper.assertTrue(donorBefore.unchanged(donor) && bodyBefore.unchanged(body),
                pair.label() + ": factory mutated original physical bodies");

            host.setPos(body.getX(), body.getY(), body.getZ());
            helper.assertTrue(host.position().distanceToSqr(body.position()) < 0.000001,
                pair.label() + ": host not aligned to recipient position");
            helper.assertTrue(level.getEntity(host.getUUID()) == null,
                pair.label() + ": positioning host caused world registration");
            helper.assertTrue(donorBefore.unchanged(donor) && bodyBefore.unchanged(body),
                pair.label() + ": positioning host mutated physical bodies");

            System.out.println("FF-GAMETEST pair "+pair.label()+" PASS");
            donor.discard();
            body.discard();
            index++;
        }
        System.out.println("FF-GAMETEST detached_cross_species PASS");
        helper.succeed();
    }
}
