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

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        NATIVE_SPEED_COLLISION=FUNCTIONS.register(
            "native_speed_collision",()->PlayerMobGameTests::verifyNativeSpeedCollision);

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
            // Same entrypoint as a real book right-click, then the actual
            // Brigadier command that Ctrl+S sends from the player's client.
            MindSwapSessions.selectFromInteraction(player,chicken);
            var commandSource=level.getServer().createCommandSourceStack().withEntity(player);
            try {
                helper.assertTrue(level.getServer().getCommands().getDispatcher().execute(
                    "freakyfriday self",commandSource)==1,
                    "Ctrl+S /freakyfriday self command rejected by real Brigadier");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new AssertionError("Real self selection command failed",e);
            }
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
                try {
                    helper.assertTrue(level.getServer().getCommands().getDispatcher().execute(
                        "freakyfriday cancel",commandSource)==1,
                        "Real return-to-body /cancel command rejected by Brigadier");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new AssertionError("Real possession cancel command failed",e);
                }
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

    /**
     * Real collision-world behavioral regression: the SAME chicken changes
     * speed with its native movement attribute, while a stone wall continues
     * to block physical movement. No numerical mock movement is accepted.
     */
    private static void verifyNativeSpeedCollision(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null,"Chicken factory failed in native-speed regression");
        for(int x=1;x<=7;x++)for(int z=1;z<=9;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        // Full-height physical wall in the forward direction, unlike an
        // artificial setPos clamp. Real Entity.move must resolve its collision.
        for(int x=1;x<=7;x++)for(int y=1;y<=3;y++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,5)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos start=helper.absolutePos(new BlockPos(3,1,2));
        chicken.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
        var speed=chicken.getAttribute(
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed!=null,"Chicken lacks native movement speed");
        speed.setBaseValue(0.08);
        helper.assertTrue(level.addFreshEntity(chicken),
            "Real Chicken was not registered for speed/collision");
        UUID bodyId=chicken.getUUID(),owner=player.getUUID();
        float health=chicken.getHealth();
        double startZ=chicken.getZ();
        try {
            String error=PlayerMobPossession.begin(player,chicken);
            helper.assertTrue(error==null,"Native-speed possession rejected: "+error);
            // Keep input current throughout each physical tick window.
            for(int tick=1;tick<=8;tick++) {
                final int sequence=tick;
                helper.runAfterDelay(tick,()->PlayerMobPossession.input(player,
                    new PlayerMobPossession.Input(1,0,false,0,0,sequence)));
            }
            final double[] slow={Double.NaN};
            helper.runAfterDelay(10,()->{
                slow[0]=chicken.getZ()-startZ;
                helper.assertTrue(slow[0]>0.10 && slow[0]<1.1,
                    "Slow native speed failed: distance="+slow[0]);
                speed.setBaseValue(0.40);
            });
            for(int tick=11;tick<=23;tick++) {
                final int sequence=tick;
                helper.runAfterDelay(tick,()->PlayerMobPossession.input(player,
                    new PlayerMobPossession.Input(1,0,false,0,0,sequence)));
            }
            helper.runAfterDelay(25,()->{
                double total=chicken.getZ()-startZ;
                double fastDistance=total-slow[0];
                // The chicken's width must not enter the solid wall.
                double wallFront=helper.absolutePos(new BlockPos(3,1,5)).getZ();
                helper.assertTrue(Double.isFinite(slow[0])
                        && fastDistance>slow[0]+0.30,
                    "Native movement attribute did not increase travel: slow="
                        +slow[0]+" fast="+fastDistance);
                helper.assertTrue(chicken.getBoundingBox().maxZ<=wallFront+0.0001,
                    "Chicken passed through a solid vanilla block wall: box="
                        +chicken.getBoundingBox()+" wallZ="+wallFront);
                helper.assertTrue(level.getEntity(bodyId)==chicken
                        && Float.compare(health,chicken.getHealth())==0,
                    "Native-speed test replaced/damaged physical chicken");
                helper.assertTrue(PlayerMobPossession.end(player),
                    "Native-speed test cannot cancel active possession");
                helper.assertTrue(!chicken.isNoAi()
                        && !PlayerMobPossession.hasSession(owner),
                    "Native-speed cancel failed to restore AI and ownership");
                System.out.println("FF-GAMEPLAY native_body_speed PASS"
                    +" slow="+slow[0]+" accelerated="+fastDistance);
                System.out.println("FF-GAMEPLAY solid_wall_collision PASS"
                    +" maxZ="+chicken.getBoundingBox().maxZ+" wallZ="+wallFront);
                helper.succeed();
                chicken.discard();
            });
        } catch (RuntimeException failure) {
            PlayerMobPossession.clearOnLogout(owner);
            chicken.discard();
            throw failure;
        }
    }

}
