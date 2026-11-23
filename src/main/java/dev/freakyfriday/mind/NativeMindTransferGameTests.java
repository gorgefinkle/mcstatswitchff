package dev.freakyfriday.mind;

import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Production acceptance gate: exercises the real native mind exchange entry
 * point on a reciprocal cross-species pair. Detached construction is not a
 * substitute for this test.
 */
public final class NativeMindTransferGameTests {
    private NativeMindTransferGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION, "freakyfriday");

    public static final DeferredHolder<Consumer<GameTestHelper>, Consumer<GameTestHelper>>
        ZOMBIE_CHICKEN_RECIPROCAL = FUNCTIONS.register(
            "native_zombie_chicken_reciprocal",
            () -> NativeMindTransferGameTests::testZombieChickenReciprocal);

    private static void testZombieChickenReciprocal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Mob zombie = EntityTypes.ZOMBIE.create(level, EntitySpawnReason.COMMAND);
        Mob chicken = EntityTypes.CHICKEN.create(level, EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie != null && chicken != null,
            "Zombie and Chicken factories must create real Mob instances");

        BlockPos origin = helper.absolutePos(new BlockPos(2, 1, 2));
        zombie.setPos(origin.getX() + 0.5, origin.getY(), origin.getZ() + 0.5);
        chicken.setPos(origin.getX() + 3.5, origin.getY(), origin.getZ() + 0.5);
        helper.assertTrue(level.addFreshEntity(zombie) && level.addFreshEntity(chicken),
            "Both physical bodies must register in the same live test level");

        String forward = NativeBrainSwap.exchange(zombie, chicken);
        helper.assertTrue(forward == null,
            "Zombie mind -> Chicken body must perform a real native exchange; rejected: " + forward);

        String reverse = NativeBrainSwap.exchange(zombie, chicken);
        helper.assertTrue(reverse == null,
            "Chicken mind -> Zombie body must perform the reciprocal native exchange; rejected: " + reverse);

        helper.assertTrue(zombie.isAlive() && chicken.isAlive()
                && level.getEntity(zombie.getUUID()) == zombie
                && level.getEntity(chicken.getUUID()) == chicken,
            "Mind exchange must not replace, remove, or kill either physical body");

        System.out.println("FF-NATIVE-MIND zombie_chicken_reciprocal PASS");
        zombie.discard();
        chicken.discard();
        helper.succeed();
    }
}
