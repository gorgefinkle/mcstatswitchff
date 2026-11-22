package dev.unseenaddon;

import com.mojang.brigadier.arguments.StringArgumentType;
import java.util.UUID;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * TEST-ONLY independent command, registered with vanilla Brigadier exactly
 * as any unrelated NeoForge mod would. This class DOES NOT consult Freaky
 * Friday's routing map or carry any per-mod compatibility logic.
 *
 * Enabled exclusively in the disposable GameTest JVM; never on ordinary
 * player servers. The last observation is only for test assertions.
 */
public final class UnseenAddonCommandFixture {
    private UnseenAddonCommandFixture() {}

    public record Observation(UUID executorBody, UUID targetBody,
                              String executorTextName, Object permissions,
                              Object feedbackSource) {}
    private static volatile Observation last;
    private static int executions;

    public static Observation last() { return last; }
    public static int executions() { return executions; }
    public static void clear() { last = null; executions = 0; }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        if (!Boolean.getBoolean("freakyfriday.thirdPartyFixture")) return;
        event.getDispatcher().register(Commands.literal("unseenaddon")
            .then(Commands.literal("self").executes(ctx -> {
                var src=ctx.getSource();
                ServerPlayer entity=src.getPlayerOrException();
                executions++;
                last=new Observation(entity.getUUID(),entity.getUUID(),
                    src.getTextName(),src.permissions(),src.source);
                return 1;
            }))
            .then(Commands.literal("selector")
                .then(Commands.argument("target",EntityArgument.entity())
                    .executes(ctx -> {
                        var src=ctx.getSource();
                        ServerPlayer entity=src.getPlayerOrException();
                        var target=EntityArgument.getEntity(ctx,"target");
                        executions++;
                last=new Observation(entity.getUUID(),target.getUUID(),
                            src.getTextName(),src.permissions(),src.source);
                        return 1;
                    })))
            .then(Commands.literal("name")
                .then(Commands.argument("target",StringArgumentType.word())
                    .executes(ctx -> {
                        var src=ctx.getSource();
                        ServerPlayer entity=src.getPlayerOrException();
                        String name=StringArgumentType.getString(ctx,"target");
                        ServerPlayer target=src.getServer().getPlayerList()
                            .getPlayerByName(name);
                        executions++;
                last=new Observation(entity.getUUID(),
                            target==null?null:target.getUUID(),src.getTextName(),
                            src.permissions(),src.source);
                        return target==null?0:1;
                    }))));
    }
}
