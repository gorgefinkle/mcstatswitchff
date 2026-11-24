package dev.freakyfriday.mind;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Owns live reciprocal Mob mind sessions. Each incoming Mob mind runs inside
 * its own disposable NeoForge server process; only signed data crosses back
 * to the live server, and only the unchanged physical recipient body port is
 * invoked. Unsupported/late worker output tears down the whole exchange.
 *
 * The first production transport exposes native navigation/look and an
 * observed direct-melee action. It is not a universal arbitrary-mod side-effect
 * sandbox: ranged weapons, projectiles, effects, inventory, building, sounds,
 * capabilities and other mod hooks remain a separate acceptance requirement.
 */
public final class NativeMindWorkerSupervisor {
    private NativeMindWorkerSupervisor() {}

    private static final int WORKER_HEAP_MEGABYTES=768;
    private static final int MAX_FRAME_BYTES=CapturedIntentWireCodec.MAX_WIRE_BYTES;
    private static final Map<UUID,PairSession> BY_BODY=new java.util.HashMap<>();
    // Requester is an index/manager, not necessarily a participant body. One
    // requester may own several independent Mob↔Mob sessions at once.
    private static final Map<UUID,java.util.Set<PairSession>> BY_ACCOUNT=new java.util.HashMap<>();

    private static final class Worker {
        final LivingEntity recipient;
        final UUID mindId;
        final byte[] key;
        final Path root,output,inbox;
        final NativeMindSessionChannel channel;
        final Process process;

        Worker(LivingEntity recipient,UUID mindId,byte[] key,Path root,
                NativeMindSessionChannel channel,Process process) {
            this.recipient=recipient;
            this.mindId=mindId;
            this.key=key;
            this.root=root;
            this.output=root.resolve("ff-worker-frame.bin");
            this.inbox=root.resolve("ff-worker-senses-inbox.bin");
            this.channel=channel;
            this.process=process;
        }
    }

    private static final class MobState {
        final Mob body;
        final boolean originalNoAi;
        MobState(Mob body) {
            this.body=body;
            this.originalNoAi=body.isNoAi();
        }
    }

    private static final class PairSession {
        final ServerPlayer requester;
        final ServerLevel level;
        final CapturedIntentBridge bridge;
        final boolean playerMob;
        final ServerPlayer playerAccount;
        final Mob controlledMob;
        final MobState firstMob,secondMob;
        final List<Worker> workers;
        final UUID[] bodyIds;
        boolean stopping;

        PairSession(ServerPlayer requester,ServerLevel level,CapturedIntentBridge bridge,
                boolean playerMob,ServerPlayer playerAccount,Mob controlledMob,
                MobState firstMob,MobState secondMob,List<Worker> workers,UUID[] bodyIds) {
            this.requester=requester;
            this.level=level;
            this.bridge=bridge;
            this.playerMob=playerMob;
            this.playerAccount=playerAccount;
            this.controlledMob=controlledMob;
            this.firstMob=firstMob;
            this.secondMob=secondMob;
            this.workers=List.copyOf(workers);
            this.bodyIds=bodyIds.clone();
        }
    }

    private static boolean supportedRuntime() {
        return !Boolean.getBoolean("freakyfriday.aiWorker")
            && !Boolean.getBoolean("freakyfriday.thirdPartyFixture")
            && System.getProperty("neoforge.enabledGameTestNamespaces")==null;
    }

    private static Path workerBase() {
        return Path.of(System.getProperty("user.dir","."))
            .toAbsolutePath().normalize().resolve("freakyfriday-workers").normalize();
    }

    private static String entityId(LivingEntity entity) {
        return BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString();
    }

    private static Path javaExecutable() {
        String exe=System.getProperty("os.name","").toLowerCase().contains("win")
            ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"),"bin",exe);
    }

