package dev.freakyfriday.mind;

import dev.freakyfriday.FreakyFriday;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * GAMEPLAY acceptance, not detached-host construction:
 * book + real Chicken + self actually enters possessed body, physical Chicken
 * moves with genuine server ticks and collision, cancel restores original AI.
 *
 * Client camera/input rendering still needs a real connected-client test.
 * Original Player body is not yet driven by donor chicken AI.
 */
public final class PlayerMobGameTests {
    private PlayerMobGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        PLAYER_CONTROLS_CHICKEN=FUNCTIONS.register(
            "player_controls_chicken",()->PlayerMobGameTests::verifyPlayerControlsChicken);

    private static void verifyPlayerControlsChicken(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null,"Actual Chicken physical entity factory failed");
        // A reproducible, actual collision platform. The default empty
        // structure has no reliable survival floor for a walking Chicken.
        for(int x=1;x<=6;x++)for(int z=1;z<=7;z++)
            level.setBlockAndUpdate(
                helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos origin=helper.absolutePos(new BlockPos(2,1,2));
        chicken.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
        chicken.setNoAi(false);
        helper.assertTrue(level.addFreshEntity(chicken),
            "Real Chicken did not register in the GameTest level");
        UUID id=chicken.getUUID();
        float health=chicken.getHealth();
        double startZ=chicken.getZ();
        var ownerId=player.getUUID();
        try {
            MindSwapSessions.select(player,chicken);
            MindSwapSessions.select(player,player);
            helper.assertTrue(PlayerMobPossession.hasSession(ownerId)
                    && id.equals(PlayerMobPossession.possessedBody(ownerId)),
                "Book pair did NOT activate genuine server-side Chicken control");
            helper.assertTrue(chicken.isNoAi(),
                "Original Chicken AI continued fighting player's movement");
            helper.assertTrue(level.getEntity(id)==chicken,
                "Playable Chicken body was replaced instead of possessed");

            helper.assertTrue(!PlayerMobPossession.hasSession(UUID.randomUUID()),
                "Unrelated player inherited another account's body");
            boolean rejected=false;
            try {
                new PlayerMobPossession.Input(Float.NaN,0,false,0,0,1);
            } catch (IllegalArgumentException badPacket) {
                rejected=true;
            }
            helper.assertTrue(rejected,"Unsafe NaN input was accepted");

            for(int i=1;i<=7;i++) {
                final int seq=i;
                helper.runAfterDelay(i*2,()->PlayerMobPossession.input(player,
                    new PlayerMobPossession.Input(1,0,false,0,0,seq)));
            }
            helper.runAfterDelay(25,()->{
                helper.assertTrue(chicken.getZ()>startZ+0.35,
                    "Chicken did NOT move under authenticated player's input"
                    +" startZ="+startZ+" actualZ="+chicken.getZ());
                helper.assertTrue(level.getEntity(id)==chicken
                    && Float.compare(health,chicken.getHealth())==0,
                    "Possession changed physical Chicken identity or health");
                helper.assertTrue(PlayerMobPossession.hasSession(ownerId),
                    "Possession session dropped during actual movement");
                MindSwapSessions.cancel(player);
                helper.assertTrue(!PlayerMobPossession.hasSession(ownerId),
                    "Cancel failed to end playable possession");
                helper.assertTrue(!chicken.isNoAi(),
                    "Cancel did not restore genuine Chicken AI");
                helper.assertTrue(level.getEntity(id)==chicken,
                    "Cancel discarded the original physical Chicken");
                System.out.println("FF-GAMEPLAY player_chicken_movement PASS"
                    +" actualDistance="+(chicken.getZ()-startZ)
                    +" realPhysicalEntity=true playerInput=true");
                System.out.println("FF-GAMEPLAY player_chicken_cancel_restore PASS");
                System.out.println("FF-GAMEPLAY player_mob_possession PASS"
                    +" cameraClientIntegrationNotProven=true"
                    +" chickenMindInPlayerBody=false");
                helper.succeed();
                chicken.discard();
                MindSwapSessions.clearOnLogout(ownerId);
            });
        } catch (RuntimeException failure) {
            MindSwapSessions.clearOnLogout(ownerId);
            chicken.discard();
            throw failure;
        }
    }
}
