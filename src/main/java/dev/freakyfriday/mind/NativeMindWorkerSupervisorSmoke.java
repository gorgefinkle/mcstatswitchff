package dev.freakyfriday.mind;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Real parent-server integration test for the production supervisor. Unlike
 * the two-JVM fixture writer, this launches child NeoForge servers from a
 * normal runServer process and captures the live recipient world every tick.
 */
public final class NativeMindWorkerSupervisorSmoke {
    private NativeMindWorkerSupervisorSmoke() {}

    private static final int MAX_PHASE_TICKS=1000;
    private static MinecraftServer server;
    private static ServerLevel level;
    private static FakePlayer account;
    private static Mob occupiedMob;
    private static Mob firstMob,secondMob;
    private static Mob target;
    private static int phase,ticks;
    private static long sequence;
    private static double accountStartX,accountStartZ,mobStartX,mobStartZ;
    private static double firstStartX,firstStartZ,secondStartX,secondStartZ;
    private static boolean firstOriginalNoAi,secondOriginalNoAi;
    private static UUID[] originalIds;

    private static boolean enabled() {
        return Boolean.getBoolean("freakyfriday.supervisorSmoke");
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if(!enabled()) return;
        server=event.getServer();
        level=server.overworld();
        phase=0;ticks=0;sequence=0;
        try {
            // All fixtures fit inside a single pair of loaded chunks.
            // The server's generated spawn terrain may occupy y=64 at these
            // coordinates; a floor-only fixture previously spawned the
            // Villager inside solid terrain ("suffocated in a wall") and
            // immobilized both reciprocal bodies. Clear a roomy, bounded
            // arena before adding any living entity.
            level.getChunk(0,0);
            level.getChunk(1,0);
            level.getChunk(0,1);
            level.getChunk(1,1);
            for(int x=0;x<=31;x++) for(int z=0;z<=15;z++) {
                level.setBlockAndUpdate(new BlockPos(x,63,z),Blocks.STONE.defaultBlockState());
                for(int y=64;y<=70;y++) {
                    BlockPos clear=new BlockPos(x,y,z);
                    if(!level.getBlockState(clear).isAir())
                        level.setBlockAndUpdate(clear,Blocks.AIR.defaultBlockState());
                }
            }
            UUID id=UUID.randomUUID();
            account=new FakePlayer(level,new GameProfile(id,"FFSupervisor"));
            level.addNewPlayer(account);
            account.setPos(2.5,64,2.5);
            account.setNoGravity(false);

            occupiedMob=createMob(EntityTypes.ZOMBIE,8.5,64,2.5);
            target=createVillager(6.5,64,2.5);
            accountStartX=account.getX();accountStartZ=account.getZ();
            mobStartX=occupiedMob.getX();mobStartZ=occupiedMob.getZ();
            originalIds=new UUID[]{account.getUUID(),occupiedMob.getUUID(),target.getUUID()};
            String error=NativeMindWorkerSupervisor.startPlayerMob(account,occupiedMob);
            if(error!=null) throw new IllegalStateException("Player↔Mob supervisor start rejected: "+error);
            PlayerMobPossession.input(account,
                new PlayerMobPossession.Input(1,0,false,0,0,++sequence));
            phase=1;
            System.out.println("FF-SUPERVISOR PLAYER_MOB_STARTED PASS");
        } catch(Exception failure) {
            fail("PLAYER_MOB",failure);
        }
    }

    private static Mob createMob(
            net.minecraft.world.entity.EntityType<?> type,double x,double y,double z) {
        var entity=type.create(level,EntitySpawnReason.COMMAND);
        if(!(entity instanceof Mob mob))
            throw new IllegalStateException("Smoke fixture type is not a Mob: "+type);
        mob.setPos(x,y,z);
        mob.setPersistenceRequired();
        if(!level.addFreshEntity(mob))
            throw new IllegalStateException("Could not add smoke Mob: "+type);
        return mob;
    }

