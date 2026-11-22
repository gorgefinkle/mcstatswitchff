package dev.freakyfriday.mind;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * An ACTUAL NeoForge server-side native donor AI producer. It runs ONLY in
 * Gradle's distinct aiWorker runServer process and gameDirectory.
 *
 * This process contains REAL Minecraft AI (and optionally other installed
 * mods), but has no references or handles to the live server world. All
 * changes happen in the disposable worker world. An OS/container isolation
 * layer is still needed to restrict arbitrary modded Java filesystem/network
 * effects: a separate JVM alone is not a comprehensive security sandbox.
 *
 * The first bounded observation captures naturally produced native navigation
 * movement from either hostile or passive donors. It does NOT capture all native powers, inventory changes, attacks,
 * projectiles, or modded side effects. Unknown/unsupported behavior fails
 * closed instead of being replaced with scripted per-mob rules.
 */
public final class NativeAiWorkerServer {
    private NativeAiWorkerServer() {}

    private static final String WORKER_PROP = "freakyfriday.aiWorker";
    private static final Path REQUEST = Path.of("ff-worker-request.properties");
    private static final Path OUTPUT = Path.of("ff-worker-frame.bin");

    private static State active;
    private static boolean started;

    private record Request(UUID session, UUID recipientBody, UUID sourceMind,
                           long recipientTick, String donor, String stimulus,
                           byte[] secret) {}

    private static final class State {
        final ServerLevel level;
        final Request request;
        final Mob donor;
        final Mob stimulus;
        final BlockPos anchor;
        final double startX, startZ;
        int age, targetTicks, navigationTicks;
        boolean produced;

        State(ServerLevel level, Request request, Mob donor, Mob stimulus,
              BlockPos anchor) {
            this.level=level;
            this.request=request;
            this.donor=donor;
            this.stimulus=stimulus;
            this.anchor=anchor;
            this.startX=donor.getX();
            this.startZ=donor.getZ();
        }
    }

    private static void checkWorkerPath() {
        if (!Path.of("").toAbsolutePath().normalize().getFileName()
                .toString().equals("run-ai-worker"))
            throw new IllegalStateException(
                "Refusing worker mode outside the dedicated run-ai-worker directory");
        if (!Files.exists(REQUEST))
            throw new IllegalStateException("Missing disposable worker request fixture");
    }

    private static Request readRequest() throws IOException {
        Properties config=new Properties();
        try (InputStream in=Files.newInputStream(REQUEST)) {
            config.load(in);
        }
        String hex=Objects.requireNonNull(System.getenv("FF_WORKER_KEY_HEX"),
            "FF_WORKER_KEY_HEX is required in worker mode");
        byte[] secret=HexFormat.of().parseHex(hex);
        if (secret.length != CapturedIntentWireCodec.KEY_BYTES)
            throw new IllegalArgumentException("Worker secret must be 256-bit");
        String donor=config.getProperty("donor");
        String stimulus=config.getProperty("stimulus");
        if (donor==null || stimulus==null)
            throw new IllegalArgumentException("Missing donor/stimulus entity types");
        long tick=Long.parseLong(config.getProperty("recipientTick"));
        if (tick < 0) throw new IllegalArgumentException("Negative recipient tick");
        return new Request(
            UUID.fromString(config.getProperty("session")),
            UUID.fromString(config.getProperty("recipientBody")),
            UUID.fromString(config.getProperty("sourceMind")),
            tick,donor,stimulus,secret);
    }

