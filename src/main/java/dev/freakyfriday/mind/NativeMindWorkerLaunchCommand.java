package dev.freakyfriday.mind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds a child server JVM command from the currently running NeoForge JVM. */
public final class NativeMindWorkerLaunchCommand {
    private NativeMindWorkerLaunchCommand() {}

    public static List<String> build(String javaExecutable,List<String> parentArguments,
            Path workerRoot,int heapMegabytes) {
        Objects.requireNonNull(javaExecutable,"javaExecutable");
        Objects.requireNonNull(parentArguments,"parentArguments");
        Objects.requireNonNull(workerRoot,"workerRoot");
        if(javaExecutable.isBlank() || parentArguments.isEmpty())
            throw new IllegalArgumentException("Current NeoForge JVM launch command is unavailable");
        if(heapMegabytes<512 || heapMegabytes>8192)
            throw new IllegalArgumentException("Worker heap must be between 512 and 8192 MiB");
        Path root=workerRoot.toAbsolutePath().normalize();
        if(root.getParent()==null)
            throw new IllegalArgumentException("Worker root must have an owned parent directory");

        List<String> command=new ArrayList<>();
        command.add(javaExecutable);
        command.add("-Dfreakyfriday.aiWorker=true");
        command.add("-Dfreakyfriday.workerRoot="+root);
        command.add("-Xms256M");
        command.add("-Xmx"+heapMegabytes+"M");
        for(String argument:parentArguments) {
            if(argument==null || argument.isBlank()) continue;
            if(argument.startsWith("-Xms") || argument.startsWith("-Xmx")
                    || argument.startsWith("-Dfreakyfriday.aiWorker=")
                    || argument.startsWith("-Dfreakyfriday.workerRoot=")
                    || argument.startsWith("-Dfreakyfriday.thirdPartyFixture=")
                    || argument.startsWith("-Dneoforge.enabledGameTestNamespaces="))
                continue;
            command.add(argument);
        }
        // The worker gets its own world root and an ephemeral local server
        // port. The process shares no live world handles with its parent.
        command.add("--universe");
        command.add(root.toString());
        command.add("--level-name");
        command.add("world");
        command.add("--port");
        command.add("0");
        command.add("--server-ip");
        command.add("127.0.0.1");
        command.add("--online-mode");
        command.add("false");
        command.add("--max-players");
        command.add("1");
        command.add("--view-distance");
        command.add("2");
        command.add("--simulation-distance");
        command.add("2");
        command.add("--nogui");
        return List.copyOf(command);
    }
}
