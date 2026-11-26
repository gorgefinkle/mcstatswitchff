package dev.freakyfriday.mind;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/**
 * Entry point used by the book-selection flow. Mob↔Mob swaps execute in the
 * current server by installing both incoming native goal graphs on the two
 * original physical entities. This class deliberately contains no process
 * launcher and cannot create a second Minecraft server.
 */
public final class NativeMindWorkerSupervisor {
    private NativeMindWorkerSupervisor() {}

    /** Start a same-server reciprocal Player↔Mob controller exchange. */
    public static synchronized String startPlayerMob(ServerPlayer player,Mob mob) {
        return PlayerMobPossession.beginReciprocal(player,mob);
    }

    /** Live, in-process, reciprocal native controller exchange. */
    public static synchronized String startMobPair(ServerPlayer requester,Mob first,Mob second) {
        if(requester==null||first==null||second==null)
            return "A requester and two physical Mob bodies are required.";
        return GoalMindRuntime.begin(requester.getUUID(),first,second);
    }

    public static synchronized boolean cancelByAccount(UUID account) {
        return GoalMindRuntime.cancelByAccount(account);
    }

    @SubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        clearOnServerStop();
    }

    public static synchronized void clearOnServerStop() {
        GoalMindRuntime.clearOnServerStop();
    }
}