    private static Mob construct(ServerLevel level,String entityId) {
        Identifier id=Identifier.tryParse(entityId);
        if (id==null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            throw new IllegalArgumentException("Unknown worker entity type: "+entityId);
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity=type.create(level, EntitySpawnReason.COMMAND);
        if (!(entity instanceof Mob mob))
            throw new IllegalArgumentException("Worker requires registered Mob type: "+entityId);
        return mob;
    }

    @SubscribeEvent
    public static void onServerStart(ServerStartedEvent event) {
        if (!Boolean.getBoolean(WORKER_PROP)) return;
        if (active != null) throw new IllegalStateException("Only one native worker session");
        try {
            checkWorkerPath();
            Request request=readRequest();
            ServerLevel level=event.getServer().overworld();
            if (level==null) throw new IllegalStateException("No worker overworld");
            // A disposable fully loaded, clear-sight stone test arena.
            // No original donor or recipient from the LIVE world exists here.
            BlockPos anchor=new BlockPos(0, 120, 0);
            // A playerless worker has no simulation-distance tickets.
            // Force the single arena chunk so the REAL native AI ticks.
            level.setChunkForced(0,0,true);
            for(int x=-12;x<=12;x++) for(int z=-12;z<=12;z++) {
                level.setBlockAndUpdate(new BlockPos(x,120,z),Blocks.STONE.defaultBlockState());
                // Roof guards daylight-sensitive native mobs without touching
                // the removed 26.x ServerLevel.setDayTime API.
                level.setBlockAndUpdate(new BlockPos(x,126,z),Blocks.STONE.defaultBlockState());
                for(int y=121;y<=125;y++)
                    level.setBlockAndUpdate(new BlockPos(x,y,z),Blocks.AIR.defaultBlockState());
            }
            Mob donor=construct(level,request.donor());
            Mob stimulus=construct(level,request.stimulus());
            donor.setPos(0.5,121,0.5);
            stimulus.setPos(8.5,121,0.5);
            stimulus.setNoAi(true); // Stationary target; donor uses its OWN vanilla AI.
            if (!level.addFreshEntity(donor) || !level.addFreshEntity(stimulus))
                throw new IllegalStateException("Worker failed to spawn native NPCs");
            // addFreshEntity schedules registration during server startup.
            // Entity lookup can legitimately return null until subsequent
            // full server ticks, so verify after startup, not in this event.
            active=new State(level,request,donor,stimulus,anchor);
            started=true;
            System.out.println("FF-NATIVE-WORKER READY donor="+request.donor()
                +" stimulus="+request.stimulus()+" ownWorld=true"
                +" nativeAiEnabled="+!donor.isNoAi()
                +" liveWorldAccess=false");
        } catch (Exception failure) {
            System.err.println("FF-NATIVE-WORKER START_FAILURE "
                +failure.getClass().getName()+": "+failure.getMessage());
            throw new IllegalStateException("Native worker must fail closed",failure);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!started || active==null || active.produced) return;
        State state=active;
        state.age++;
        Mob donor=state.donor, stimulus=state.stimulus;
        if (state.age < 8) return;
        if (state.level.getEntity(donor.getUUID())!=donor
                || state.level.getEntity(stimulus.getUUID())!=stimulus) {
            if (state.age>=30) {
                System.err.println("FF-NATIVE-WORKER WORLD_REGISTRATION_FAILED"
                    +" age="+state.age
                    +" donorFound="+(state.level.getEntity(donor.getUUID())==donor)
                    +" stimulusFound="+(state.level.getEntity(stimulus.getUUID())==stimulus));
                state.produced=true;
            }
            return;
        }
        if (!donor.isAlive() || !stimulus.isAlive()) {
            System.err.println("FF-NATIVE-WORKER MOB_DIED before AI capture");
            state.produced=true;
            return;
        }
        // Both conditions are supplied by the REAL Minecraft AI scheduler.
        // We never inject a Goal, call its tick manually, or force navigation.
        if (donor.getTarget()==stimulus) state.targetTicks++;
        if (donor.getNavigation().isInProgress()) state.navigationTicks++;

        // Both hostile pursuit and passive random wandering are observed
        // through the very same native navigation seam. No entity-type
        // conditional or species recipe decides the action.
        var movement=NativeMindObservation.observe(
            state.startX,donor.getY(),state.startZ,
            donor.getX(),donor.getY(),donor.getZ(),
            state.navigationTicks>=2);
        if (movement!=null) {
            try {
                var frame=NativeMindObservation.frame(
                    state.request.recipientBody(),
                    state.request.sourceMind(),
                    state.request.recipientTick(),movement);
                byte[] signed=CapturedIntentWireCodec.encode(
                    new CapturedIntentWireCodec.Envelope(
                        state.request.session(),frame),state.request.secret());
                Path tmp=Path.of("ff-worker-frame.bin.tmp");
                Files.write(tmp,signed);
                try {
                    Files.move(tmp,OUTPUT,StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
                } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                    Files.move(tmp,OUTPUT,StandardCopyOption.REPLACE_EXISTING);
                }
                state.produced=true;
                System.out.println("FF-NATIVE-WORKER NATIVE_NAVIGATION_OBSERVED"
                    +" donor="+state.request.donor()
                    +" nativeTargetTicks="+state.targetTicks
                    +" nativeNavigationTicks="+state.navigationTicks
                    +" observedNativeTravel="+movement.observedDistance()
                    +" nativeOffsetX="+movement.dx()
                    +" nativeOffsetZ="+movement.dz()
                    +" originalWorldIsWorker=true");
                System.out.println("FF-NATIVE-WORKER FRAME_WRITTEN PASS"
                    +" signedBytes="+signed.length
                    +" donorNativeAiActuallyTicked=true"
                    +" actionCapture=native_relative_navigation_only");
            } catch (Exception failure) {
                state.produced=true;
                System.err.println("FF-NATIVE-WORKER FRAME_FAILURE "
                    +failure.getClass().getName()+": "+failure.getMessage());
            }
        } else if (state.age>=400) {
            state.produced=true;
            System.err.println("FF-NATIVE-WORKER NO_NATIVE_DECISION"
                +" targetTicks="+state.targetTicks
                +" navigationTicks="+state.navigationTicks);
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        active=null;
        started=false;
    }
}
