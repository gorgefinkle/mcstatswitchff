package dev.freakyfriday.mind;

import java.util.List;
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
            MindSwapSessions.selectFromInteraction(player, donor);
            helper.assertTrue(dispatcher.execute("freakyfriday inspect", source) == 1,
                "Real inspect command was rejected");
            helper.assertTrue(dispatcher.execute("freakyfriday hostprobe", source) == 1,
                "Real hostprobe command was rejected");
            helper.assertTrue(dispatcher.execute("freakyfriday hostprobe40", source) == 1,
                "Real hostprobe40 command was rejected");

            MindSwapSessions.selectFromInteraction(player, recipient);
            helper.assertTrue(MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                    donor.getUUID(), recipient.getUUID()),
                "Book event selections did not lock Cow -> Zombie");
            MindSwapSessions.selectFromInteraction(player, donor);
            helper.assertTrue(MindSwapSessions.diagnosticPairMatches(player.getUUID(),
                    donor.getUUID(), recipient.getUUID()),
                "Third book interaction silently replaced the locked pair");
            helper.assertTrue(dispatcher.execute("freakyfriday dualprobe", source) == 1,
                "Real dualprobe command was rejected");
            helper.assertTrue(dispatcher.execute("freakyfriday dualprobe40", source) == 1,
                "Real dualprobe40 command was rejected");
            helper.assertTrue(DetachedHostMirrorProbe.hasActiveProbe(recipient.getUUID()),
                "dualprobe40 command did not create an active 40-tick host session");
            helper.assertTrue(dispatcher.execute("freakyfriday self", source) == 1,
                "Real self command route failed");
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
                MindSwapSessions.selectFromInteraction(player, donor);
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
