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
import net.neoforged.neoforge.common.world.chunk.RegisterTicketControllersEvent;
import net.neoforged.neoforge.common.world.chunk.TicketController;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
 * The bounded observer captures naturally produced native navigation and,
 * when it actually occurs, a direct melee hit at Minecraft's damage event
 * boundary. It does NOT capture all native powers, ranged/projectile attacks,
 * inventory changes, blocks, or modded side effects. Unknown behavior is not
 * replaced with scripted per-mob rules.
 */
public final class NativeAiWorkerServer {
    private NativeAiWorkerServer() {}

    private static final String WORKER_PROP = "freakyfriday.aiWorker";
    private static final Path REQUEST = Path.of("ff-worker-request.properties");
    private static final Path OUTPUT = Path.of("ff-worker-frame.bin");

    // NeoForge's UUID owner ticking ticket explicitly enables full entity
    // ticks in a playerless worker dimension. Vanilla setChunkForced alone
    // left Chicken AI at ~75 ticks after 400 worker-server ticks.
    private static final TicketController ARENA_TICKETS = new TicketController(
        Identifier.fromNamespaceAndPath("freakyfriday","native_worker_arena"));
    private static final UUID ARENA_OWNER = UUID.nameUUIDFromBytes(
        "freakyfriday-native-worker".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    /** Registered on NeoForge's MOD event bus during mod construction. */
    public static void registerChunkTickets(RegisterTicketControllersEvent event) {
        event.register(ARENA_TICKETS);
    }

    /**
     * Test fixtures may live beyond the GameTest structure's entity-ticking
     * chunk. Give them an explicit full-ticking ticket instead of relying on
     * a mock player or accidental chunk placement. Each fixture supplies its
     * own owner UUID so parallel tests cannot release one another's tickets.
     */
    public static void setEntityTickingChunk(ServerLevel level, UUID owner,
            int chunkX, int chunkZ, boolean forced) {
        boolean changed=ARENA_TICKETS.forceChunk(
            level,owner,chunkX,chunkZ,forced,true);
        if(forced && !changed)
            throw new IllegalStateException("Could not install full entity-ticking test ticket"
                +" chunk="+chunkX+","+chunkZ);
    }

    private static State active;
    private static boolean started;

    private record Request(UUID session, UUID recipientBody, UUID sourceMind,
                           UUID stimulusId, long recipientTick, String donor,
                           String stimulus, boolean requireAttack, byte[] secret) {}

    private static final class State {
        final ServerLevel level;
        final Request request;
        final Mob donor;
        final Mob stimulus;
        final BlockPos anchor;
        final double startX, startZ;
        int age, targetTicks, navigationTicks;
        boolean produced;
        boolean spawned;
        boolean genericStimulusApplied;
        NativeMindObservation.NativeNavigation observedMovement;
        UUID attackedTarget;
        String attackedTargetType;

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
        UUID stimulusId=UUID.fromString(config.getProperty("stimulusId"));
        boolean requireAttack=Boolean.parseBoolean(
            config.getProperty("requireAttack","false"));
        long tick=Long.parseLong(config.getProperty("recipientTick"));
        if (tick < 0) throw new IllegalArgumentException("Negative recipient tick");
        return new Request(
            UUID.fromString(config.getProperty("session")),
            UUID.fromString(config.getProperty("recipientBody")),
            UUID.fromString(config.getProperty("sourceMind")),
            stimulusId,tick,donor,stimulus,requireAttack,secret);
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
            // This arena straddles chunk boundaries at x/z zero. Forcing
            // ONLY (0,0) leaves three quarters of its navigation area
            // outside entity-ticking range in a playerless worker. Force
            // every chunk containing arena ground, not only the spawn chunk.
            // This is a generic Minecraft simulation ticket, NOT a Mob patch.
            for (int cx=-1;cx<=0;cx++) for (int cz=-1;cz<=0;cz++) {
                boolean ticketAdded=ARENA_TICKETS.forceChunk(
                    level,ARENA_OWNER,cx,cz,true,true);
                if(!ticketAdded)
                    throw new IllegalStateException(
                        "Full entity-ticking ticket not installed for "+cx+","+cz);
                level.getChunk(cx,cz);
            }
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
            stimulus.setUUID(request.stimulusId());
            donor.setPos(0.5,121,0.5);
            stimulus.setPos(8.5,121,0.5);
            donor.setPersistenceRequired();
            stimulus.setNoAi(true); // Stationary target; donor uses its OWN vanilla AI.
            stimulus.setPersistenceRequired();
            // ServerStartedEvent is still inside the startup lifecycle on
            // 26.2. Defer entity insertion to a normal server tick so the
            // EntityManager and chunk tracker are fully online first.
            active=new State(level,request,donor,stimulus,anchor);
            started=true;
            System.out.println("FF-NATIVE-WORKER READY donor="+request.donor()
                +" stimulus="+request.stimulus()+" requireAttack="+request.requireAttack()
                +" ownWorld=true"
                +" nativeAiEnabled="+!donor.isNoAi()
                +" deferredEntityRegistration=true"
                +" liveWorldAccess=false");
        } catch (Exception failure) {
            System.err.println("FF-NATIVE-WORKER START_FAILURE "
                +failure.getClass().getName()+": "+failure.getMessage());
            throw new IllegalStateException("Native worker must fail closed",failure);
        }
    }

