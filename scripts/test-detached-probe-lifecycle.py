#!/usr/bin/env python3
"""Independent stateful exercise of REAL detached-host production classes.

A tiny fake world substitutes for Minecraft's APIs, NOT for the production
DetachedMindHostProbe / DetachedHostMirrorProbe / ProbeBodySnapshot code.
This tests control-flow and lifecycle, not real NeoForge behavior or arbitrary
modded AI execution. Run in CI with Java 25; Java 21 also suffices.
"""
from pathlib import Path
import shutil
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parent.parent
DEST = ROOT / "build" / "detached-probe-regression"
STUBS = DEST / "stubs"
CLASSES = DEST / "classes"

SOURCES = {
    "net/minecraft/world/level/Level.java": """
        package net.minecraft.world.level;
        public class Level {}
    """,
    "net/minecraft/world/phys/Vec3.java": """
        package net.minecraft.world.phys;
        public final class Vec3 {
            public final double x, y, z;
            public Vec3(double x,double y,double z) {
                this.x=x; this.y=y; this.z=z;
            }
            public double x(){return x;}
            public double y(){return y;}
            public double z(){return z;}
            public double distanceToSqr(Vec3 o) {
                double a=x-o.x, b=y-o.y, c=z-o.z;
                return a*a+b*b+c*c;
            }
            public double distanceTo(Vec3 o) {
                return Math.sqrt(distanceToSqr(o));
            }
            @Override public boolean equals(Object other){
                if(!(other instanceof Vec3 o))return false;
                return Double.compare(x,o.x)==0
                    && Double.compare(y,o.y)==0 && Double.compare(z,o.z)==0;
            }
            @Override public int hashCode(){return java.util.Objects.hash(x,y,z);}
            @Override public String toString(){return "Vec3("+x+","+y+","+z+")";}
        }
    """,
    "net/minecraft/network/chat/Component.java": """
        package net.minecraft.network.chat;
        public record Component(String text) {
            public static Component literal(String s) { return new Component(s); }
        }
    """,
    "net/minecraft/world/entity/EntitySpawnReason.java": """
        package net.minecraft.world.entity;
        public enum EntitySpawnReason { COMMAND }
    """,
    "net/minecraft/world/entity/EntityType.java": """
        package net.minecraft.world.entity;
        import java.util.function.Function;
        import net.minecraft.server.level.ServerLevel;
        public final class EntityType<T extends Entity> {
            private final String name;
            private final Function<ServerLevel, T> factory;
            public EntityType(String name, Function<ServerLevel, T> factory) {
                this.name=name; this.factory=factory;
            }
            public T create(ServerLevel level, EntitySpawnReason reason) {
                return factory.apply(level);
            }
            public String toString() { return "test:" + name; }
        }
    """,
    "net/minecraft/world/entity/Entity.java": """
        package net.minecraft.world.entity;
        import java.util.UUID;
        import net.minecraft.server.level.ServerLevel;
        import net.minecraft.world.phys.Vec3;
        import net.minecraft.world.level.Level;
        public class Entity {
            protected final ServerLevel level;
            private final EntityType<?> type;
            private final UUID id = UUID.randomUUID();
            private Vec3 pos = new Vec3(0,64,0);
            public Entity(ServerLevel level, EntityType<?> type) {
                this.level=level; this.type=type;
            }
            public UUID getUUID() { return id; }
            public EntityType<?> getType() { return type; }
            public Level level() { return level; }
            public Vec3 position() { return pos; }
            public double getX() { return pos.x(); }
            public double getY() { return pos.y(); }
            public double getZ() { return pos.z(); }
            public void setPos(double x,double y,double z) { pos=new Vec3(x,y,z); }
        }
    """,
    "net/minecraft/world/entity/Mob.java": """
        package net.minecraft.world.entity;
        import java.util.List;
        import net.minecraft.server.level.ServerLevel;
        public class Mob extends Entity {
            public static class Selector {
                private int count=3;
                public List<Integer> getAvailableGoals() {
                    return java.util.Collections.nCopies(count, 1);
                }
                public void setCount(int n){count=n;}
            }
            public static class Brain {
                public boolean isBrainDead(){return false;}
            }
            public final Selector goalSelector=new Selector();
            public final Selector targetSelector=new Selector();
            private final Brain brain=new Brain();
            private float health=20f, maxHealth=20f;
            private boolean alive=true;
            public Mob(ServerLevel level, EntityType<?> type){ super(level,type); }
            public float getHealth(){return health;}
            public float getMaxHealth(){return maxHealth;}
            public void setHealth(float h){health=h;}
            public void setMaxHealth(float h){maxHealth=h;}
            public boolean isAlive(){return alive;}
            public void setAlive(boolean b){alive=b;}
            public Brain getBrain(){return brain;}
        }
    """,
    "net/minecraft/server/MinecraftServer.java": """
        package net.minecraft.server;
        import java.util.*;
        import net.minecraft.server.level.ServerPlayer;
        public final class MinecraftServer {
            private final PlayerList players=new PlayerList();
            public PlayerList getPlayerList(){return players;}
            public static final class PlayerList {
                public final Map<UUID,ServerPlayer> map=new HashMap<>();
                public ServerPlayer getPlayer(UUID id){return map.get(id);}
            }
        }
    """,
    "net/minecraft/server/level/ServerLevel.java": """
        package net.minecraft.server.level;
        import java.util.*;
        import net.minecraft.server.MinecraftServer;
        import net.minecraft.world.entity.Entity;
        import net.minecraft.world.level.Level;
        public final class ServerLevel extends Level {
            public int unrelatedWorldWrites=0;
            private long time;
            private final MinecraftServer server=new MinecraftServer();
            private final Map<UUID,Entity> entities=new HashMap<>();
            public Entity getEntity(UUID id){return entities.get(id);}
            public void add(Entity e){entities.put(e.getUUID(), e);}
            public MinecraftServer getServer(){return server;}
            public long getGameTime(){return time;}
            public void advance(){time++;}
        }
    """,
    "net/minecraft/server/level/ServerPlayer.java": """
        package net.minecraft.server.level;
        import java.util.*;
        import net.minecraft.network.chat.Component;
        import net.minecraft.world.entity.*;
        public final class ServerPlayer extends Mob {
            public final List<String> messages=new ArrayList<>();
            public ServerPlayer(ServerLevel level, EntityType<?> type){
                super(level,type); level.getServer().getPlayerList().map.put(getUUID(),this);
            }
            public void sendSystemMessage(Component c){messages.add(c.text());}
        }
    """,
    "net/neoforged/bus/api/SubscribeEvent.java": """
        package net.neoforged.bus.api;
        import java.lang.annotation.*;
        @Retention(RetentionPolicy.RUNTIME)
        @Target(ElementType.METHOD)
        public @interface SubscribeEvent {}
    """,
    "net/neoforged/neoforge/event/entity/EntityJoinLevelEvent.java": """
        package net.neoforged.neoforge.event.entity;
        import net.minecraft.world.entity.Entity;
        public final class EntityJoinLevelEvent {
            private final Entity entity;
            private boolean canceled;
            public EntityJoinLevelEvent(Entity e){entity=e;}
            public Entity getEntity(){return entity;}
            public void setCanceled(boolean b){canceled=b;}
            public boolean isCanceled(){return canceled;}
        }
    """,
    "net/neoforged/neoforge/event/entity/EntityLeaveLevelEvent.java": """
        package net.neoforged.neoforge.event.entity;
        import net.minecraft.world.entity.Entity;
        public final class EntityLeaveLevelEvent {
            private final Entity entity;
            public EntityLeaveLevelEvent(Entity e){entity=e;}
            public Entity getEntity(){return entity;}
        }
    """,
    "net/neoforged/neoforge/event/tick/EntityTickEvent.java": """
        package net.neoforged.neoforge.event.tick;
        import net.minecraft.world.entity.Entity;
        public final class EntityTickEvent {
            public record Post(Entity entity) {
                public Entity getEntity(){return entity;}
            }
        }
    """,
    "net/neoforged/neoforge/event/tick/ServerTickEvent.java": """
        package net.neoforged.neoforge.event.tick;
        import net.minecraft.server.MinecraftServer;
        public final class ServerTickEvent {
            public record Post(MinecraftServer server) {
                public MinecraftServer getServer(){return server;}
            }
        }
    """,
    "net/neoforged/neoforge/event/server/ServerStoppedEvent.java": """
        package net.neoforged.neoforge.event.server;
        public final class ServerStoppedEvent {}
    """,
    "dev/freakyfriday/mind/ProbeTelemetry.java": """
        package dev.freakyfriday.mind;
        import java.util.*;
        public final class ProbeTelemetry {
            public static final List<String> EVENTS=new ArrayList<>();
            private static int sequence;
            public static String newTrace(){return "UNIT-"+(++sequence);}
            public static void info(String trace,String stage,String format,Object... args){
                EVENTS.add(stage);
            }
            public static void warn(String trace,String stage,String format,Object... args){
                EVENTS.add(stage);
            }
            public static void error(String trace,String stage,Throwable error,String detail){
                EVENTS.add(stage);
            }
        }
    """,
    "dev/freakyfriday/mind/DetachedProbeIntegrationHarness.java": """
        package dev.freakyfriday.mind;
        import java.lang.reflect.*;
        import java.util.*;
        import net.minecraft.server.level.*;
        import net.minecraft.world.entity.*;
        import net.neoforged.neoforge.event.entity.*;
        import net.neoforged.neoforge.event.tick.*;
        import net.neoforged.neoforge.event.server.*;

        public final class DetachedProbeIntegrationHarness {
            static int checks=0;
            static boolean throwFactory,mutateFactory,worldWriteFactory,mutateHostSetPos;
            static Mob victim;
            static final EntityType<Cow> COW=new EntityType<>("cow",Cow::new);
            static final EntityType<Zombie> ZOMBIE=new EntityType<>("zombie",level->{
                if(worldWriteFactory) level.unrelatedWorldWrites++;
                if(mutateFactory && victim!=null) victim.setHealth(victim.getHealth()-3);
                if(throwFactory) throw new IllegalStateException("Injected factory failure");
                return new Zombie(level);
            });
            // The type is only used as an identity; no player factory is invoked.
            static final EntityType<Mob> PLAYER_SENTINEL=new EntityType<>("player-sentinel",
                level->new Mob(level,null));

            static final class Cow extends Mob {
                Cow(ServerLevel level){super(level,COW);}
            }
            static final class Zombie extends Mob {
                Zombie(ServerLevel level){super(level,ZOMBIE);}
                @Override public void setPos(double x,double y,double z){
                    super.setPos(x,y,z);
                    if(mutateHostSetPos && level.getEntity(getUUID())==null && victim!=null)
                        victim.setHealth(victim.getHealth()-2);
                }
            }
            record Fixture(ServerLevel level, ServerPlayer player, Zombie donor, Cow body) {}
            static Fixture fixture(){
                resetFaults();
                ServerLevel level=new ServerLevel();
                ServerPlayer player=new ServerPlayer(level,PLAYER_SENTINEL);
                Zombie donor=new Zombie(level);
                Cow body=new Cow(level);
                level.add(donor); level.add(body);
                donor.setPos(1,64,1);
                body.setPos(10,64,10);
                victim=body;
                return new Fixture(level,player,donor,body);
            }
            static void resetFaults(){
                throwFactory=mutateFactory=worldWriteFactory=mutateHostSetPos=false;
                victim=null;
            }
            static void check(boolean ok,String message){
                checks++;
                if(!ok)throw new AssertionError(message);
            }
            @SuppressWarnings("unchecked")
            static Map<UUID,Object> sessions() throws Exception {
                Field f=DetachedHostMirrorProbe.class.getDeclaredField("SESSIONS");
                f.setAccessible(true);
                return (Map<UUID,Object>)f.get(null);
            }
            static Mob host(Object session) throws Exception {
                Field f=session.getClass().getDeclaredField("host");
                f.setAccessible(true);
                return (Mob)f.get(session);
            }
            static void serverTick(Fixture f){
                f.level.advance();
                DetachedHostMirrorProbe.onServerTick(new ServerTickEvent.Post(f.level.getServer()));
            }
            static void physicalTick(Fixture f){
                DetachedHostMirrorProbe.onPhysicalTick(new EntityTickEvent.Post(f.body));
            }
            static void clean(){
                DetachedHostMirrorProbe.onStop(new ServerStoppedEvent());
                check(sessionsEmpty(),"session cleanup after simulated server shutdown");
            }
            static boolean sessionsEmpty(){
                try {return sessions().isEmpty();}
                catch(Exception e){throw new RuntimeException(e);}
            }
            public static void main(String[] args)throws Exception{
                // Snapshot checks actual real-code field comparisons, not a mock.
                Fixture f=fixture();
                ProbeBodySnapshot s=ProbeBodySnapshot.take(f.body);
                check(s.unchanged(f.body),"baseline unchanged");
                f.body.setHealth(17);
                check(s.changes(f.body).contains("health"),"health mutation detected");
                f.body.setHealth(20);
                f.body.goalSelector.setCount(5);
                check(s.changes(f.body).contains("goalCount"),"goal mutation detected");
                f.body.goalSelector.setCount(3);
                f.body.setPos(11,64,10);
                check(s.changes(f.body).contains("position"),"position mutation detected");
                f.body.setPos(10,64,10);
                check(s.unchanged(f.body),"snapshot returns to baseline");
                clean();

                // Truly cross-species: actual Zombie donor factory, Cow body.
                f=fixture();
                String started=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(started.contains("started"),"cross-species mirror accepted");
                check(sessions().size()==1,"real probe keeps one retained session");
                Object session=sessions().values().iterator().next();
                Mob ghost=host(session);
                check(ghost.getClass()==Zombie.class,"actual host is Zombie, not Cow");
                check(ghost.getType()==f.donor.getType(),"donor EntityType preserved");
                check(ghost!=f.donor && ghost!=f.body,"host is distinct object");
                check(f.level.getEntity(ghost.getUUID())==null,"host is unregistered");
                check(ghost.position().distanceToSqr(f.body.position())<1e-9,
                    "host aligned with Cow body");
                String duplicate=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(duplicate.contains("already"),"duplicate session rejected");
                for(int i=0;i<40;i++){
                    f.body.setPos(10+i*.1,64,10);
                    serverTick(f);physicalTick(f);
                    if(i<39)check(ghost.position().distanceToSqr(f.body.position())<1e-9,
                        "host follows Cow at tick "+i);
                    check(f.level.getEntity(ghost.getUUID())==null,
                        "never world-registers detached host tick "+i);
                }
                check(sessionsEmpty(),"40-tick completion frees session");
                check(f.donor.getHealth()==20 && f.body.getHealth()==20,
                    "donor and recipient health preserved");
                check(ProbeTelemetry.EVENTS.contains("mirror_finished"),
                    "final lifecycle telemetry reached");
                clean();

                // Original-body mutation caused by factory: real production rejection.
                f=fixture();
                mutateFactory=true;
                started=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(started.contains("rejected") || started.contains("changed"),
                    "factory modifying original body is rejected");
                check(sessionsEmpty(),"no session retained after factory mutation");
                check(ProbeTelemetry.EVENTS.contains("constructor_body_mutation"),
                    "constructor mutation recorded");
                clean();

                // Constructor throws: catch, log, do not retain live entities.
                f=fixture();throwFactory=true;
                started=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(started.contains("failed"),"thrown native factory rejected");
                check(sessionsEmpty(),"failed factory retains no session");
                check(ProbeTelemetry.EVENTS.contains("constructor_exception"),
                    "factory exception logged");
                clean();

                // Host positioning with side effects detected before session.
                f=fixture();mutateHostSetPos=true;
                started=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(started.contains("changed"),"unsafe host setPos rejected");
                check(sessionsEmpty(),"unsafe preflight has no active session");
                clean();

                // An entity which stops receiving physical ticks is retired
                // exactly at the 120th server tick, without relying on unload.
                f=fixture();
                started=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(started.contains("started"),"stall test starts");
                for(int i=0;i<119;i++)serverTick(f);
                check(sessions().size()==1,"not prematurely expired at 119");
                serverTick(f);
                check(sessionsEmpty(),"watchdog expires stalled session at 120");
                check(ProbeTelemetry.EVENTS.contains("mirror_watchdog_timeout"),
                    "watchdog telemetry emitted");
                clean();

                // Both lifecycle exit paths dispose of detached hosts.
                f=fixture();
                DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                DetachedHostMirrorProbe.onLeave(new EntityLeaveLevelEvent(f.donor));
                check(sessionsEmpty(),"donor unload cleans up session");
                clean();

                f=fixture();
                DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                ghost=host(sessions().values().iterator().next());
                EntityJoinLevelEvent join=new EntityJoinLevelEvent(ghost);
                DetachedHostMirrorProbe.onUnexpectedEntityJoin(join);
                check(join.isCanceled(),"attempted ghost world join is canceled");
                check(sessionsEmpty(),"join violation closes session");
                clean();

                // Safety LIMIT, explicitly documented: the unspawned factory
                // can write unrelated world state and remain undetected.
                f=fixture();worldWriteFactory=true;
                started=DetachedHostMirrorProbe.start(f.player,f.donor,f.body);
                check(f.level.unrelatedWorldWrites==1,
                    "fake world exposes unauthorized third-party factory write");
                check(started.contains("started"),
                    "CURRENT UNSAFE GAP: existing probes miss unrelated world writes");
                check(sessions().size()==1,
                    "world writes cannot be treated as isolated by current runtime");
                clean();
                System.out.println("PASS DetachedProbeIntegrationHarness: "+checks
                    +" assertions across real probe classes with injected failures.");
                System.out.println("EXPECTED SAFETY LIMIT: unrelated factory world writes "
                    +"are not intercepted; no donor native AI was executed.");
            }
        }
    """,
}

def main():
    if DEST.exists():
        shutil.rmtree(DEST)
    STUBS.mkdir(parents=True, exist_ok=True)
    CLASSES.mkdir(parents=True, exist_ok=True)
    for name, value in SOURCES.items():
        path = STUBS / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(textwrap.dedent(value).lstrip() + "\n")
    real = [
        ROOT / "src/main/java/dev/freakyfriday/mind/ProbeTimeoutGuard.java",
        ROOT / "src/main/java/dev/freakyfriday/mind/ProbeBodySnapshot.java",
        ROOT / "src/main/java/dev/freakyfriday/mind/DetachedMindHostProbe.java",
        ROOT / "src/main/java/dev/freakyfriday/mind/DetachedHostMirrorProbe.java",
    ]
    files = [str(p) for p in sorted(STUBS.rglob("*.java"))] + [str(p) for p in real]
    subprocess.run(["javac", "-d", str(CLASSES), *files], cwd=ROOT, check=True)
    subprocess.run(["java", "-ea", "-cp", str(CLASSES),
                    "dev.freakyfriday.mind.DetachedProbeIntegrationHarness"],
                   cwd=ROOT, check=True)

if __name__ == "__main__":
    main()
