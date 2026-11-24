package dev.freakyfriday.mind;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Builds a child server JVM command from the currently running NeoForge JVM. */
public final class NativeMindWorkerLaunchCommand {
    private NativeMindWorkerLaunchCommand() {}


    /** Exact JVM/application arguments of the current NeoForge server process. */
    public static List<String> currentProcessArguments() {
        var processArguments=ProcessHandle.current().info().arguments();
        if(processArguments.isPresent() && processArguments.get().length>0)
            return List.copyOf(java.util.Arrays.asList(processArguments.get()));
        String command=System.getProperty("sun.java.command","");
        if(command.isBlank())
            throw new IllegalStateException("Cannot discover the current NeoForge server main class");
        List<String> result=new ArrayList<>(
            java.lang.management.ManagementFactory.getRuntimeMXBean().getInputArguments());
        String classPath=System.getProperty("java.class.path","");
        if(classPath.isBlank())
            throw new IllegalStateException("Cannot discover the current NeoForge server classpath");
        result.add("-cp");
        result.add(classPath);
        result.addAll(splitCommand(command));
        return List.copyOf(result);
    }

    private static List<String> splitCommand(String value) {
        List<String> result=new ArrayList<>();
        StringBuilder token=new StringBuilder();
        boolean quoted=false, escaped=false, present=false;
        for(int i=0;i<value.length();i++) {
            char ch=value.charAt(i);
            if(escaped) { token.append(ch); escaped=false; present=true; continue; }
            if(ch=='\\' && quoted) { escaped=true; continue; }
            if(ch=='"') { quoted=!quoted; present=true; continue; }
            if(Character.isWhitespace(ch) && !quoted) {
                if(present) { result.add(token.toString()); token.setLength(0); present=false; }
            } else { token.append(ch); present=true; }
        }
        if(escaped || quoted)
            throw new IllegalArgumentException("Malformed current Java command line");
        if(present) result.add(token.toString());
        if(result.isEmpty()) throw new IllegalStateException("Current Java command line is empty");
        return result;
    }

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
