package dev.freakyfriday;

import dev.freakyfriday.mind.MindHostRegistry;
import dev.freakyfriday.mind.MindSwapSessions;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** User commands plus explicitly operator-only native AI dispatch diagnostics. */
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
            .then(Commands.literal("debug-exchange")
                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.argument("first", EntityArgument.entity())
                    .then(Commands.argument("second", EntityArgument.entity()).executes(ctx -> {
                        Entity firstEntity = EntityArgument.getEntity(ctx, "first");
                        Entity secondEntity = EntityArgument.getEntity(ctx, "second");
                        if (!(firstEntity instanceof Mob first) || !(secondEntity instanceof Mob second)) {
                            ctx.getSource().sendFailure(Component.literal("Debug exchange requires two Mob bodies."));
                            return 0;
                        }
                        try {
                            MindHostRegistry.debugExchangeControllers(first, second);
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "DEBUG controller/body assignment exchanged. This is not a release-ready AI swap."), true);
                            return 1;
                        } catch (RuntimeException failure) {
                            ctx.getSource().sendFailure(Component.literal("Debug exchange failed: " + failure.getMessage()));
                            return 0;
                        }
                    }))))
            .then(Commands.literal("debug-tick")
                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .then(Commands.argument("body", EntityArgument.entity()).executes(ctx -> {
                    Entity entity = EntityArgument.getEntity(ctx, "body");
                    if (!(entity instanceof Mob body)) {
                        ctx.getSource().sendFailure(Component.literal("Debug tick requires a Mob body."));
                        return 0;
                    }
                    try {
                        MindHostRegistry.debugTickBody(body);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "DEBUG native controller/body tick invoked for " + body.getType() + "."), true);
                        return 1;
                    } catch (RuntimeException failure) {
                        ctx.getSource().sendFailure(Component.literal("Debug tick failed: " + failure.getMessage()));
                        return 0;
                    }
                }))));
    }
}
