package dev.freakyfriday.mind;

import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
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
