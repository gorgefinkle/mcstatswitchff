package dev.freakyfriday.mind;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Real NeoForge game test: actual vanilla Zombie factory and Cow physical body.
 * The unregistered host's Brain/Goal/Mob ticks are NEVER executed.
 * A production build can register this harmless test function without running it.
 */
public final class DetachedProbeGameTests {
    private DetachedProbeGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, "freakyfriday");

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        DETACHED_CROSS_SPECIES = FUNCTIONS.register(
            "detached_cross_species", () -> DetachedProbeGameTests::testCrossSpecies);

    private static void testCrossSpecies(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob donor = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        Mob body = EntityTypes.COW.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(donor != null && body != null,
            "The real Zombie/Cow EntityType factories must create living Mob objects");

        BlockPos p = helper.absolutePos(new BlockPos(1, 1, 1));
        donor.setPos(p.getX() + 0.5, p.getY(), p.getZ() + 0.5);
        body.setPos(p.getX() + 3.5, p.getY(), p.getZ() + 0.5);
        helper.assertTrue(level.addFreshEntity(donor) && level.addFreshEntity(body),
            "Both original mobs must be registered before the detached factory test");

        ProbeBodySnapshot beforeDonor = ProbeBodySnapshot.take(donor);
        ProbeBodySnapshot beforeBody = ProbeBodySnapshot.take(body);
        DetachedMindHostProbe.Creation result =
            DetachedMindHostProbe.construct(donor, body, "real-neoforge-gametest");
        helper.assertTrue(result.host() != null,
            "Real cross-species host construction failed: " + result.problem());
        Mob host = result.host();
        helper.assertTrue(host != donor && host != body, "Host must be a third Java object");
        helper.assertTrue(host.getType() == EntityTypes.ZOMBIE,
            "Host EntityType must be Zombie while the visible body remains Cow");
        helper.assertTrue(host.getClass() == donor.getClass(),
            "Host concrete Java subclass must match donor");
        helper.assertTrue(level.getEntity(host.getUUID()) == null,
            "Detached host must not appear in the real ServerLevel entity map");
        helper.assertTrue(beforeDonor.unchanged(donor) && beforeBody.unchanged(body),
            "Constructing host changed one of the original real Mob snapshots");

        host.setPos(body.getX(), body.getY(), body.getZ());
        helper.assertTrue(host.position().distanceToSqr(body.position()) < 0.000001,
            "Detached Zombie cannot be positioned at Cow body's coordinates");
        helper.assertTrue(level.getEntity(host.getUUID()) == null,
            "Positioning detached Zombie unexpectedly world-registered it");
        helper.assertTrue(beforeDonor.unchanged(donor) && beforeBody.unchanged(body),
            "Positioning detached Zombie changed the donor or Cow body");

        System.out.println("FF-GAMETEST detached_cross_species PASS");
        donor.discard();
        body.discard();
        helper.succeed();
    }
}
