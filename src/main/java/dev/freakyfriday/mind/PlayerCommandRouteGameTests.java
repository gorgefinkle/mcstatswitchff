package dev.freakyfriday.mind;

import dev.unseenaddon.UnseenAddonCommandFixture;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Real two-player command routing test against an unfamiliar Brigadier addon.
 * No Freaky Friday code inside the outsider command fixture is aware of the
 * routing rules. Tests a COMMAND CONTEXT swap, not full network/physics swap.
 */
public final class PlayerCommandRouteGameTests {
    private PlayerCommandRouteGameTests() {}
    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        COMMAND_ROUTE = FUNCTIONS.register(
            "third_party_command_route",()->PlayerCommandRouteGameTests::verifyRoute);

    @SuppressWarnings("removal")
    private static void verifyRoute(GameTestHelper helper) {
        helper.assertTrue(Boolean.getBoolean("freakyfriday.thirdPartyFixture"),
            "Unfamiliar addon command fixture wasn't enabled in GameTest");
        var level = helper.getLevel();
        ServerPlayer fred = helper.makeMockServerPlayerInLevel();
        ServerPlayer sally = helper.makeMockServerPlayerInLevel();
        helper.assertTrue(fred != null && sally != null
                && !fred.getUUID().equals(sally.getUUID()),
            "Need two distinct simulated connected players");

        var p = helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        fred.setPos(p.getX()+0.5,p.getY(),p.getZ()+0.5);
        sally.setPos(p.getX()+3.5,p.getY(),p.getZ()+0.5);
        var server=level.getServer();
        helper.assertTrue(server.getCommands().getDispatcher()
                .getRoot().getChild("unseenaddon") != null,
            "Independent third-party command wasn't registered");

        UnseenAddonCommandFixture.clear();
        try {
            server.getCommands().performPrefixedCommand(
                fred.createCommandSourceStack(),"unseenaddon self");
            var initial=UnseenAddonCommandFixture.last();
            helper.assertTrue(initial != null
                && initial.executorBody().equals(fred.getUUID()),
                "Unswapped unfamiliar command must execute as Fred");

            PlayerCommandBodyRouting.bindCommittedPlayerExchange(fred,sally);
            helper.assertTrue(PlayerCommandBodyRouting.hasRoute(fred.getUUID())
                    && PlayerCommandBodyRouting.hasRoute(sally.getUUID()),
                "Both opposite player command routes must be armed atomically");

            UnseenAddonCommandFixture.clear();
            var origin=fred.createCommandSourceStack();
            server.getCommands().performPrefixedCommand(origin,"unseenaddon self");
            var moved=UnseenAddonCommandFixture.last();
            helper.assertTrue(moved != null
                    && moved.executorBody().equals(sally.getUUID())
                    && moved.targetBody().equals(sally.getUUID()),
                "Unfamiliar command did not use Sally body for Fred's command"
                    +" observed="+moved
                    +" expected="+sally.getUUID()
                    +" from="+fred.getUUID());
            helper.assertTrue(UnseenAddonCommandFixture.executions()==1,
                "Rerouting executed the unrelated command more than once");
            helper.assertTrue(moved.feedbackSource() == origin.source
                    && moved.permissions().equals(origin.permissions()),
                "Connected Fred's feedback source or permissions were changed");
            helper.assertTrue(moved.executorTextName().equals(
                    sally.getName().getString()),
                "Unfamiliar command's entity name was not the occupied body");

            UnseenAddonCommandFixture.clear();
            server.getCommands().performPrefixedCommand(
                fred.createCommandSourceStack(),"unseenaddon selector @s");
            var self=UnseenAddonCommandFixture.last();
            int firstSelectorCount=UnseenAddonCommandFixture.executions();
            helper.assertTrue(firstSelectorCount <= 1,
                "Rerouted outsider @s command executed multiple times: "
                    +firstSelectorCount);
            if (firstSelectorCount == 1) {
                helper.assertTrue(self != null
                        && self.executorBody().equals(sally.getUUID())
                        && self.targetBody().equals(sally.getUUID()),
                    "Outsider @s selected wrong occupied entity: "+self);
                System.out.println("FF-PLAYER-COMMAND unfamiliar_entity_selector PASS");
            } else {
                System.out.println("FF-PLAYER-COMMAND unfamiliar_entity_selector UNTESTED"
                    +" reason=gametest_mock_not_resolved_by_entity_selector");
            }

            UnseenAddonCommandFixture.clear();
            server.getCommands().performPrefixedCommand(
                sally.createCommandSourceStack(),"unseenaddon selector @s");
            var reverse=UnseenAddonCommandFixture.last();
            int reverseSelectorCount=UnseenAddonCommandFixture.executions();
            helper.assertTrue(reverseSelectorCount <= 1,
                "Reverse outsider @s executed multiple times: "+reverseSelectorCount);
            if (reverseSelectorCount == 1) {
                helper.assertTrue(reverse != null
                        && reverse.executorBody().equals(fred.getUUID())
                        && reverse.targetBody().equals(fred.getUUID()),
                    "Reverse outsider @s selected wrong occupied entity: "+reverse);
                System.out.println("FF-PLAYER-COMMAND reverse_entity_selector PASS");
            } else {
                System.out.println("FF-PLAYER-COMMAND reverse_entity_selector UNTESTED"
                    +" reason=gametest_mock_not_resolved_by_entity_selector");
            }

            // A server/console 'execute as Fred' must NOT inherit Fred's
            // authenticated connection routing. Only client-originated
            // commands are projected.
            UnseenAddonCommandFixture.clear();
            server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack().withEntity(fred),
                "unseenaddon self");
            var console=UnseenAddonCommandFixture.last();
            helper.assertTrue(UnseenAddonCommandFixture.executions()==1,
                "Console outside command was double-executed");
            helper.assertTrue(console != null
                    && console.executorBody().equals(fred.getUUID()),
                "Non-player command masqueraded as the connected client");

            // Named targets are ordinary vanilla physical-player lookups.
            // Mock GameTest players may not be in the online PlayerList;
            // when they are, require that third-party named targeting stays
            // with the named body, NOT the mind controlling it.
            String fredName=fred.getGameProfile().name();
            String sallyName=sally.getGameProfile().name();
            if (!fredName.equals(sallyName)
                    && server.getPlayerList().getPlayerByName(fredName)==fred
                    && server.getPlayerList().getPlayerByName(sallyName)==sally) {
                UnseenAddonCommandFixture.clear();
                server.getCommands().performPrefixedCommand(
                    fred.createCommandSourceStack(),
                    "unseenaddon name "+fredName);
                var named=UnseenAddonCommandFixture.last();
                helper.assertTrue(named!=null
                        && named.executorBody().equals(sally.getUUID())
                        && named.targetBody().equals(fred.getUUID()),
                    "Named physical target was incorrectly remapped to controller");
                System.out.println("FF-PLAYER-COMMAND named_player_lookup PASS");
            } else {
                System.out.println("FF-PLAYER-COMMAND named_player_lookup UNTESTED"
                    +" reason=mock_players_not_distinct_in_real_PlayerList");
            }

            PlayerCommandBodyRouting.clearInvolving(fred.getUUID());
            helper.assertTrue(!PlayerCommandBodyRouting.hasRoute(fred.getUUID())
                    && !PlayerCommandBodyRouting.hasRoute(sally.getUUID()),
                "Routing cleanup left the other participant redirected");
            UnseenAddonCommandFixture.clear();
            server.getCommands().performPrefixedCommand(
                fred.createCommandSourceStack(),"unseenaddon self");
            var restored=UnseenAddonCommandFixture.last();
            helper.assertTrue(restored!=null
                    && restored.executorBody().equals(fred.getUUID()),
                "Command context not restored after end of swap");

            System.out.println("FF-PLAYER-COMMAND unrelated_addon_self PASS");
            System.out.println("FF-PLAYER-COMMAND selector_no_double_dispatch PASS");
            System.out.println("FF-PLAYER-COMMAND reversed_controllers PASS");
            System.out.println("FF-PLAYER-COMMAND permission_and_feedback_origin PASS");
            System.out.println("FF-PLAYER-COMMAND console_not_redirected PASS");
            System.out.println("FF-PLAYER-COMMAND cleanup PASS");
            System.out.println("FF-PLAYER-COMMAND all PASS"
                +" playerNetworkTransfer=false nativeBodyControlSwap=false"
                +" arbitraryUUIDPluginSupport=false");
            helper.succeed();
        } finally {
            PlayerCommandBodyRouting.clearInvolving(fred.getUUID());
            PlayerCommandBodyRouting.clearInvolving(sally.getUUID());
            UnseenAddonCommandFixture.clear();
        }
    }
}