    private static Mob createVillager(double x,double y,double z) {
        var entity=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
        if(!(entity instanceof Mob villager))
            throw new IllegalStateException("Could not construct smoke Villager Mob");
        villager.setPos(x,y,z);
        villager.setNoAi(true);
        villager.setNoGravity(true);
        villager.setPersistenceRequired();
        if(!level.addFreshEntity(villager))
            throw new IllegalStateException("Could not add smoke Villager");
        return villager;
    }

    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        if(!enabled() || phase==3 || account==null || level==null) return;
        ticks++;
        try {
            if(phase==1) {
                if(!account.isRemoved() && PlayerMobPossession.hasSession(account.getUUID())) {
                    PlayerMobPossession.input(account,
                        new PlayerMobPossession.Input(1,0,false,0,0,++sequence));
                    if((ticks%20)==0)
                        System.out.println("FF-SUPERVISOR PLAYER_INPUT"
                            +" sequence="+sequence
                            +" bodyPosition="+occupiedMob.position()
                            +" playerPosition="+account.position()
                            +" playerToMobDistance="+account.distanceToSqr(occupiedMob));
                }
                double playerMoved=Math.hypot(account.getX()-accountStartX,
                    account.getZ()-accountStartZ);
                double mobMoved=Math.hypot(occupiedMob.getX()-mobStartX,
                    occupiedMob.getZ()-mobStartZ);
                if(playerMoved>0.12 && mobMoved>0.45) {
                    requireIds();
                    requireHealthy(account,occupiedMob,target);
                    NativeMindWorkerSupervisor.cancelByAccount(account.getUUID());
                    System.out.println("FF-SUPERVISOR PLAYER_MOB_RECIPROCAL PASS"
                        +" mobMindToPlayerBody="+playerMoved
                        +" playerMindToMobBody="+mobMoved
                        +" originalUUIDsRetained=true");
                    cleanupMobPhase();
                    beginMobPair();
                    return;
                }
                if(ticks>MAX_PHASE_TICKS)
                    throw new IllegalStateException("Timed out waiting for both reciprocal Player↔Mob bodies to move"
                        +" player="+playerMoved+" mob="+mobMoved);
            } else if(phase==2) {
                double firstMoved=Math.hypot(firstMob.getX()-firstStartX,
                    firstMob.getZ()-firstStartZ);
                double secondMoved=Math.hypot(secondMob.getX()-secondStartX,
                    secondMob.getZ()-secondStartZ);
                if(firstMoved>0.20 && secondMoved>0.20) {
                    requireHealthy(firstMob,secondMob,target);
                    if(!firstMob.getUUID().equals(originalIds[0])
                            || !secondMob.getUUID().equals(originalIds[1])
                            || !target.getUUID().equals(originalIds[2]))
                        throw new IllegalStateException("Mob↔Mob exchange replaced a physical entity UUID");
                    NativeMindWorkerSupervisor.cancelByAccount(account.getUUID());
                    if(firstMob.isNoAi()!=firstOriginalNoAi
                            || secondMob.isNoAi()!=secondOriginalNoAi)
                        throw new IllegalStateException("Cancelling M↔M did not restore each body's original AI policy");
                    System.out.println("FF-SUPERVISOR MOB_MOB_RECIPROCAL PASS"
                        +" firstMindToSecondBody="+secondMoved
                        +" secondMindToFirstBody="+firstMoved
                        +" originalUUIDsRetained=true"
                        +" originalAiPoliciesRestored=true");
                    cleanupMobPair();
                    phase=3;
                    System.out.println("FF-SUPERVISOR ALL_PAIRINGS PASS");
                    server.halt(false);
                    return;
                }
                if(ticks>MAX_PHASE_TICKS)
                    throw new IllegalStateException("Timed out waiting for both reciprocal Mob bodies to move"
                        +" first="+firstMoved+" second="+secondMoved);
            }
        } catch(Exception failure) {
            fail(phase==1?"PLAYER_MOB":"MOB_MOB",failure);
        }
    }

    private static void beginMobPair() {
        phase=2;ticks=0;
        account.setPos(2.5,64,2.5);
        firstMob=createMob(EntityTypes.ZOMBIE,12.5,64,8.5);
        secondMob=createMob(EntityTypes.HUSK,20.5,64,8.5);
        target=createVillager(16.5,64,8.5);
        firstStartX=firstMob.getX();firstStartZ=firstMob.getZ();
        secondStartX=secondMob.getX();secondStartZ=secondMob.getZ();
        originalIds=new UUID[]{firstMob.getUUID(),secondMob.getUUID(),target.getUUID()};
        firstOriginalNoAi=firstMob.isNoAi();
        secondOriginalNoAi=secondMob.isNoAi();
        String error=NativeMindWorkerSupervisor.startMobPair(account,firstMob,secondMob);
        if(error!=null) throw new IllegalStateException("Mob↔Mob supervisor start rejected: "+error);
        System.out.println("FF-SUPERVISOR MOB_MOB_STARTED PASS");
    }

    private static void requireIds() {
        if(!account.getUUID().equals(originalIds[0])
                || !occupiedMob.getUUID().equals(originalIds[1])
                || !target.getUUID().equals(originalIds[2]))
            throw new IllegalStateException("Physical player/Mob/villager UUID changed during exchange");
    }

    private static void requireHealthy(net.minecraft.world.entity.LivingEntity... entities) {
        // The smoke deliberately verifies identity, not damage. Native melee
        // may legitimately damage a mapped target as part of AI behavior.
        for(var entity:entities)
            if(entity.isRemoved() || !entity.isAlive())
                throw new IllegalStateException("Original physical entity died/was removed during test");
    }

    private static void cleanupMobPhase() {
        if(occupiedMob!=null&&!occupiedMob.isRemoved()) occupiedMob.discard();
        if(target!=null&&!target.isRemoved()) target.discard();
        occupiedMob=null;target=null;
    }

    private static void cleanupMobPair() {
        if(firstMob!=null&&!firstMob.isRemoved()) firstMob.discard();
        if(secondMob!=null&&!secondMob.isRemoved()) secondMob.discard();
        if(target!=null&&!target.isRemoved()) target.discard();
        firstMob=null;secondMob=null;target=null;
    }

    private static void fail(String label,Exception failure) {
        phase=3;
        System.err.println("FF-SUPERVISOR "+label+" FAIL "
            +failure.getClass().getSimpleName()+": "+failure.getMessage());
        if(account!=null) NativeMindWorkerSupervisor.cancelByAccount(account.getUUID());
        cleanupMobPhase();
        cleanupMobPair();
        if(server!=null) server.halt(false);
    }

    @SubscribeEvent
    public static void protectSmokeTarget(LivingIncomingDamageEvent event) {
        // Both reciprocal donor minds can legitimately select this shared
        // sensory stimulus. The smoke verifies routing/action execution, not
        // whether two real mobs can kill a disposable fixture before checks.
        if(enabled() && target!=null && event.getEntity()==target)
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        if(!enabled()) return;
        NativeMindWorkerSupervisor.clearOnServerStop();
        if(account!=null&&!account.isRemoved())
            level.removePlayerImmediately(account,net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
        server=null;level=null;account=null;
    }
}
