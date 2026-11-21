package dev.freakyfriday;

import dev.freakyfriday.mind.MindSwapSessions;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
            .then(Commands.literal("inspect").executes(ctx -> {
                MindSwapSessions.inspect(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("hostprobe").executes(ctx -> {
                MindSwapSessions.hostProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("hostprobe40").executes(ctx -> {
                MindSwapSessions.timedHostProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("dualprobe").executes(ctx -> {
                MindSwapSessions.dualInstanceProbe(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("dualprobe40").executes(ctx -> {
                MindSwapSessions.timedDualInstanceProbe(ctx.getSource().getPlayerOrException());
                return 1;
            })));
    }
}
