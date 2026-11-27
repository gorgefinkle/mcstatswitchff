package dev.freakyfriday;

import dev.freakyfriday.mind.MindHostRegistry;
import dev.freakyfriday.mind.MindSwapSessions;
import java.util.Comparator;
import java.util.List;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
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

    private static List<Mob> nearbyMobs(net.minecraft.commands.CommandSourceStack source) {
        var position = source.getPosition();
        AABB bounds = new AABB(
            position.x - 256, -64, position.z - 256,
            position.x + 256, 384, position.z + 256
        );
        return source.getLevel().getEntities(null, bounds, entity -> entity instanceof Mob)
            .stream()
            .filter(Mob.class::isInstance)
            .map(Mob.class::cast)
            .sorted(Comparator.comparingDouble(Mob::getX).thenComparingDouble(Mob::getZ))
            .toList();
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
            .then(Commands.literal("debug-exchange-loaded")
                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .executes(ctx -> {
                    List<Mob> mobs = nearbyMobs(ctx.getSource());
                    if (mobs.size() < 2) {
                        ctx.getSource().sendFailure(Component.literal(
                            "Debug exchange needs at least two loaded Mob bodies near the command source; found " + mobs.size() + "."));
                        return 0;
                    }
                    Mob first = mobs.get(0);
                    Mob second = mobs.get(1);
                    try {
                        MindHostRegistry.debugExchangeControllers(first, second);
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "DEBUG exchanged loaded controllers: " + first.getType() + " / " + second.getType()), true);
                        return 1;
                    } catch (RuntimeException failure) {
                        ctx.getSource().sendFailure(Component.literal("Debug exchange failed: " + failure.getMessage()));
                        return 0;
                    }
                }))
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
                })))
            .then(Commands.literal("debug-tick-loaded")
                .requires(source -> source.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.GAMEMASTERS)))
                .executes(ctx -> {
                    List<Mob> mobs = nearbyMobs(ctx.getSource());
                    if (mobs.size() < 2) {
                        ctx.getSource().sendFailure(Component.literal(
                            "Debug tick needs at least two loaded Mob bodies near the command source; found " + mobs.size() + "."));
                        return 0;
                    }
                    for (Mob body : mobs.subList(0, 2)) {
                        try {
                            MindHostRegistry.debugTickBody(body);
                        } catch (RuntimeException failure) {
                            ctx.getSource().sendFailure(Component.literal("Debug tick failed: " + failure.getMessage()));
                            return 0;
                        }
                    }
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "DEBUG ticked the first two loaded Mob bodies through their assigned native controllers."), true);
                    return 1;
                })));
    }
}