    private static NativeMindSenseFrame.EntitySense selectStimulus(
            NativeMindSenseFrame senses) {
        return senses.entities().stream().filter(e->e.alive()&&e.visible())
            .min(Comparator.comparingDouble(e->e.dx()*e.dx()+e.dy()*e.dy()+e.dz()*e.dz()))
            .orElseGet(()->senses.entities().stream().filter(NativeMindSenseFrame.EntitySense::alive)
                .min(Comparator.comparingDouble(e->e.dx()*e.dx()+e.dy()*e.dy()+e.dz()*e.dz()))
                .orElse(null));
    }

    private static Worker launchWorker(LivingEntity recipient,Mob donor,UUID mindId,
            long hostTick) throws Exception {
        if(!supportedRuntime())
            throw new IllegalStateException("Live native worker launch is disabled in GameTest, worker or synthetic mode");
        NativeMindSenseFrame senses=MinecraftMindSenseCapture.capture(recipient,mindId,0);
        NativeMindSenseFrame.EntitySense stimulus=selectStimulus(senses);
        if(stimulus==null)
            throw new IllegalStateException("No living target exists within the recipient's 32-block sensory radius. "
                +"Choose nearby bodies and retry.");
        byte[] key=CapturedIntentWireCodec.generateKey();
        Path base=workerBase();
        Files.createDirectories(base);
        Path root=base.resolve("session-"+UUID.randomUUID()).normalize();
        if(!root.startsWith(base)||root.equals(base))
            throw new IllegalStateException("Unsafe worker data directory");
        Files.createDirectories(root);
        try {
            UUID sessionId=UUID.randomUUID();
            Properties props=new Properties();
            props.setProperty("session",sessionId.toString());
            props.setProperty("recipientBody",recipient.getUUID().toString());
            props.setProperty("sourceMind",mindId.toString());
            props.setProperty("recipientTick","0");
            props.setProperty("donor",entityId(donor));
            props.setProperty("recipient",entityId(recipient));
            props.setProperty("stimulus",stimulus.entityType());
            props.setProperty("stimulusId",stimulus.id().toString());
            props.setProperty("requireAttack","false");
            props.setProperty("sensesFile","ff-worker-senses.bin");
            props.setProperty("sensesInboxFile","ff-worker-senses-inbox.bin");
            try(OutputStream out=Files.newOutputStream(root.resolve("ff-worker-request.properties"))) {
                props.store(out,"Freaky Friday isolated native mind");
            }
            Files.writeString(root.resolve("eula.txt"),"eula=true\n");
            Files.writeString(root.resolve("server.properties"),
                "online-mode=false\nserver-port=0\nlevel-name=ff-native-mind-worker-world\n"
                +"difficulty=normal\ngamemode=survival\n"
                +"level-seed=0\nlevel-type=minecraft:flat\ngenerate-structures=false\n"
                +"simulation-distance=2\nview-distance=2\nmax-players=1\n"
                +"spawn-protection=0\n"+"pause-when-empty-seconds=0\n");
            NativeMindSessionChannel channel=new NativeMindSessionChannel(
                sessionId,recipient.getUUID(),mindId,key,0,
                NativeMindSessionChannel.DEFAULT_REPLY_DEADLINE_TICKS);
            var initial=channel.begin(senses,hostTick);
            atomicWrite(root.resolve("ff-worker-senses.bin"),initial.sensoryWire());
            List<String> command=NativeMindWorkerLaunchCommand.build(
                javaExecutable().toString(),
                NativeMindWorkerLaunchCommand.currentProcessArguments(),
                root,WORKER_HEAP_MEGABYTES);
            ProcessBuilder builder=new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true).redirectOutput(root.resolve("worker.log").toFile());
            builder.environment().put("FF_WORKER_KEY_HEX",HexFormat.of().formatHex(key));
            Process process=builder.start();
            try { process.getOutputStream().close(); } catch(IOException ignored) {}
            return new Worker(recipient,mindId,key,root,channel,process);
        } catch(Exception failure) {
            java.util.Arrays.fill(key,(byte)0);
            deleteTree(root);
            throw failure;
        }
    }

    private static void atomicWrite(Path destination,byte[] bytes) throws IOException {
        Path tmp=destination.resolveSibling(destination.getFileName()+".tmp");
        Files.write(tmp,bytes);
        try {
            Files.move(tmp,destination,StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE);
        } catch(java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(tmp,destination,StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteTree(Path root) {
        try {
            if(!Files.exists(root,LinkOption.NOFOLLOW_LINKS)||Files.isSymbolicLink(root)) return;
            try(var walk=Files.walk(root)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path->{
                    try { Files.deleteIfExists(path); } catch(IOException ignored) {}
                });
            }
        } catch(IOException ignored) {}
    }

    private static void archiveAndDeleteWorker(Worker worker) {
        // Preserve the isolated worker's complete shutdown/failure log before
        // deleting its disposable world. In particular, do not copy while the
        // child JVM may still be appending its final exception or shutdown data.
        if(Boolean.getBoolean("freakyfriday.supervisorSmoke")) {
            try {
                Path source=worker.root.resolve("worker.log");
                if(Files.isRegularFile(source,LinkOption.NOFOLLOW_LINKS)
                        && !Files.isSymbolicLink(source)) {
                    Path archive=worker.root.getParent().resolve(
                        "worker-debug-"+worker.mindId+".log");
                    Files.copy(source,archive,StandardCopyOption.REPLACE_EXISTING);
                    System.out.println("FF-SUPERVISOR WORKER_LOG_ARCHIVED path="+archive);
                }
            } catch(IOException diagnosticFailure) {
                System.err.println("FF-SUPERVISOR WORKER_LOG_ARCHIVE_FAILURE "
                    +diagnosticFailure.getMessage());
            }
        }
        deleteTree(worker.root);
    }

    private static void stopWorker(Worker worker) {
        worker.channel.close();
        java.util.Arrays.fill(worker.key,(byte)0);
        try {
            if(worker.process.isAlive()) {
                // Process.destroy() initiates an orderly server shutdown; the
                // old immediate isAlive()/destroyForcibly() pair usually killed
                // the JVM before its shutdown hooks or mirror cleanup could run.
                // Keep the session directory until the child has actually exited.
                worker.process.onExit().thenRun(() -> archiveAndDeleteWorker(worker));
                worker.process.destroy();
                CompletableFuture.delayedExecutor(3,TimeUnit.SECONDS).execute(() -> {
                    if(worker.process.isAlive()) {
                        System.err.println("FF-SUPERVISOR WORKER_GRACE_TIMEOUT mind="
                            +worker.mindId+"; forcing child shutdown");
                        worker.process.destroyForcibly();
                    }
                });
                return;
            }
        } catch(RuntimeException shutdownFailure) {
            System.err.println("FF-SUPERVISOR WORKER_SHUTDOWN_FAILURE mind="
                +worker.mindId+" reason="+shutdownFailure.getMessage());
            try { worker.process.destroyForcibly(); }
            catch(RuntimeException ignored) {}
        }
        archiveAndDeleteWorker(worker);
    }

    public static synchronized String startPlayerMob(ServerPlayer player,Mob mob) {
        if(player==null||mob==null||!player.isAlive()||!mob.isAlive())
            return "Both player and Mob bodies must be alive.";
        if(!(player.level() instanceof ServerLevel level)||mob.level()!=level)
            return "Both selected bodies must be in the same loaded dimension.";
        if(player.distanceToSqr(mob)>32.0*32.0)
            return "Keep the player and Mob within 32 blocks so its surroundings can be mirrored.";
        if(!supportedRuntime())
            return "Live native workers are unavailable in this test/worker environment.";
        if(PlayerMobPossession.hasSession(player.getUUID())
                ||PlayerPlayerExchange.active(player.getUUID())
                ||BY_BODY.containsKey(player.getUUID())
                ||BY_BODY.containsKey(mob.getUUID()))
            return "One of these participants already belongs to an active exchange.";

        CapturedIntentBridge bridge=new CapturedIntentBridge();
        UUID playerMind=player.getUUID(),mobMind=mob.getUUID();
        bridge.register(new MinecraftPlayerBodyPort(player),
            new MindLedger.Mind(playerMind,MindLedger.Kind.PLAYER,"minecraft:player"));
        bridge.register(new MinecraftBodyPort(mob),
            new MindLedger.Mind(mobMind,MindLedger.Kind.NPC,entityId(mob)));
        try { bridge.exchange(player.getUUID(),mob.getUUID()); }
        catch(Exception rejected) { return "Mind ownership preflight failed: "+rejected.getMessage(); }

        Worker worker=null;
        try {
            worker=launchWorker(player,mob,mobMind,level.getGameTime());
            String issue=PlayerMobPossession.beginReciprocal(player,mob);
            if(issue!=null) {
                stopWorker(worker);
                return issue;
            }
            PairSession pair=new PairSession(player,level,bridge,true,player,mob,
                null,null,List.of(worker),new UUID[]{player.getUUID(),mob.getUUID()});
            register(pair);
            return null;
        } catch(Exception failure) {
            if(worker!=null) stopWorker(worker);
            PlayerMobPossession.end(player);
            return "Could not start reciprocal player/Mob workers: "
                +failure.getClass().getSimpleName()+": "+failure.getMessage();
        }
    }

    public static synchronized String startMobPair(ServerPlayer requester,Mob first,Mob second) {
        if(requester==null||first==null||second==null||first==second
                ||!first.isAlive()||!second.isAlive())
            return "Two distinct living Mob bodies are required.";
        if(!(requester.level() instanceof ServerLevel level)
                ||first.level()!=level||second.level()!=level)
            return "Requester and both Mob bodies must be in the same loaded dimension.";
        if(first.distanceToSqr(second)>32.0*32.0)
            return "Keep the two Mob bodies within 32 blocks for reciprocal native sensing.";
        if(!supportedRuntime())
            return "Live native workers are unavailable in this test/worker environment.";
        if(BY_BODY.containsKey(first.getUUID())||BY_BODY.containsKey(second.getUUID())
                ||PlayerMobPossession.hasSession(requester.getUUID())
                ||PlayerPlayerExchange.active(requester.getUUID()))
            return "One of these participants already belongs to an active exchange.";

        CapturedIntentBridge bridge=new CapturedIntentBridge();
        UUID firstMind=first.getUUID(),secondMind=second.getUUID();
        bridge.register(new MinecraftBodyPort(first),
            new MindLedger.Mind(firstMind,MindLedger.Kind.NPC,entityId(first)));
        bridge.register(new MinecraftBodyPort(second),
            new MindLedger.Mind(secondMind,MindLedger.Kind.NPC,entityId(second)));
        try { bridge.exchange(first.getUUID(),second.getUUID()); }
        catch(Exception rejected) { return "Mind ownership preflight failed: "+rejected.getMessage(); }

        List<Worker> workers=new ArrayList<>(2);
        MobState firstState=new MobState(first),secondState=new MobState(second);
        try {
            workers.add(launchWorker(second,first,firstMind,level.getGameTime()));
            workers.add(launchWorker(first,second,secondMind,level.getGameTime()));
            first.setNoAi(true);
            second.setNoAi(true);
            PairSession pair=new PairSession(requester,level,bridge,false,null,null,
                firstState,secondState,workers,new UUID[]{first.getUUID(),second.getUUID()});
            register(pair);
            return null;
        } catch(Exception failure) {
            workers.forEach(NativeMindWorkerSupervisor::stopWorker);
            first.setNoAi(firstState.originalNoAi);
            second.setNoAi(secondState.originalNoAi);
            return "Could not start reciprocal Mob workers: "
                +failure.getClass().getSimpleName()+": "+failure.getMessage();
        }
    }

    /**
     * Reserve every physical body atomically. The requester index is not a
     * participant lock: a Mob↔Mob session does not occupy the requester's
     * player body, so that same player may initiate another exchange with a
     * different pair (including a Player↔Mob exchange using their own body).
     *
     * Validate the complete body reservation before mutating either index.
     * All callers hold this class monitor, so no other session can race between
     * preflight and commit.
     */
    private static void register(PairSession pair) {
        java.util.HashSet<UUID> uniqueBodies = new java.util.HashSet<>();
        for (UUID body : pair.bodyIds) {
            if (body == null || !uniqueBodies.add(body))
                throw new IllegalStateException("A mind exchange cannot reserve a null or duplicate body UUID");
            if (BY_BODY.containsKey(body))
                throw new IllegalStateException("A physical body was claimed during exchange startup: " + body);
        }
        // Commit body ownership only after every physical participant passes.
        for (UUID body : pair.bodyIds) BY_BODY.put(body, pair);
        BY_ACCOUNT.computeIfAbsent(pair.requester.getUUID(), ignored -> new java.util.HashSet<>())
            .add(pair);
    }

    @SubscribeEvent
    public static synchronized void tick(ServerTickEvent.Post event) {
        for(PairSession pair:List.copyOf(new java.util.HashSet<>(BY_BODY.values()))) {
            try {
                if(pair.stopping) continue;
                if(pair.requester.isRemoved()||!pair.requester.isAlive()
                        ||pair.requester.level()!=pair.level)
                    throw new IllegalStateException("Requesting player left or died");
                if(pair.playerMob) {
                    if(pair.playerAccount.isRemoved()||!pair.playerAccount.isAlive()
                            ||!PlayerMobPossession.hasSession(pair.playerAccount.getUUID()))
                        throw new IllegalStateException("Reciprocal player/Mob control ended");
                    if(!pair.controlledMob.isAlive()||pair.controlledMob.isRemoved()
                            ||pair.controlledMob.level()!=pair.level)
                        throw new IllegalStateException("Occupied Mob body died or unloaded");
                } else {
                    if(!pair.firstMob.body.isAlive()||pair.firstMob.body.isRemoved()
                            ||!pair.secondMob.body.isAlive()||pair.secondMob.body.isRemoved())
                        throw new IllegalStateException("One of the original Mob bodies died or unloaded");
                }
                long hostTick=pair.level.getGameTime();
                for(Worker worker:pair.workers) {
                    if(!worker.process.isAlive())
                        throw new IllegalStateException("Native worker process exited; see "+worker.root.resolve("worker.log"));
                    Path output=worker.output;
                    if(Files.exists(output,LinkOption.NOFOLLOW_LINKS)) {
                        if(Files.isSymbolicLink(output)
                                ||!Files.isRegularFile(output,LinkOption.NOFOLLOW_LINKS))
                            throw new IllegalStateException("Worker output path is not a regular file");
                        Path claimed=output.resolveSibling("ff-worker-frame.claimed");
                        try { Files.move(output,claimed,StandardCopyOption.ATOMIC_MOVE); }
                        catch(java.nio.file.AtomicMoveNotSupportedException unsupported) {
                            Files.move(output,claimed);
                        }
                        try {
                            long bytes=Files.size(claimed);
                            if(bytes<64||bytes>MAX_FRAME_BYTES)
                                throw new IllegalArgumentException("Worker action frame is outside size bounds");
                            CapturedIntentBridge.Frame frame=worker.channel.complete(
                                Files.readAllBytes(claimed),hostTick);
                            double beforeX=worker.recipient.getX(),beforeZ=worker.recipient.getZ();
                            CapturedIntentBridge.Receipt receipt=pair.bridge.deliver(frame);
                            if(Boolean.getBoolean("freakyfriday.supervisorSmoke")) {
                                System.out.println("FF-SUPERVISOR FRAME_DISPATCH"
                                    +" mind="+worker.mindId
                                    +" recipient="+worker.recipient.getType()
                                    +" sensoryTick="+(worker.channel.nextTick()-1)
                                    +" hostTick="+hostTick
                                    +" actionCount="+frame.actions().size()
                                    +" actions="+frame.actions()
                                    +" outcomes="+receipt.outcomes()
                                    +" before="+beforeX+","+beforeZ
                                    +" after="+worker.recipient.getX()+","+worker.recipient.getZ());
                            }
                            if(receipt.status()!=CapturedIntentBridge.Status.DISPATCHED)
                                throw new IllegalStateException("Recipient rejected worker frame: "
                                    +receipt.status()+" "+receipt.diagnostic());
                            NativeMindSenseFrame nextSense=MinecraftMindSenseCapture.capture(
                                worker.recipient,worker.mindId,worker.channel.nextTick());
                            NativeMindSessionChannel.Request next=worker.channel.begin(nextSense,hostTick);
                            atomicWrite(worker.inbox,next.sensoryWire());
                        } finally {
                            Files.deleteIfExists(claimed);
                        }
                    } else if(worker.channel.expire(hostTick)) {
                        throw new IllegalStateException("Native worker missed its reply deadline");
                    }
                }
            } catch(Exception failure) {
                endPair(pair,"Native mind session retired safely: "
                    +failure.getClass().getSimpleName()+": "+failure.getMessage(),true);
            }
        }
    }

    private static void registerPair(PairSession pair) {
        java.util.HashSet<UUID> uniqueBodies=new java.util.HashSet<>();
        for(UUID body:pair.bodyIds) {
            if(body==null||!uniqueBodies.add(body)||BY_BODY.containsKey(body))
                throw new IllegalStateException("Physical body already belongs to an exchange: "+body);
        }
        for(UUID body:pair.bodyIds) BY_BODY.put(body,pair);
        BY_ACCOUNT.computeIfAbsent(pair.requester.getUUID(), ignored -> new java.util.HashSet<>())
            .add(pair);
    }

    private static void unindexRequester(PairSession pair) {
        java.util.Set<PairSession> sessions=BY_ACCOUNT.get(pair.requester.getUUID());
        if(sessions==null) return;
        sessions.remove(pair);
        if(sessions.isEmpty()) BY_ACCOUNT.remove(pair.requester.getUUID());
    }

    private static void endPair(PairSession pair,String reason,boolean notify) {
        if(pair.stopping) return;
        pair.stopping=true;
        for(Worker worker:pair.workers) stopWorker(worker);
        if(pair.playerMob) {
            if(pair.playerAccount!=null) PlayerMobPossession.end(pair.playerAccount);
        } else {
            if(pair.firstMob!=null&&!pair.firstMob.body.isRemoved())
                pair.firstMob.body.setNoAi(pair.firstMob.originalNoAi);
            if(pair.secondMob!=null&&!pair.secondMob.body.isRemoved())
                pair.secondMob.body.setNoAi(pair.secondMob.originalNoAi);
        }
        for(UUID body:pair.bodyIds) BY_BODY.remove(body,pair);
        unindexRequester(pair);
        if(notify&&!pair.requester.isRemoved())
            pair.requester.sendSystemMessage(Component.literal("[Freaky Friday] "+reason));
    }

    public static synchronized boolean cancelByAccount(UUID account) {
        java.util.Set<PairSession> owned=new java.util.HashSet<>(
            BY_ACCOUNT.getOrDefault(account,java.util.Set.of()));
        // Also include any session where this account is the occupied player
        // body, even if another operator originally requested it.
        for(PairSession candidate:new java.util.HashSet<>(BY_BODY.values())) {
            if(candidate.playerAccount!=null
                    &&candidate.playerAccount.getUUID().equals(account))
                owned.add(candidate);
        }
        if(owned.isEmpty()) return false;
        for(PairSession pair:owned)
            endPair(pair,"Native mind exchange cancelled; original body ownership restored.",true);
        return true;
    }

    @SubscribeEvent
    public static synchronized void onServerStopped(ServerStoppedEvent event) {
        clearOnServerStop();
    }

    public static synchronized void clearOnServerStop() {
        for(PairSession pair:List.copyOf(new java.util.HashSet<>(BY_BODY.values())))
            endPair(pair,"Server stopped; native worker sessions were closed.",false);
        BY_BODY.clear();
        BY_ACCOUNT.clear();
    }
}
