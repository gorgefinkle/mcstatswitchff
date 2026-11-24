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
        rejects(()->NativeMindWorkerLaunchCommand.build("java",List.of(),Path.of("worker"),768),
            "missing parent launch args rejected");
        rejects(()->NativeMindWorkerLaunchCommand.build("java",List.of("Main"),Path.of("worker"),128),
            "unbounded undersized heap rejected");
        System.out.println("PASS NativeMindWorkerLaunchCommandSelfTest: "+checks
            +" assertions; cloned NeoForge launch, isolated worker mode/world, bounded heap and GameTest-mode stripping");
    }
}
