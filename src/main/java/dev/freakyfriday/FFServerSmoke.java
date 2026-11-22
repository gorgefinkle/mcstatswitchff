package dev.freakyfriday;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Ordinary dedicated-server smoke instrumentation, NOT a synthetic unit test.
 * CI launches `runServer` and requires both the registered real command tree
 * and 100 actual server tick callbacks before it considers startup successful.
 * Runs without players and does not summon or swap mobs.
 */
public final class FFServerSmoke {
    private FFServerSmoke() {}
    private static boolean started, passed;
    private static int ticks;

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        started = false;
        passed = false;
        ticks = 0;
        var root = event.getServer().getCommands().getDispatcher().getRoot();
        var ff = root.getChild("freakyfriday");
        if (ff == null || ff.getChild("cancel") == null
                || ff.getChild("dualprobe") == null
                || ff.getChild("dualprobe40") == null
                || ff.getChild("inspect") == null
                || ff.getChild("hostprobe") == null
                || ff.getChild("hostprobe40") == null
                || ff.getChild("self") == null
                || ff.getChild("test") == null
                || ff.getChild("test").getChild("pair") == null
                || ff.getChild("test").getChild("suite") == null) {
            System.err.println("FF-SERVER-SMOKE COMMAND_TREE_MISSING FAIL");
            return;
        }
        started = true;
        System.out.println("FF-SERVER-SMOKE COMMANDS_READY PASS");
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if (!started || passed) return;
        if (++ticks == 100) {
            passed = true;
            System.out.println("FF-SERVER-SMOKE 100_SERVER_TICKS PASS");
        }
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        started = false;
        passed = false;
        ticks = 0;
    }
}
