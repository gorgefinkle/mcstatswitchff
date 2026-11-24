package dev.freakyfriday.mind;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;

/**
 * Bilateral M↔M integration acceptance. Two independent native-worker JVMs
 * produce reciprocal minds, and their signed frames drive both original
 * physical bodies in the same receiver-world session.
 */
public final class ReciprocalMobWorkerGameTests {
    private ReciprocalMobWorkerGameTests() {}


    private static Properties load(Path path) throws Exception {
        Properties properties=new Properties();
        try(InputStream in=Files.newInputStream(path)){properties.load(in);}
        return properties;
    }

    private static int[] chunkBounds(GameTestHelper helper) {
        BlockPos min=helper.absolutePos(new BlockPos(1,1,1));
        BlockPos max=helper.absolutePos(new BlockPos(30,1,30));
        return new int[]{min.getX()>>4,max.getX()>>4,min.getZ()>>4,max.getZ()>>4};
    }

    private static void tickets(ServerLevel level,UUID owner,int[] bounds,boolean force) {
        for(int cx=bounds[0];cx<=bounds[1];cx++)
            for(int cz=bounds[2];cz<=bounds[3];cz++)
                NativeAiWorkerServer.setEntityTickingChunk(level,owner,cx,cz,force);
    }

    private static void cleanup(ServerLevel level,UUID owner,int[] bounds,
            Mob chicken,Mob zombie,Mob villager) {
        if(chicken!=null && !chicken.isRemoved()) chicken.discard();
        if(zombie!=null && !zombie.isRemoved()) zombie.discard();
        if(villager!=null && !villager.isRemoved()) villager.discard();
        tickets(level,owner,bounds,false);
    }