    @SubscribeEvent
    public static void onNativeAttack(LivingIncomingDamageEvent event) {
        State state=active;
        if(!started || state==null || state.produced || state.attackedTarget!=null)
            return;
        if(event.getSource().getEntity()==state.donor) {
            state.attackedTarget=event.getEntity().getUUID();
            state.attackedTargetType=event.getEntity().getType().toString();
            // Do not let the shadow donor damage the worker's target. The
            // physical recipient will perform the mapped attack in its world.
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (!started || active==null || active.produced) return;
        State state=active;
        state.age++;
        Mob donor=state.donor, stimulus=state.stimulus;
        if(!state.spawned) {
            if(state.age<5)return;
            boolean donorAdded=state.level.addFreshEntity(donor);
            boolean stimulusAdded=state.level.addFreshEntity(stimulus);
            if(!donorAdded || !stimulusAdded) {
                state.produced=true;
                System.err.println("FF-NATIVE-WORKER WORLD_REGISTRATION_FAILED"
                    +" addDonor="+donorAdded+" addStimulus="+stimulusAdded
                    +" donorUUID="+donor.getUUID()
                    +" stimulusUUID="+stimulus.getUUID());
                return;
            }
            state.spawned=true;
            System.out.println("FF-NATIVE-WORKER ENTITIES_SPAWNED PASS"
                +" donor="+donor.getUUID()+" stimulus="+stimulus.getUUID());
            return;
        }
        if (state.age < 8) return;
        if (state.level.getEntity(donor.getUUID())!=donor
                || state.level.getEntity(stimulus.getUUID())!=stimulus) {
            if (state.age>=100) {
                System.err.println("FF-NATIVE-WORKER WORLD_REGISTRATION_FAILED"
                    +" age="+state.age
                    +" donorFound="+(state.level.getEntity(donor.getUUID())==donor)
                    +" stimulusFound="+(state.level.getEntity(stimulus.getUUID())==stimulus)
                    +" donorRemoved="+donor.isRemoved()
                    +" donorRemovalReason="+donor.getRemovalReason()
                    +" stimulusRemoved="+stimulus.isRemoved()
                    +" stimulusRemovalReason="+stimulus.getRemovalReason()
                    +" donorUUID="+donor.getUUID()
                    +" stimulusUUID="+stimulus.getUUID()
                    +" donorPos="+donor.position()
                    +" stimulusPos="+stimulus.position());
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

        // For a native mind that has not yet received any meaningful reason
        // to act, apply the SAME ordinary environmental damage stimulus to
        // ANY registered Mob. This does not install a Goal, fake panic, or
        // choose behavior by species; the donor's real AI either responds
        // or the test must fail. Damage occurs ONLY in the disposable world.
        // Do not apply to fragile mobs whose current health would be lethal.
        if(state.age>=80 && state.navigationTicks==0
                && !state.genericStimulusApplied && donor.getHealth()>1.0f){
            state.genericStimulusApplied=true;
            float amount=Math.min(1.0f,donor.getHealth()*0.20f);
            // An attacker-associated hit triggers ordinary native defensive
            // AI (for example flee/panic) through Minecraft's own target and
            // hurt-memory path. It does not synthesize donor-specific actions.
            boolean applied=donor.hurtServer(state.level,
                donor.damageSources().mobAttack(stimulus),amount);
            System.out.println("FF-NATIVE-WORKER GENERIC_ENVIRONMENT_STIMULUS"
                +" donor="+state.request.donor()
                +" damageApplied="+applied+" damage="+amount
                +" originalRecipientUntouched=true");
        }

        // Both hostile pursuit and passive random wandering are observed
        // through the very same native navigation seam. No entity-type
        // conditional or species recipe decides the action.
        var movement=NativeMindObservation.observe(
            state.startX,donor.getY(),state.startZ,
            donor.getX(),donor.getY(),donor.getZ(),
            state.navigationTicks>=2);
        if(movement!=null)state.observedMovement=movement;

        // For hostile AI acceptance, wait until the REAL donor reaches the
        // native damage pipeline. The incoming hit is canceled only in this
        // disposable worker so the mapped recipient can perform it once.
        if(state.observedMovement!=null
                && (!state.request.requireAttack() || state.attackedTarget!=null)) {
            try {
                var frame=NativeMindObservation.frame(
                    state.request.recipientBody(),
                    state.request.sourceMind(),
                    state.request.recipientTick(),state.observedMovement,
                    state.attackedTarget);
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
                    +" observedNativeTravel="+state.observedMovement.observedDistance()
                    +" nativeOffsetX="+state.observedMovement.dx()
                    +" nativeOffsetZ="+state.observedMovement.dz()
                    +" originalWorldIsWorker=true");
                if(state.attackedTarget!=null)
                    System.out.println("FF-NATIVE-WORKER NATIVE_ATTACK_OBSERVED"
                        +" target="+state.attackedTarget
                        +" targetType="+state.attackedTargetType
                        +" damageCancelledInWorker=true");
                System.out.println("FF-NATIVE-WORKER FRAME_WRITTEN PASS"
                    +" signedBytes="+signed.length
                    +" donorNativeAiActuallyTicked=true"
                    +" attackCaptured="+(state.attackedTarget!=null)
                    +" actionCapture=native_navigation_plus_observed_attack");
            } catch (Exception failure) {
                state.produced=true;
                System.err.println("FF-NATIVE-WORKER FRAME_FAILURE "
                    +failure.getClass().getName()+": "+failure.getMessage());
            }
        } else if (state.age>=400) {
            state.produced=true;
            System.err.println(state.observedMovement==null
                ? "FF-NATIVE-WORKER NO_NATIVE_DECISION"
                : "FF-NATIVE-WORKER NO_NATIVE_ATTACK");
            System.err.println("FF-NATIVE-WORKER NATIVE_CAPTURE_INCOMPLETE"
                +" targetTicks="+state.targetTicks
                +" navigationTicks="+state.navigationTicks
                +" attackCaptured="+(state.attackedTarget!=null)
                +" requireAttack="+state.request.requireAttack()
                +" mobTickCount="+donor.tickCount
                +" noAi="+donor.isNoAi()
                +" onGround="+donor.onGround()
                +" nativeGoals="+donor.goalSelector.getAvailableGoals().size()
                +" displacement="+Math.hypot(donor.getX()-state.startX,
                    donor.getZ()-state.startZ)
                +" hasPath="+(donor.getNavigation().getPath()!=null)
                +" workerChunkLoaded="+state.level.hasChunkAt(donor.blockPosition())
                +" chunkX="+(donor.blockPosition().getX()>>4)
                +" chunkZ="+(donor.blockPosition().getZ()>>4));
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppedEvent event) {
        if (active!=null) {
            for (int cx=-1;cx<=0;cx++) for (int cz=-1;cz<=0;cz++)
                ARENA_TICKETS.forceChunk(active.level,ARENA_OWNER,cx,cz,false,true);
        }
        active=null;
        started=false;
    }
}
