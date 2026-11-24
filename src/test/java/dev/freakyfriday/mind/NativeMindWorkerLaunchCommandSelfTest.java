package dev.freakyfriday.mind;

import java.nio.file.Path;
import java.util.List;

public final class NativeMindWorkerLaunchCommandSelfTest {
    private NativeMindWorkerLaunchCommandSelfTest() {}
    private static int checks;
    private static void check(boolean value,String message) {
        checks++;
        if(!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action,String message) {
        try { action.run(); } catch(RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    public static void main(String[] args) {
        var command=NativeMindWorkerLaunchCommand.build("/java/bin/java",
            List.of("-Xms2G","-Xmx6G","-Dneoforge.enabledGameTestNamespaces=freakyfriday",
                "-Dfreakyfriday.thirdPartyFixture=true",
                "@user_jvm_args.txt","@libraries/neoforge/unix_args.txt","nogui"),
            Path.of("freakyfriday-workers","test-session"),768);
        check(command.get(0).equals("/java/bin/java"),"current JVM executable retained");
        check(command.get(1).equals("-Dfreakyfriday.aiWorker=true"),
            "worker mode set before launching the same NeoForge server bootstrap");
        check(command.get(2).startsWith("-Dfreakyfriday.workerRoot="),
            "worker data root is passed to child");
        check(command.contains("-Xmx768M") && !command.contains("-Xmx6G")
                && !command.contains("-Xms2G"),
            "child heap is bounded independently of parent heap");
        check(!command.contains("-Dneoforge.enabledGameTestNamespaces=freakyfriday")
                && !command.contains("-Dfreakyfriday.thirdPartyFixture=true"),
            "child starts a normal server, not the parent's GameTest/fake-client mode");
        check(command.contains("@user_jvm_args.txt")
                && command.contains("@libraries/neoforge/unix_args.txt"),
            "NeoForge launch argument files are preserved");
        check(command.indexOf("--universe")>command.indexOf("@libraries/neoforge/unix_args.txt"),
            "child universe overrides are passed to the server launch target");
        check(command.contains("--world") && command.contains("ff-native-mind-worker-world")
                && !command.contains("--level-name") && !command.contains("--gameDir"),
            "current dedicated-server CLI receives supported world option, not legacy level-name");
        check(!command.contains("--server-ip") && !command.contains("--online-mode")
                && !command.contains("--max-players") && !command.contains("--view-distance")
                && !command.contains("--simulation-distance"),
            "network and simulation settings are loaded from isolated server.properties");
        var isolated=NativeMindWorkerLaunchCommand.build("java",
            List.of("Main","--gameDir","/parent/run","--universe","/parent/world",
                "--world","parent","--port","25565","--nogui"),
            Path.of("/tmp/ff-worker/test"),768);
        check(!isolated.contains("/parent/run") && !isolated.contains("/parent/world")
                && !isolated.contains("parent") && !isolated.contains("25565"),
            "parent game data and port overrides cannot leak into child command");
        rejects(()->NativeMindWorkerLaunchCommand.build("java",List.of(),Path.of("worker"),768),
            "missing parent launch args rejected");
        rejects(()->NativeMindWorkerLaunchCommand.build("java",List.of("Main"),Path.of("worker"),128),
            "unbounded undersized heap rejected");
        System.out.println("PASS NativeMindWorkerLaunchCommandSelfTest: "+checks
            +" assertions; cloned NeoForge launch, isolated worker mode/world, bounded heap and GameTest-mode stripping");
    }
}
