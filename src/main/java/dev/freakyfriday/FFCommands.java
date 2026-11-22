package dev.freakyfriday;

import dev.freakyfriday.mind.MindSwapSessions;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.minecraft.server.level.ServerPlayer;

/** Commands are only keyboard and cancel affordances; no category menu. */
public final class FFCommands {
    private FFCommands() {}

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            MindSwapSessions.clearOnLogout(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerStop(ServerStoppedEvent event) {
        MindSwapSessions.clearOnServerStop();
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("freakyfriday")
            .then(Commands.literal("self").executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                MindSwapSessions.select(player, player);
                return 1;
            }))
            .then(Commands.literal("cancel").executes(ctx -> {
                MindSwapSessions.cancel(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("inspect").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                MindSwapSessions.inspect(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("hostprobe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                MindSwapSessions.hostProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("hostprobe40").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                MindSwapSessions.timedHostProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("dualprobe").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                MindSwapSessions.dualInstanceProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("dualprobe40").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)).executes(ctx -> {
                MindSwapSessions.timedDualInstanceProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            // One-command diagnostics: no book, spawning, manual target
            // selection or donor AI execution. Operator and disposable world only.
            .then(Commands.literal("test").requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("suite").executes(ctx ->
                    FFDiagnosticAutoTest.runSuite(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("pair")
                    .then(Commands.argument("donor", StringArgumentType.word())
                        .then(Commands.argument("recipient", StringArgumentType.word())
                            .executes(ctx -> FFDiagnosticAutoTest.runPair(
                                ctx.getSource().getPlayerOrException(),
                                StringArgumentType.getString(ctx, "donor"),
                                StringArgumentType.getString(ctx, "recipient"))))))));
    }
}
