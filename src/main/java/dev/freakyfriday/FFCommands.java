package dev.freakyfriday;

import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;

public final class FFCommands {
    private FFCommands() {}
    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SwapSessions.clearOnLogout(player.getUUID());
    }
    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("freakyfriday")
            .then(Commands.literal("self").executes(ctx -> {
                var player = ctx.getSource().getPlayerOrException();
                SwapSessions.select(player,player);
                return 1;
            }))
            .then(Commands.literal("cancel").executes(ctx -> {
                SwapSessions.cancel(ctx.getSource().getPlayerOrException());
                return 1;
            }))
            .then(Commands.literal("category")
                .then(Commands.argument("group",StringArgumentType.word())
                    .executes(ctx -> {
                        SwapSessions.choose(ctx.getSource().getPlayerOrException(),
                            StringArgumentType.getString(ctx, "group"));
                        return 1;
                    }))));
    }
}
