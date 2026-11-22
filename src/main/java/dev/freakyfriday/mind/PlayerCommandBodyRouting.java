package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;

/**
 * Generic Brigadier command-context projection for an already-committed
 * two-PLAYER mind exchange. NO command names or third-party mod rules.
 *
 * Connected account A remains the authentication/permission and feedback
 * source, while the command entity, @s, location, facing and dimension come
 * from occupied body B. Names and UUIDs continue to resolve to physical
 * bodies through vanilla's ordinary player lookup. This routing is NOT
 * equivalent to changing account ownership, game profiles or network
 * connections. Third-party commands that bypass Brigadier's source/entity
 * APIs or key their own state on account UUID cannot be transparently
 * redirected by this layer.
 *
 * The present R0.15.4 book path still has NO committed player mind swap.
 * Only an authorized future swap engine (and disposable GameTests) may
 * call bindCommittedPlayerExchange. Do not activate this by a chat command.
 */
public final class PlayerCommandBodyRouting {
    private PlayerCommandBodyRouting() {}
    private record Route(ServerPlayer connectionOwner, ServerPlayer occupiedBody) {}
    private static final Map<UUID, Route> ROUTES = new HashMap<>();

    /**
     * Called AFTER a future engine has atomically committed a player/player
     * mind exchange and confirmed both sessions are live. This is NOT a
     * gameplay swap by itself; it only configures command context.
     */
    public static synchronized void bindCommittedPlayerExchange(
            ServerPlayer first, ServerPlayer second) {
        if (first == null || second == null || first == second
                || first.getUUID().equals(second.getUUID()))
            throw new IllegalArgumentException("Two distinct authenticated players required");
        if (first.level().getServer() != second.level().getServer())
            throw new IllegalArgumentException("Players belong to different servers");
        if (first.isRemoved() || second.isRemoved())
            throw new IllegalArgumentException("Removed player body");
        if (ROUTES.containsKey(first.getUUID()) || ROUTES.containsKey(second.getUUID()))
            throw new IllegalStateException("Existing command routing session must end first");
        ROUTES.put(first.getUUID(), new Route(first,second));
        ROUTES.put(second.getUUID(), new Route(second,first));
    }

    public static synchronized boolean hasRoute(UUID account) {
        return ROUTES.containsKey(account);
    }

    /** Invalidate both sides on ANY logout, death/body unload or swap exit. */
    public static synchronized void clearInvolving(UUID connectionOrBody) {
        Route found = ROUTES.get(connectionOrBody);
        if (found == null) return;
        ROUTES.remove(found.connectionOwner().getUUID());
        ROUTES.remove(found.occupiedBody().getUUID());
    }

    public static synchronized void clearOnServerStop() {
        ROUTES.clear();
    }

    /**
     * Fires only for normal server command execution, before Brigadier
     * dispatch. The authenticated player's CommandSource is retained;
     * replacing it with the body's source could grant privileges or misroute
     * feedback. We reparse the entire command under the projected entity,
     * including selectors and redirects, rather than replacing parsed text.
     */
    @SubscribeEvent
    public static void onCommand(CommandEvent event) {
        var parsed = event.getParseResults();
        CommandSourceStack original = parsed.getContext().getSource();
        if (!(original.getEntity() instanceof ServerPlayer connected)) return;

        // Console, command blocks and /execute as must not masquerade as
        // a connected user's own command. Their original source differs.
        if (original.source != connected.commandSource()) return;

        Route route;
        synchronized (PlayerCommandBodyRouting.class) {
            route = ROUTES.get(connected.getUUID());
        }
        if (route == null) return;
        if (route.connectionOwner() != connected
                || route.occupiedBody().isRemoved()
                || route.occupiedBody().level().getServer() != connected.level().getServer()) {
            clearInvolving(connected.getUUID());
            event.setCanceled(true); // fail closed; do not run in wrong body
            original.sendFailure(Component.literal(
                "[Freaky Friday] Player body route ended; command was not executed."));
            return;
        }

        ServerPlayer body = route.occupiedBody();
        CommandSourceStack projected = original
            .withEntity(body)
            .withLevel(body.level())
            .withPosition(body.position())
            .withRotation(body.getRotationVector());

        // Credentials, permission checks and feedback source remain those
        // of the connected user, even though getPlayerOrException() is B.
        if (!original.permissions().equals(projected.permissions())
                || projected.source != original.source) {
            event.setCanceled(true);
            original.sendFailure(Component.literal(
                "[Freaky Friday] Unsafe command permission/source change blocked."));
            return;
        }

        var dispatcher = original.getServer().getCommands().getDispatcher();
        event.setParseResults(dispatcher.parse(parsed.getReader().getString(), projected));
    }
}