    static void verify(GameTestHelper helper) {
        Path forwardRequest=Path.of("ff-worker-request.properties");
        Path reverseRequest=Path.of("ff-worker-request-reverse.properties");
        boolean reciprocalFixture=Files.isRegularFile(reverseRequest);
        boolean reciprocalExpected="true".equalsIgnoreCase(
            System.getenv("FF_RECIPROCAL_E2E"));
        System.out.println("FF-NATIVE-WORKER RECIPROCAL_MOB_TEST_ENTERED"
            +" fixture="+reciprocalFixture
            +" expected="+reciprocalExpected
            +" cwd="+Path.of("").toAbsolutePath());
        if(!reciprocalFixture) {
            if(reciprocalExpected)
                throw new IllegalStateException(
                    "Reciprocal e2e receiver did not receive ff-worker-request-reverse.properties");
            System.out.println("FF-NATIVE-WORKER RECIPROCAL_MOB_SKIPPED_NO_TWO_WORKER_FIXTURE");
            helper.succeed();
            return;
        }
        Path forwardFrame=Path.of("ff-worker-frame.bin");
        Path forwardNext=Path.of("ff-worker-frame-next.bin");
        Path reverseFrame=Path.of("ff-worker-frame-reverse.bin");
        Path reverseNext=Path.of("ff-worker-frame-next-reverse.bin");
        helper.assertTrue(Files.isRegularFile(forwardRequest)
                && Files.isRegularFile(forwardFrame) && Files.isRegularFile(forwardNext)
                && Files.isRegularFile(reverseFrame) && Files.isRegularFile(reverseNext),
            "A bilateral two-worker fixture must contain both requests and all four frames");

        ServerLevel level=helper.getLevel();
        Mob chicken=null,zombie=null,villager=null;
        UUID owner=UUID.randomUUID();
        int[] bounds=chunkBounds(helper);
        tickets(level,owner,bounds,true);
        try {
            Properties zombieRequest=load(forwardRequest);
            Properties chickenRequest=load(reverseRequest);
            helper.assertTrue("minecraft:zombie".equals(zombieRequest.getProperty("donor"))
                    && "minecraft:chicken".equals(zombieRequest.getProperty("recipient"))
                    && "minecraft:chicken".equals(chickenRequest.getProperty("donor"))
                    && "minecraft:zombie".equals(chickenRequest.getProperty("recipient"))
                    && "true".equals(zombieRequest.getProperty("requireAttack"))
                    && "false".equals(chickenRequest.getProperty("requireAttack")),
                "Reciprocal fixture must carry both native directions");
            UUID chickenId=UUID.fromString(zombieRequest.getProperty("recipientBody"));
            UUID zombieId=UUID.fromString(chickenRequest.getProperty("recipientBody"));
            UUID zombieMind=UUID.fromString(zombieRequest.getProperty("sourceMind"));
            UUID chickenMind=UUID.fromString(chickenRequest.getProperty("sourceMind"));
            UUID targetId=UUID.fromString(zombieRequest.getProperty("stimulusId"));
            helper.assertTrue(targetId.equals(UUID.fromString(chickenRequest.getProperty("stimulusId")))
                    && !chickenId.equals(zombieId) && !chickenMind.equals(zombieMind),
                "Two physical bodies/minds must retain separate identities");

            chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
            zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
            villager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(chicken!=null && zombie!=null && villager!=null,
                "Bilateral receiver needs real Chicken, Zombie and Villager entities");
            for(int x=0;x<=30;x++) for(int z=0;z<=30;z++) {
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                    Blocks.STONE.defaultBlockState());
                // Keep the physical Zombie body out of daylight burn logic.
                // This test measures mind-driven movement, not environmental
                // damage from the receiver world's day cycle.
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,8,z)),
                    Blocks.STONE.defaultBlockState());
            }
            BlockPos chickenPos=helper.absolutePos(new BlockPos(8,1,8));
            BlockPos zombiePos=helper.absolutePos(new BlockPos(21,1,8));
            BlockPos villagerPos=helper.absolutePos(new BlockPos(12,1,8));
            chicken.setPos(chickenPos.getX()+0.5,chickenPos.getY(),chickenPos.getZ()+0.5);
            zombie.setPos(zombiePos.getX()+0.5,zombiePos.getY(),zombiePos.getZ()+0.5);
            villager.setPos(villagerPos.getX()+0.5,villagerPos.getY(),villagerPos.getZ()+0.5);
            chicken.setUUID(chickenId);
            zombie.setUUID(zombieId);
            villager.setUUID(targetId);
            chicken.goalSelector.removeAllGoals(goal->true);
            chicken.targetSelector.removeAllGoals(goal->true);
            zombie.goalSelector.removeAllGoals(goal->true);
            zombie.targetSelector.removeAllGoals(goal->true);
            chicken.setNoAi(true);
            zombie.setNoAi(true);
            villager.setNoAi(true);
            villager.setNoGravity(true);
            chicken.setPersistenceRequired();
            zombie.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(chicken)
                    && level.addFreshEntity(zombie)
                    && level.addFreshEntity(villager),
                "Bilateral receiver physical entities failed to register");

            final Mob chickenBody=chicken,zombieBody=zombie,targetBody=villager;
            final float chickenHealth=chickenBody.getHealth();
            final float zombieHealth=zombieBody.getHealth();
            final float villagerHealth=targetBody.getHealth();
            CapturedIntentBridge bridge=new CapturedIntentBridge();
            bridge.register(new MinecraftBodyPort(chickenBody),
                new MindLedger.Mind(chickenMind,MindLedger.Kind.NPC,"minecraft:chicken"));
            bridge.register(new MinecraftBodyPort(zombieBody),
                new MindLedger.Mind(zombieMind,MindLedger.Kind.NPC,"minecraft:zombie"));
            bridge.exchange(chickenId,zombieId);
            helper.assertTrue(bridge.controlling(chickenId).identity().equals(zombieMind)
                    && bridge.controlling(zombieId).identity().equals(chickenMind),
                "The two native minds were not exchanged across the original physical bodies");

            byte[] secret=java.util.HexFormat.of().parseHex(
                System.getenv("FF_WORKER_KEY_HEX"));
            helper.assertTrue(secret.length==CapturedIntentWireCodec.KEY_BYTES,
                "Bilateral worker HMAC key is missing");
            UUID zombieSession=UUID.fromString(zombieRequest.getProperty("session"));
            UUID chickenSession=UUID.fromString(chickenRequest.getProperty("session"));
            long zombieTick=Long.parseLong(zombieRequest.getProperty("recipientTick"));
            long chickenTick=Long.parseLong(chickenRequest.getProperty("recipientTick"));
            byte[] zombiePacket=Files.readAllBytes(forwardFrame);
            byte[] zombieNextPacket=Files.readAllBytes(forwardNext);
            byte[] chickenPacket=Files.readAllBytes(reverseFrame);
            byte[] chickenNextPacket=Files.readAllBytes(reverseNext);
            var zombieFrame=CapturedIntentWireCodec.decode(
                zombiePacket,secret,zombieSession,zombieTick).frame();
            var chickenFrame=CapturedIntentWireCodec.decode(
                chickenPacket,secret,chickenSession,chickenTick).frame();
            var zombieNext=CapturedIntentWireCodec.decode(
                zombieNextPacket,secret,zombieSession,Math.addExact(zombieTick,1)).frame();
            var chickenNext=CapturedIntentWireCodec.decode(
                chickenNextPacket,secret,chickenSession,Math.addExact(chickenTick,1)).frame();
            helper.assertTrue(zombieFrame.bodyId().equals(chickenId)
                    && zombieFrame.mindId().equals(zombieMind)
                    && zombieFrame.actions().size()==3
                    && zombieFrame.actions().get(2).actionId().equals(MinecraftBodyPort.ATTACK_ENTITY)
                    && targetId.toString().equals(zombieFrame.actions().get(2).arguments().get("targetId")),
                "Zombie native decision did not preserve the mapped Villager target action");
            helper.assertTrue(chickenFrame.bodyId().equals(zombieId)
                    && chickenFrame.mindId().equals(chickenMind)
                    && chickenFrame.actions().size()>=2
                    && chickenFrame.actions().get(0).actionId().equals(MinecraftBodyPort.LOOK_OFFSET)
                    && chickenFrame.actions().get(1).actionId().equals(MinecraftBodyPort.MOVE_OFFSET),
                "Chicken native decision did not map to the physical Zombie body");
            helper.assertTrue(zombieNext.bodyId().equals(chickenId)
                    && zombieNext.mindId().equals(zombieMind)
                    && chickenNext.bodyId().equals(zombieId)
                    && chickenNext.mindId().equals(chickenMind),
                "Adjacent sensory updates changed the reciprocal mind/body mappings");

            final double[] start={Double.NaN,Double.NaN,Double.NaN,Double.NaN};
            helper.runAfterDelay(5,()->{
                try {
                    helper.assertTrue(level.getEntity(chickenId)==chickenBody
                            && level.getEntity(zombieId)==zombieBody
                            && level.getEntity(targetId)==targetBody,
                        "Original physical Mob UUIDs were not indexed in the common receiver world");
                    start[0]=chickenBody.getX();start[1]=chickenBody.getZ();
                    start[2]=zombieBody.getX();start[3]=zombieBody.getZ();
                    float healthBeforeAttack=targetBody.getHealth();
                    var chickenResult=bridge.deliverAuthenticated(
                        zombiePacket,secret,zombieSession,zombieTick);
                    var zombieResult=bridge.deliverAuthenticated(
                        chickenPacket,secret,chickenSession,chickenTick);
                    helper.assertTrue(chickenResult.status()==CapturedIntentBridge.Status.DISPATCHED
                            && chickenResult.outcomes().size()==3
                            && chickenResult.outcomes().get(0).status()==BodyActionGate.Status.EXECUTED
                            && chickenResult.outcomes().get(1).status()==BodyActionGate.Status.EXECUTED
                            && chickenResult.outcomes().get(2).status()==BodyActionGate.Status.UNAVAILABLE,
                        "Zombie mind did not run its supported movement and body-limited attack on Chicken");
                    helper.assertTrue(zombieResult.status()==CapturedIntentBridge.Status.DISPATCHED
                            && zombieResult.outcomes().size()==chickenFrame.actions().size()
                            && zombieResult.outcomes().get(0).status()==BodyActionGate.Status.EXECUTED
                            && zombieResult.outcomes().get(1).status()==BodyActionGate.Status.EXECUTED,
                        "Chicken mind did not execute its native movement on Zombie");
                    helper.assertTrue(Float.compare(targetBody.getHealth(),healthBeforeAttack)==0,
                        "Unsupported Chicken attack damaged the physical Villager target");
                    helper.runAfterDelay(1,()->{
                        try {
                            var chickenNextResult=bridge.deliverAuthenticated(
                                zombieNextPacket,secret,zombieSession,Math.addExact(zombieTick,1));
                            var zombieNextResult=bridge.deliverAuthenticated(
                                chickenNextPacket,secret,chickenSession,Math.addExact(chickenTick,1));
                            helper.assertTrue(chickenNextResult.status()==CapturedIntentBridge.Status.DISPATCHED
                                    && zombieNextResult.status()==CapturedIntentBridge.Status.DISPATCHED,
                                "Both reciprocal bodies rejected their second adjacent native frames");
                            helper.runAfterDelay(50,()->{
                                try {
                                    double chickenMoved=Math.hypot(chickenBody.getX()-start[0],
                                        chickenBody.getZ()-start[1]);
                                    double zombieMoved=Math.hypot(zombieBody.getX()-start[2],
                                        zombieBody.getZ()-start[3]);
                                    helper.assertTrue(chickenMoved>0.20 && zombieMoved>0.20,
                                        "Both physical bodies must move from the opposite native mind"
                                        +" chicken="+chickenMoved+" zombie="+zombieMoved);
                                    helper.assertTrue(chickenBody.getUUID().equals(chickenId)
                                            && zombieBody.getUUID().equals(zombieId)
                                            && Float.compare(chickenBody.getHealth(),chickenHealth)==0
                                            && Float.compare(zombieBody.getHealth(),zombieHealth)==0
                                            && Float.compare(targetBody.getHealth(),villagerHealth)==0,
                                        "Mob↔mob exchange changed physical state: "
                                            +"chickenId="+chickenBody.getUUID().equals(chickenId)
                                            +" zombieId="+zombieBody.getUUID().equals(zombieId)
                                            +" chickenHealth="+chickenBody.getHealth()+"/"+chickenHealth
                                            +" zombieHealth="+zombieBody.getHealth()+"/"+zombieHealth
                                            +" villagerHealth="+targetBody.getHealth()+"/"+villagerHealth);
                                    System.out.println("FF-NATIVE-WORKER RECIPROCAL_MOB_PAIR PASS"
                                        +" zombieMindToChickenBody="+chickenMoved
                                        +" chickenMindToZombieBody="+zombieMoved
                                        +" bothOriginalBodiesRetained=true"
                                        +" unavailableAnatomyBlocked=true"
                                        +" twoIndependentWorkers=true");
                                    helper.succeed();
                                } finally {
                                    cleanup(level,owner,bounds,chickenBody,zombieBody,targetBody);
                                }
                            });
                        } catch(Exception failure) {
                            cleanup(level,owner,bounds,chickenBody,zombieBody,targetBody);
                            throw new AssertionError("Second reciprocal worker frame failed",failure);
                        }
                    });
                } catch(Exception failure) {
                    cleanup(level,owner,bounds,chickenBody,zombieBody,targetBody);
                    throw new AssertionError("First reciprocal worker frame failed",failure);
                }
            });
        } catch(Exception failure) {
            cleanup(level,owner,bounds,chicken,zombie,villager);
            throw new AssertionError("Reciprocal native Mob worker acceptance failed",failure);
        }
    }
}
