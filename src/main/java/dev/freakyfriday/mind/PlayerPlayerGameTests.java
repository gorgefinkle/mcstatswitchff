package dev.freakyfriday.mind;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * REAL two-ServerPlayer reciprocal movement, not just command route metadata.
 * Both physical player entities are moved by the OPPOSITE player's input via
 * Minecraft's collision resolver and can independently end both sessions.
 *
 * Mock GameTest players have no graphical camera or real connected clients:
 * this test does not claim a visual camera, inventory, or combat transfer.
 */
public final class PlayerPlayerGameTests {
    private PlayerPlayerGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        PLAYER_EXCHANGE = FUNCTIONS.register(
            "reciprocal_player_control",()->PlayerPlayerGameTests::verify);

    private static void verify(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer fred=helper.makeMockServerPlayerInLevel();
        ServerPlayer sally=helper.makeMockServerPlayerInLevel();
        helper.assertTrue(fred!=null && sally!=null && fred!=sally
                && !fred.getUUID().equals(sally.getUUID()),
            "Two unique real ServerPlayer entities required for reciprocal control");
        for(int x=1;x<=11;x++) for(int z=1;z<=11;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                Blocks.STONE.defaultBlockState());
        BlockPos fredOrigin=helper.absolutePos(new BlockPos(3,1,3));
        BlockPos sallyOrigin=helper.absolutePos(new BlockPos(7,1,3));
        fred.setPos(fredOrigin.getX()+0.5,fredOrigin.getY(),fredOrigin.getZ()+0.5);
        sally.setPos(sallyOrigin.getX()+0.5,sallyOrigin.getY(),sallyOrigin.getZ()+0.5);
        UUID fredId=fred.getUUID(),sallyId=sally.getUUID();
        double fredX=fred.getX(),fredZ=fred.getZ();
        double sallyX=sally.getX(),sallyZ=sally.getZ();
        float fredHealth=fred.getHealth(),sallyHealth=sally.getHealth();
        try {
            String failed=PlayerPlayerExchange.begin(fred,sally);
            helper.assertTrue(failed==null,
                "Immediate no-consent player-body swap rejected: "+failed);
            helper.assertTrue(PlayerPlayerExchange.active(fredId)
                    && PlayerPlayerExchange.active(sallyId)
                    && sallyId.equals(PlayerPlayerExchange.occupied(fredId))
                    && fredId.equals(PlayerPlayerExchange.occupied(sallyId)),
                "Both original physical players were not mapped to opposite controllers");
            helper.assertTrue(PlayerCommandBodyRouting.hasRoute(fredId)
                    && PlayerCommandBodyRouting.hasRoute(sallyId),
                "Reciprocal physical control did not activate command body routing");
            // Distinct forward vectors prove the OTHER account drives each
            // unchanged real player body. No packet can specify target ID.
            for (int tick=1;tick<=25;tick++) {
                final int seq=tick;
                helper.runAfterDelay(tick,()->{
                    PlayerPlayerExchange.input(fred,new PlayerMobPossession.Input(
                        1,0,false,0,0,seq));        // Fred moves Sally +Z
                    PlayerPlayerExchange.input(sally,new PlayerMobPossession.Input(
                        1,0,false,-90,0,seq));      // Sally moves Fred +X
                });
            }
            helper.runAfterDelay(29,()->{
                helper.assertTrue(fred.getX()>fredX+0.40,
                    "Sally's input did not actually move Fred body: before="
                        +fredX+" after="+fred.getX());
                helper.assertTrue(sally.getZ()>sallyZ+0.40,
                    "Fred's input did not actually move Sally body: before="
                        +sallyZ+" after="+sally.getZ());
                helper.assertTrue(Math.abs(fred.getZ()-fredZ)<1.0
                        && Math.abs(sally.getX()-sallyX)<1.0,
                    "Opposite controllers' movement was routed to incorrect axes");
                helper.assertTrue(fred.getUUID().equals(fredId)
                        && sally.getUUID().equals(sallyId)
                        && Float.compare(fred.getHealth(),fredHealth)==0
                        && Float.compare(sally.getHealth(),sallyHealth)==0,
                    "Reciprocal control replaced an actual player or mutated body health");
                helper.assertTrue(PlayerPlayerExchange.cancel(sally),
                    "The other swapped player cannot end the exchange");
                helper.assertTrue(!PlayerPlayerExchange.active(fredId)
                        && !PlayerPlayerExchange.active(sallyId)
                        && !PlayerCommandBodyRouting.hasRoute(fredId)
                        && !PlayerCommandBodyRouting.hasRoute(sallyId),
                    "Cancel left body control or command routing swapped");
                System.out.println("FF-GAMEPLAY player_player_reciprocal_movement PASS"
                    +" fredBodyFromSally="+(fred.getX()-fredX)
                    +" sallyBodyFromFred="+(sally.getZ()-sallyZ)
                    +" intactPhysicalPlayers=true");
                System.out.println("FF-GAMEPLAY player_player_cancel_restores_both PASS"
                    +" immediateExchange=true noConsentRequired=true");
                helper.succeed();
            });
        } catch(RuntimeException failure) {
            PlayerPlayerExchange.clearOnLogout(fredId);
            PlayerPlayerExchange.clearOnLogout(sallyId);
            throw failure;
        }
    }
}
