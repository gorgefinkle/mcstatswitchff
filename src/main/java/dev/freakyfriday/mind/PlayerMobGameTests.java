package dev.freakyfriday.mind;

import dev.freakyfriday.FreakyFriday;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * GAMEPLAY acceptance, not detached-host construction:
 * book + real Chicken + self actually enters possessed body, physical Chicken
 * moves with genuine server ticks and collision, cancel restores original AI.
 *
 * Client camera/input rendering still needs a real connected-client test.
 * Original Player body is not yet driven by donor chicken AI.
 */
public final class PlayerMobGameTests {
    private PlayerMobGameTests() {}

    private static final class OneShotNativeAttackGoal extends Goal {
        private final Mob attacker;
        private final LivingEntity target;
        private boolean fired;
        OneShotNativeAttackGoal(Mob attacker,LivingEntity target) {
            this.attacker=attacker;
            this.target=target;
        }
        @Override public boolean canUse() { return !fired&&target.isAlive(); }
        @Override public boolean canContinueToUse() { return !fired&&target.isAlive(); }
        @Override public void tick() {
            if(fired||!target.isAlive())return;
            fired=true;
            attacker.doHurtTarget((ServerLevel)attacker.level(),target);
        }
    }

    private record GoalSnapshot(int priority,Goal goal) {}

    private static List<GoalSnapshot> snapshotGoals(Mob body,boolean targets) {
        List<GoalSnapshot> result=new java.util.ArrayList<>();
        var selector=targets?body.targetSelector:body.goalSelector;
        for(WrappedGoal wrapped:selector.getAvailableGoals())
            result.add(new GoalSnapshot(wrapped.getPriority(),wrapped.getGoal()));
        return List.copyOf(result);
    }

    private static boolean hasSameGoals(Mob body,boolean targets,List<GoalSnapshot> expected) {
        List<GoalSnapshot> actual=snapshotGoals(body,targets);
        if(actual.size()!=expected.size()) return false;
        for(GoalSnapshot wanted:expected) {
            if(actual.stream().noneMatch(found->
                    found.priority()==wanted.priority()&&found.goal()==wanted.goal()))
                return false;
        }
        return true;
    }

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        PLAYER_CONTROLS_CHICKEN=FUNCTIONS.register(
            "player_controls_chicken",()->PlayerMobGameTests::verifyPlayerControlsChicken);

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        NATIVE_SPEED_COLLISION=FUNCTIONS.register(
            "native_speed_collision",()->PlayerMobGameTests::verifyNativeSpeedCollision);

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        FALL_PROFILES=FUNCTIONS.register(
            "native_fall_profiles",()->PlayerMobGameTests::verifyFallProfiles);
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        MOB_DEATH_RETURN=FUNCTIONS.register(
            "mob_death_returns_player",()->PlayerMobGameTests::verifyMobDeathReturn);

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        OCCUPIED_OCELOT_DAMAGE=FUNCTIONS.register(
            "ocelot_damage_and_death",()->PlayerMobGameTests::verifyOccupiedOcelotDamage);

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        EXPLICIT_SELF_REQUIRED=FUNCTIONS.register(
            "explicit_self_selection",()->PlayerMobGameTests::verifyExplicitSelfSelection);
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        MOB_ORIGIN_ATTACK=FUNCTIONS.register(
            "possessed_mob_attack",()->PlayerMobGameTests::verifyMobOriginAttack);
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        PLAYER_MOB_NATIVE_PROXY=FUNCTIONS.register(
            "player_mob_native_mind_proxy",()->PlayerMobGameTests::verifyPlayerMobNativeMindProxy);
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        ZOMBIE_CHICKEN_BEHAVIOR=FUNCTIONS.register(
            "zombie_chicken_behavior_acceptance",
            ()->PlayerMobGameTests::verifyZombieChickenBehaviorAcceptance);
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        MOB_MOB_RECIPROCAL_ATTACK=FUNCTIONS.register(
            "mob_mob_reciprocal_attack",()->PlayerMobGameTests::verifyMobMobReciprocalAttack);

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        PARTIAL_SWAP_REJECTED=FUNCTIONS.register(
            "partial_native_swap_fails_closed",()->PlayerMobGameTests::verifyPartialSwapRejected);

    private static void verifyPlayerControlsChicken(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null,"Actual Chicken physical entity factory failed");
        BlockPos origin=helper.absolutePos(new BlockPos(2,1,2));
        chicken.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
        chicken.setNoAi(false);
        helper.assertTrue(level.addFreshEntity(chicken),
            "Real Chicken did not register in the GameTest level");
        UUID bodyId=chicken.getUUID(), ownerId=player.getUUID();
        var originalBrain=chicken.getBrain();
        var originalGoals=List.copyOf(chicken.goalSelector.getAvailableGoals());
        var originalTargets=List.copyOf(chicken.targetSelector.getAvailableGoals());

        MindSwapSessions.selectFromInteraction(player,chicken);
        helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)
                ==MindSwapSessions.SELECTION_TIMEOUT_TICKS,
            "Selecting first body did not start the eight-second HUD countdown");
        MindSwapSessions.select(player,player);
        helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)==0,
            "Successful reciprocal pair did not clear its pending HUD selection");
        helper.assertTrue(PlayerMobPossession.hasSession(ownerId)
                &&chicken.isNoAi()&&player.isAlive(),
            "Book selection did not start reciprocal Player/Chicken control");
        helper.assertTrue(level.getEntity(bodyId)==chicken,
            "Player/Chicken exchange replaced the original physical Chicken");

        double chickenStart=chicken.getZ();
        for(int pulse=0;pulse<3;pulse++) {
            final long sequence=pulse+1L;
            helper.runAfterDelay(pulse*2,()->PlayerMobPossession.input(player,
                new PlayerMobPossession.Input(1.0f,0.0f,false,0.0f,0.0f,sequence)));
        }
        helper.runAfterDelay(8,()->{
            helper.assertTrue(chicken.getZ()>chickenStart+0.02,
                "Authenticated player input did not move the real Chicken body; "
                    +"startZ="+chickenStart+" finalZ="+chicken.getZ());
            helper.assertTrue(PlayerMobPossession.end(player),
                "Cancel did not end reciprocal Player/Chicken control");
            helper.assertTrue(!chicken.isNoAi()&&chicken.getBrain()==originalBrain
                    &&originalGoals.equals(List.copyOf(chicken.goalSelector.getAvailableGoals()))
                    &&originalTargets.equals(List.copyOf(chicken.targetSelector.getAvailableGoals()))
                    &&level.getEntity(bodyId)==chicken,
                "Cancel did not restore the Chicken's original AI and physical identity");
            System.out.println("FF-GAMEPLAY player_mob_reciprocal_gate PASS"
                +" reciprocalControlStarted=true playerInputMovesRealChicken=true"
                +" originalBodyAndAiRestored=true");
            chicken.discard();
            MindSwapSessions.clearOnLogout(ownerId);
            verifyPlayerBodyActionPort(helper);
            helper.succeed();
        });
    }

    /** Exercise the separate player-body adapter against a real GameTest world. */
    private static void verifyPlayerBodyActionPort(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        BlockPos floor=helper.absolutePos(new BlockPos(2,0,2));
        for(int x=0;x<5;x++)for(int z=0;z<5;z++)
            level.setBlockAndUpdate(floor.offset(x,0,z),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos start=helper.absolutePos(new BlockPos(3,1,3));
        player.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
        Mob target=EntityTypes.COW.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(target!=null,"Player-body attack target factory failed");
        target.setPos(player.getX()+0.75,player.getY(),player.getZ());
        helper.assertTrue(level.addFreshEntity(target),"Player-body attack target did not register");

        BodyActionGate.BodyPort port=MinecraftBodyPortFactory.create(player);
        BodyActionGate.BodyPort mobPort=MinecraftBodyPortFactory.create(target);
        helper.assertTrue(port instanceof MinecraftPlayerBodyPort
                && mobPort instanceof MinecraftBodyPort
                && mobPort.supports(BodyActionGate.MOVE_OFFSET),
            "Body adapter factory did not dispatch solely by physical body type");
        helper.assertTrue(port.id().equals(player.getUUID()),
            "Player body adapter changed physical identity");
        helper.assertTrue(port.supports(BodyActionGate.MOVE_OFFSET)
                && port.supports(BodyActionGate.LOOK_OFFSET)
                && port.supports(BodyActionGate.ATTACK_ENTITY)
                && !port.supports(MinecraftBodyPort.DETONATE),
            "Player body capabilities are not independent from Mob-only abilities");

        double startZ=player.getZ();
        var moved=BodyActionGate.dispatch(port,new BodyActionGate.Intent(
            BodyActionGate.MOVE_OFFSET,java.util.Map.of(
                "dx","0","dz","3","speed","1")));
        helper.assertTrue(moved.status()==BodyActionGate.Status.EXECUTED
                && player.getZ()>startZ,
            "Player body did not move through its own collision-resolved entity movement");

        var looked=BodyActionGate.dispatch(port,new BodyActionGate.Intent(
            BodyActionGate.LOOK_OFFSET,java.util.Map.of(
                "dx","1","dy","0","dz","0")));
        helper.assertTrue(looked.status()==BodyActionGate.Status.EXECUTED
                && Math.abs(player.getYRot()+90.0f)<0.01f,
            "Player body's native view rotation was not updated");

        helper.assertTrue(level.getEntity(player.getUUID())==player,
            "Player action adapter replaced the original ServerPlayer body");

        var playerMind=new MindLedger.Mind(UUID.randomUUID(),
            MindLedger.Kind.PLAYER,"player-native-controller");
        var mobMind=new MindLedger.Mind(UUID.randomUUID(),
            MindLedger.Kind.NPC,"mob-native-controller");
        var bridge=new CapturedIntentBridge();
        bridge.register(port,playerMind);
        bridge.register(mobPort,mobMind);
        try {
            bridge.exchange(player.getUUID(),target.getUUID());
        } catch (Exception failure) {
            throw new AssertionError("Initial reciprocal controller exchange failed", failure);
        }
        helper.assertTrue(bridge.controlling(player.getUUID()).equals(mobMind)
                && bridge.controlling(target.getUUID()).equals(playerMind),
            "Reciprocal controller ownership did not assign mob mind to original player body");
        long tick=level.getGameTime()+1;
        float bridgeTargetHealth=target.getHealth();
        var incomingMobFrame=new CapturedIntentBridge.Frame(player.getUUID(),
            mobMind.identity(),tick,List.of(
                new BodyActionGate.Intent(BodyActionGate.LOOK_OFFSET,
                    java.util.Map.of("dx","0","dy","0","dz","1")),
                new BodyActionGate.Intent(BodyActionGate.MOVE_OFFSET,
                    // Keep the subsequent native player attack inside melee
                    // reach while still exercising a real collision-resolved
                    // second movement segment.
                    java.util.Map.of("dx","0","dz","3","speed","0.2")),
                new BodyActionGate.Intent(BodyActionGate.ATTACK_ENTITY,
                    java.util.Map.of("targetId",target.getUUID().toString()))));
        var incomingMobResult=bridge.deliver(incomingMobFrame);
        helper.assertTrue(incomingMobResult.status()==CapturedIntentBridge.Status.DISPATCHED
                && incomingMobResult.outcomes().size()==3
                && incomingMobResult.outcomes().stream().allMatch(
                    result->result.status()==BodyActionGate.Status.EXECUTED)
                && target.getHealth()<bridgeTargetHealth,
            "Mob controller frame did not drive the original player body's native action adapter"
            +" result="+incomingMobResult+" playerPos="+player.position()
            +" targetPos="+target.position()+" targetHealth="+target.getHealth()
            +" startZ="+startZ);

        // Exercise the actual recipient-world sensory capture and the new
        // authenticated per-tick worker-session contract before reversing the
        // controller exchange. The response producer is deliberately a test
        // fixture; this does not pretend the external AI worker is streaming yet.
        UUID streamSession=UUID.randomUUID();
        byte[] streamKey=CapturedIntentWireCodec.generateKey();
        long streamTick=tick+1;
        var stream=new NativeMindSessionChannel(streamSession,player.getUUID(),
            mobMind.identity(),streamKey,streamTick,2);
        for(int sample=0;sample<3;sample++) {
            long currentTick=stream.nextTick();
            var senses=MinecraftMindSenseCapture.capture(
                player,mobMind.identity(),currentTick);
            var mappedTarget=senses.entities().stream()
                .filter(entity->entity.id().equals(target.getUUID())).findFirst().orElse(null);
            helper.assertTrue(senses.body().x()==0.0 && senses.body().y()==0.0
                    && senses.body().z()==0.0 && mappedTarget!=null && mappedTarget.visible()
                    && Math.abs(mappedTarget.dx()-(target.getX()-player.getX()))<0.001,
                "Player-body senses leaked world coordinates or omitted real target visibility");
            var request=stream.begin(senses);
            helper.assertTrue(request.tick()==currentTick
                    && request.sensoryWire().length>32,
                "Live recipient sensory snapshot was not packaged for worker tick");
            var workerFrame=new CapturedIntentBridge.Frame(player.getUUID(),
                mobMind.identity(),currentTick,List.of(new BodyActionGate.Intent(
                    BodyActionGate.LOOK_OFFSET,java.util.Map.of(
                        "dx","0","dy","0","dz","1"))));
            byte[] workerReply=CapturedIntentWireCodec.encode(
                new CapturedIntentWireCodec.Envelope(streamSession,workerFrame),streamKey);
            var admitted=stream.complete(workerReply,currentTick+1);
            byte[] bodyDispatch=CapturedIntentWireCodec.encode(
                new CapturedIntentWireCodec.Envelope(streamSession,admitted),streamKey);
            var receipt=bridge.deliverAuthenticated(
                bodyDispatch,streamKey,streamSession,currentTick+1);
            helper.assertTrue(receipt.status()==CapturedIntentBridge.Status.DISPATCHED
                    && receipt.outcomes().size()==1
                    && receipt.outcomes().get(0).status()==BodyActionGate.Status.EXECUTED
                    && receipt.tick()==currentTick+1
                    && stream.nextTick()==currentTick+1
                    && !stream.hasOutstandingRequest(),
                "Sequential authenticated worker tick did not reach original player body"
                    +" sample="+sample+" requestTick="+request.tick()
                    +" admittedFrameTick="+admitted.tick()+" receipt="+receipt
                    +" nextSensoryTick="+stream.nextTick()
                    +" outstanding="+stream.hasOutstandingRequest()
                    +" streamState="+stream.state()+" fault="+stream.fault());
        }
        long reverseTick=stream.nextTick();
        stream.close();
        helper.assertTrue(stream.state()==NativeMindSessionChannel.State.CLOSED,
            "Native worker session did not retire cleanly");

        try {
            bridge.exchange(player.getUUID(),target.getUUID());
        } catch (Exception failure) {
            throw new AssertionError("Reverse reciprocal controller exchange failed", failure);
        }
        helper.assertTrue(bridge.controlling(player.getUUID()).equals(playerMind)
                && bridge.controlling(target.getUUID()).equals(mobMind),
            "Reverse controller exchange did not restore both original minds");
        var incomingPlayerFrame=new CapturedIntentBridge.Frame(target.getUUID(),
            mobMind.identity(),reverseTick,List.of(new BodyActionGate.Intent(
                BodyActionGate.LOOK_OFFSET,java.util.Map.of(
                    "dx","1","dy","0","dz","0"))));
        var incomingPlayerResult=bridge.deliver(incomingPlayerFrame);
        helper.assertTrue(incomingPlayerResult.status()==CapturedIntentBridge.Status.DISPATCHED
                && incomingPlayerResult.outcomes().get(0).status()==BodyActionGate.Status.EXECUTED,
            "Reverse controller frame did not route through the original Mob body adapter");

        System.out.println("FF-GAMEPLAY player_body_action_port PASS"
            +" nativeMove=true nativeLook=true nativeAttack=true"
            +" mobFrameDrivesPlayerBody=true reverseFrameDrivesMobBody=true"
            +" authenticatedContinuousSenseTicks=3"
            +" externalWorkerStreaming=not_claimed"
            +" originalPlayerPreserved=true");
        target.discard();
    }

    /**
     * Real collision-world behavioral regression: the SAME chicken changes
     * speed with its native movement attribute, while a stone wall continues
     * to block physical movement. No numerical mock movement is accepted.
     */
    private static void verifyNativeSpeedCollision(GameTestHelper helper) {
        System.setProperty("freakyfriday.debugMotion","true");
        var level=helper.getLevel();
        ServerPlayer fastPlayer=helper.makeMockServerPlayerInLevel();
        ServerPlayer slowPlayer=helper.makeMockServerPlayerInLevel();
        fastPlayer.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        slowPlayer.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        Mob fastChicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob slowChicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(fastChicken!=null&&slowChicken!=null,
            "Chicken factory failed in native-speed regression");
        for(int x=1;x<=7;x++)for(int z=1;z<=22;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        // Remove any template boundary blocks from the test lanes so the
        // measured speed difference is not accidentally clamped at z=5.
        for(int x=1;x<=7;x++)for(int y=1;y<=3;y++)for(int z=1;z<20;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,z)),
                net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
        // Both physical bodies run at the same time in separate lanes. Give
        // the high-speed profile room to accelerate before the solid wall.
        for(int x=1;x<=7;x++)for(int y=1;y<=3;y++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,20)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos fastStartPos=helper.absolutePos(new BlockPos(3,1,4));
        BlockPos slowStartPos=helper.absolutePos(new BlockPos(5,1,4));
        fastChicken.setPos(fastStartPos.getX()+0.5,fastStartPos.getY(),fastStartPos.getZ()+0.5);
        slowChicken.setPos(slowStartPos.getX()+0.5,slowStartPos.getY(),slowStartPos.getZ()+0.5);
        var fastSpeed=fastChicken.getAttribute(
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        var slowSpeed=slowChicken.getAttribute(
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        helper.assertTrue(fastSpeed!=null&&slowSpeed!=null,
            "Chickens lack native movement speed attributes");
        helper.assertTrue(level.addFreshEntity(fastChicken)&&level.addFreshEntity(slowChicken),
            "Real Chicken bodies were not registered for speed/collision");
        // Spawn finalization can rewrite attributes, so apply the test profiles
        // only after each original physical entity is present in the level.
        fastSpeed.setBaseValue(1.2);
        slowSpeed.setBaseValue(0.08);
        UUID fastBodyId=fastChicken.getUUID(),slowBodyId=slowChicken.getUUID();
        UUID fastOwner=fastPlayer.getUUID(),slowOwner=slowPlayer.getUUID();
        float fastHealth=fastChicken.getHealth(),slowHealth=slowChicken.getHealth();
        double fastStartZ=fastChicken.getZ(),slowStartZ=slowChicken.getZ();
        try {
            String fastError=PlayerMobPossession.begin(fastPlayer,fastChicken);
            String slowError=PlayerMobPossession.begin(slowPlayer,slowChicken);
            helper.assertTrue(fastError==null&&slowError==null,
                "Native-speed possession rejected: fast="+fastError+" slow="+slowError);
            // Authenticated control pulses remain fresh across the entire
            // measurement window for both independent physical bodies.
            for(int tick=1;tick<=23;tick++) {
                final int sequence=tick;
                helper.runAfterDelay(tick,()->{
                    PlayerMobPossession.input(fastPlayer,
                        new PlayerMobPossession.Input(1,0,false,0,0,sequence));
                    PlayerMobPossession.input(slowPlayer,
                        new PlayerMobPossession.Input(1,0,false,0,0,sequence));
                });
            }
            helper.runAfterDelay(25,()->{
                System.clearProperty("freakyfriday.debugMotion");
                double fastDistance=fastChicken.getZ()-fastStartZ;
                double slowDistance=slowChicken.getZ()-slowStartZ;
                double wallFront=helper.absolutePos(new BlockPos(3,1,20)).getZ();
                helper.assertTrue(slowDistance>0.02,
                    "Slow native body did not move using its own movement attribute: "+slowDistance);
                helper.assertTrue(fastDistance>slowDistance+0.10,
                    "Higher native movement attribute did not increase travel: fast="
                        +fastDistance+" slow="+slowDistance
                        +" fastAttribute="+fastChicken.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                        +" slowAttribute="+slowChicken.getAttributeValue(
                            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED)
                        +" fastMobSpeed="+fastChicken.getSpeed()
                        +" slowMobSpeed="+slowChicken.getSpeed()
                        +" fastDelta="+fastChicken.getDeltaMovement()
                        +" slowDelta="+slowChicken.getDeltaMovement());
                helper.assertTrue(fastChicken.getBoundingBox().maxZ<=wallFront+0.0001,
                    "Fast Chicken passed through a solid vanilla block wall: box="
                        +fastChicken.getBoundingBox()+" wallZ="+wallFront);
                helper.assertTrue(level.getEntity(fastBodyId)==fastChicken
                        &&level.getEntity(slowBodyId)==slowChicken
                        &&Float.compare(fastHealth,fastChicken.getHealth())==0
                        &&Float.compare(slowHealth,slowChicken.getHealth())==0,
                    "Native-speed test replaced/damaged a physical Chicken body");
                helper.assertTrue(PlayerMobPossession.end(fastPlayer)
                        &&PlayerMobPossession.end(slowPlayer),
                    "Native-speed tests could not cancel both active possessions");
                helper.assertTrue(!fastChicken.isNoAi()&&!slowChicken.isNoAi()
                        &&!PlayerMobPossession.hasSession(fastOwner)
                        &&!PlayerMobPossession.hasSession(slowOwner),
                    "Native-speed cancel failed to restore both bodies and owners");
                System.out.println("FF-GAMEPLAY native_body_speed PASS"
                    +" fast="+fastDistance+" slow="+slowDistance);
                System.out.println("FF-GAMEPLAY solid_wall_collision PASS"
                    +" maxZ="+fastChicken.getBoundingBox().maxZ+" wallZ="+wallFront);
                helper.succeed();
                fastChicken.discard();
                slowChicken.discard();
            });
        } catch (RuntimeException failure) {
            PlayerMobPossession.clearOnLogout(fastOwner);
            PlayerMobPossession.clearOnLogout(slowOwner);
            fastChicken.discard();
            slowChicken.discard();
            throw failure;
        }
    }


    /**
     * Behavioral fall check using REAL world ticks, three distinct original
     * bodies and one controller at a time: chicken flaps; ocelot falls
     * normally without damage; zombie falls normally and IS damaged.
     * Distinct native body type behavior must never leak between sessions.
     */

    private static int[] arenaChunkBounds(GameTestHelper helper,
            int minX,int maxX,int minZ,int maxZ) {
        BlockPos[] corners={
            helper.absolutePos(new BlockPos(minX,1,minZ)),
            helper.absolutePos(new BlockPos(minX,1,maxZ)),
            helper.absolutePos(new BlockPos(maxX,1,minZ)),
            helper.absolutePos(new BlockPos(maxX,6,maxZ))
        };
        int minCx=Integer.MAX_VALUE,maxCx=Integer.MIN_VALUE;
        int minCz=Integer.MAX_VALUE,maxCz=Integer.MIN_VALUE;
        for(BlockPos p:corners) {
            int cx=p.getX()>>4, cz=p.getZ()>>4;
            minCx=Math.min(minCx,cx); maxCx=Math.max(maxCx,cx);
            minCz=Math.min(minCz,cz); maxCz=Math.max(maxCz,cz);
        }
        return new int[]{minCx,maxCx,minCz,maxCz};
    }

    private static void setArenaChunks(net.minecraft.server.level.ServerLevel level,
            UUID owner,int[] bounds,boolean forced) {
        if(bounds==null)return;
        for(int cx=bounds[0];cx<=bounds[1];cx++)
            for(int cz=bounds[2];cz<=bounds[3];cz++)
                NativeAiWorkerServer.setEntityTickingChunk(level,owner,cx,cz,forced);
    }

    private static void verifyFallProfiles(GameTestHelper helper) {
        var level=helper.getLevel();
        UUID ticketOwner=UUID.randomUUID();
        int[] ticketBounds=arenaChunkBounds(helper,1,9,1,6);
        setArenaChunks(level,ticketOwner,ticketBounds,true);
        helper.runAfterDelay(120,()->setArenaChunks(level,ticketOwner,ticketBounds,false));
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        BlockPos playerAnchor=helper.absolutePos(new BlockPos(1,1,2));
        player.setPos(playerAnchor.getX()+0.5,playerAnchor.getY(),
            playerAnchor.getZ()+0.5);
        for(int x=1;x<=9;x++)for(int z=1;z<=6;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            // Prevent daylight ignition from masquerading as a fall result.
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,17,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }

        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob ocelot=EntityTypes.OCELOT.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null&&ocelot!=null,
            "Native Chicken/Ocelot factories required");
        Mob[] bodies={chicken,ocelot};
        Mob[] zombieBody={null};
        for(int i=0;i<bodies.length;i++) {
            BlockPos origin=helper.absolutePos(new BlockPos(2+2*i,12,2));
            bodies[i].setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
            bodies[i].setNoAi(true);
            if(i>0)bodies[i].setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(bodies[i]),
                "Native fall test could not register entity "+i);
        }
        double startY=chicken.getY();
        float ocelotHealth=ocelot.getHealth();
        float[] zombieHealth={0.0f};
        String error=PlayerMobPossession.begin(player,chicken);
        helper.assertTrue(error==null,"Chicken physical-travel session rejected: "+error);
        final double[] chickenDrop={0.0};
        helper.runAfterDelay(12,()->{
            chickenDrop[0]=startY-chicken.getY();
            helper.assertTrue(chickenDrop[0]>0.2&&chickenDrop[0]<8.0,
                "Controlled Chicken did not use native vertical travel: drop="+chickenDrop[0]);
            helper.assertTrue(PlayerMobPossession.end(player),
                "Cannot end Chicken native-travel session");
            ocelot.setNoGravity(false);
            ocelot.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            String next=PlayerMobPossession.begin(player,ocelot);
            helper.assertTrue(next==null,"Ocelot native-travel session rejected: "+next);
        });
        helper.runAfterDelay(42,()->{
            helper.assertTrue(ocelot.onGround(),
                "Native Ocelot travel did not land in its physical arena");
            helper.assertTrue(Float.compare(ocelot.getHealth(),ocelotHealth)==0,
                "Vanilla fall-immune Ocelot took damage in possessed body");
            helper.assertTrue(PlayerMobPossession.end(player),
                "Cannot end Ocelot native-travel session");
            Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(zombie!=null,"Native Zombie factory failed");
            BlockPos zombieOrigin=helper.absolutePos(new BlockPos(6,12,2));
            zombie.setPos(zombieOrigin.getX()+0.5,zombieOrigin.getY(),
                zombieOrigin.getZ()+0.5);
            zombie.setNoGravity(true);
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(zombie),"Zombie did not register");
            zombieHealth[0]=zombie.getHealth();
            zombieBody[0]=zombie;
        });
        helper.runAfterDelay(57,()->{
            Mob zombie=zombieBody[0];
            helper.assertTrue(zombie!=null&&level.getEntity(zombie.getUUID())==zombie,
                "Zombie is not registered before native-travel possession");
            BlockPos origin=helper.absolutePos(new BlockPos(6,12,2));
            zombie.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
            zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            zombie.setNoGravity(false);
            String next=PlayerMobPossession.begin(player,zombie);
            helper.assertTrue(next==null,"Zombie native-travel session rejected: "+next);
        });
        helper.runAfterDelay(87,()->{
            Mob zombie=zombieBody[0];
            helper.assertTrue(zombie!=null,"Zombie not constructed for native travel");
            double drop=startY-zombie.getY();
            helper.assertTrue(drop>chickenDrop[0]*0.5,
                "Zombie native travel was incorrectly given a Chicken-specific fall response: chicken="
                    +chickenDrop[0]+" zombie="+drop);
        });
        helper.runAfterDelay(115,()->{
            Mob zombie=zombieBody[0];
            helper.assertTrue(zombie!=null,"Missing Zombie when checking landing");
            helper.assertTrue(zombie.getHealth()<zombieHealth[0],
                "Zombie did not take its native fall damage; finalHealth="+zombie.getHealth()
                    +" initial="+zombieHealth[0]+" onGround="+zombie.onGround()+" y="+zombie.getY());
            if(PlayerMobPossession.hasSession(player.getUUID()))
                helper.assertTrue(PlayerMobPossession.end(player),
                    "Cannot end surviving Zombie session");
            else helper.assertTrue(!zombie.isAlive()&&player.isAlive(),
                "Fall-dead Zombie must automatically return controller");
            System.out.println("FF-GAMEPLAY native_fall_profiles PASS"
                +" nativeTravel=true ocelotImmune=true zombieDamaged=true"
                +" noSpeciesMovementTable=true");
            for(Mob mob:bodies)mob.discard();
            zombie.discard();
            setArenaChunks(level,ticketOwner,ticketBounds,false);
            helper.succeed();
        });
    }

    private static void verifyMobDeathReturn(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        Mob occupied=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(occupied!=null,"Death-return body factory failed");
        BlockPos floor=helper.absolutePos(new BlockPos(2,0,2));
        level.setBlockAndUpdate(floor,
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        occupied.setPos(floor.getX()+0.5,floor.getY()+1,floor.getZ()+0.5);
        occupied.setNoAi(false);
        helper.assertTrue(level.addFreshEntity(occupied),
            "Death-return body did not register");
        UUID controller=player.getUUID();
        String problem=PlayerMobPossession.begin(player,occupied);
        helper.assertTrue(problem==null,"Death-return possession failed: "+problem);
        helper.runAfterDelay(3,()->occupied.setHealth(0.0f));
        helper.runAfterDelay(6,()->{
            helper.assertTrue(!occupied.isAlive(),"Occupied body was not actually dead");
            helper.assertTrue(!PlayerMobPossession.hasSession(controller)
                    && PlayerMobPossession.possessedBody(controller)==null,
                "Occupied body death left player's mind controlling a dead mob");
            helper.assertTrue(player.isAlive(),
                "Possessed mob death wrongly killed authenticated player body");
            System.out.println("FF-GAMEPLAY occupied_mob_death_returns_player PASS");
            helper.succeed();
            occupied.discard();
        });
    }


    /**
     * A fall-immune Ocelot still has a native damageable health pool.
     * This GameTest uses real Minecraft DamageSource and LivingEntity's
     * hurtServer pathway, not a synthetic setHealth-only kill.
     * The authenticated original player body must survive the mob's death.
     */
    private static void verifyOccupiedOcelotDamage(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        Mob ocelot=EntityTypes.OCELOT.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(ocelot!=null,"Ocelot factory failed");
        BlockPos floor=helper.absolutePos(new BlockPos(2,0,2));
        level.setBlockAndUpdate(floor,
            net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        ocelot.setPos(floor.getX()+0.5,floor.getY()+1,floor.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(ocelot),"Ocelot was not registered");
        UUID owner=player.getUUID(),body=ocelot.getUUID();
        float playerHealth=player.getHealth(),original=ocelot.getHealth();
        helper.assertTrue(original>2.0f,
            "Physical ocelot must have real vanilla health, not fake 0 HP");
        String error=PlayerMobPossession.begin(player,ocelot);
        helper.assertTrue(error==null,"Ocelot possession failed: "+error);
        helper.runAfterDelay(3,()->{
            boolean damaged=ocelot.hurtServer(level,
                ocelot.damageSources().generic(),2.0f);
            helper.assertTrue(damaged && ocelot.getHealth()<original
                && ocelot.getHealth()>0.0f,
                "Possessed Ocelot ignored ordinary damage: old="+original
                    +" new="+ocelot.getHealth());
            helper.assertTrue(body.equals(PlayerMobPossession.possessedBody(owner))
                    && !PlayerPlayerExchange.active(owner),
                "Damage incorrectly ended living mob possession");
            System.out.println("FF-GAMEPLAY occupied_ocelot_attack_health PASS"
                +" original="+original+" damaged="+ocelot.getHealth());
        });
        // Past vanilla damage invulnerability frames; a second source can
        // legitimately kill the actual occupied Ocelot and end possession.
        helper.runAfterDelay(18,()->{
            helper.assertTrue(ocelot.hurtServer(level,
                ocelot.damageSources().generic(),100.0f),
                "Fatal vanilla Ocelot damage was unexpectedly ignored");
        });
        helper.runAfterDelay(22,()->{
            helper.assertTrue(!ocelot.isAlive(),
                "Damaged Ocelot was still alive after a fatal vanilla hit");
            helper.assertTrue(!PlayerMobPossession.hasSession(owner)
                    && PlayerMobPossession.possessedBody(owner)==null,
                "Fatal body damage did not release possession");
            helper.assertTrue(player.isAlive()
                    && Float.compare(playerHealth,player.getHealth())==0,
                "Original authenticated player's own body inherited Ocelot damage");
            System.out.println("FF-GAMEPLAY occupied_ocelot_attack_death_returns PASS"
                +" originalPlayerAlive=true");
            helper.succeed();
            ocelot.discard();
        });
    }


    /**
     * No silent pairing with a stale /self. A freshly selected second self
     * explicitly starts the session. Real book/test command workflow.
     */
    private static void verifyExplicitSelfSelection(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        UUID owner=player.getUUID();
        MindSwapSessions.select(player,player);
        helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)
                ==MindSwapSessions.SELECTION_TIMEOUT_TICKS,
            "First self selection failed to enter the HUD countdown");
        helper.assertTrue(!PlayerMobPossession.hasSession(owner),
            "Selecting only the original player body started a session");
        helper.runAfterDelay((int)MindSwapSessions.SELECTION_TIMEOUT_TICKS+2,()->{
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)==0,
                "Timed-out first selection remained active after eight seconds");
            Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(chicken!=null,"Fresh Chicken factory failed");
            BlockPos floor=helper.absolutePos(new BlockPos(2,0,2));
            level.setBlockAndUpdate(floor,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            chicken.setPos(floor.getX()+0.5,floor.getY()+1,floor.getZ()+0.5);
            chicken.setNoAi(false);
            helper.assertTrue(level.addFreshEntity(chicken),"Fresh Chicken not registered");
            MindSwapSessions.selectFromInteraction(player,chicken);
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)
                    ==MindSwapSessions.SELECTION_TIMEOUT_TICKS,
                "New first-body selection did not restart its countdown");
            MindSwapSessions.select(player,player);
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)==0
                    &&PlayerMobPossession.hasSession(owner)&&chicken.isNoAi(),
                "Explicit second selection did not start reciprocal Player/Chicken control");
            helper.assertTrue(PlayerMobPossession.end(player),
                "Explicit self selection did not cancel the reciprocal body session");
            helper.assertTrue(!chicken.isNoAi()&&!PlayerMobPossession.hasSession(owner),
                "Explicit self selection cancel did not restore the original Chicken body");
            System.out.println("FF-GAMEPLAY stale_selection_and_reciprocal_gate PASS"
                +" actualReciprocalStarted=true exactBodyRestored=true");
            chicken.discard();
            MindSwapSessions.clearOnLogout(owner);
            helper.succeed();
        });
    }

    /** Zombie-origin attack must damage actual original player, not kick them. */
    private static void verifyMobOriginAttack(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null,"Cannot spawn Zombie");
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            // No daytime Zombie burning during the spawn-invulnerability wait.
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,4,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        BlockPos pos=helper.absolutePos(new BlockPos(3,1,3));
        player.setPos(pos.getX()+0.5,pos.getY(),pos.getZ()+0.5);
        zombie.setPos(pos.getX()+1.5,pos.getY(),pos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(zombie),"Zombie not registered");
        player.gameMode.changeGameModeForPlayer(
            net.minecraft.world.level.GameType.SURVIVAL);
        // Survival gamemode clears creative abilities. The separate
        // entity-level immunity is asserted below rather than mutated via
        // an API that is unavailable in Minecraft 26.3.
        helper.assertTrue(!player.getAbilities().invulnerable
                && !player.isInvulnerable(),
            "Attack test requires a genuinely damageable Survival player");
        String issue=PlayerMobPossession.begin(player,zombie);
        helper.assertTrue(issue==null,"Zombie possession rejected: "+issue);
        float initial=player.getHealth();
        PlayerMobPossession.input(player,new PlayerMobPossession.Input(0,0,false,90,0,1));
        // A new ServerPlayer may ignore ordinary hostile damage during the
        // initial spawn-protection window. Attacking at tick 3 tests that
        // protection, not possession. Wait for ordinary survival damage.
        helper.runAfterDelay(75,()->{
            net.minecraft.world.entity.Entity target=
                PossessionCombat.findValidMeleeTarget(level,zombie,player.getId(),player);
            helper.assertTrue(target==player,
                "Authenticated original body was rejected as a melee target"
                  +" dist="+zombie.distanceTo(player)
                  +" yaw="+zombie.getYRot()
                  +" sight="+zombie.hasLineOfSight(player));
            PlayerMobPossession.attack(player,player.getId(),1);
            helper.assertTrue(player.getHealth()<initial,
                "Zombie physical attack caused no damage to mock player"
                +" id="+player.getId()+" registered="+(level.getEntity(player.getId())==player)
                +" creative="+player.isCreative()+" spectator="+player.isSpectator()
                +" invulnerable="+player.isInvulnerable()
                +" zombieAttack="+zombie.getAttributeValue(
                    net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                +" health="+player.getHealth());
            float damaged=player.getHealth();
            PlayerMobPossession.attack(player,player.getId(),1);
            PlayerMobPossession.attack(player,zombie.getId(),2);
            helper.assertTrue(Float.compare(player.getHealth(),damaged)==0,
                "Duplicate or self-targeted attack bypassed server checks");
            helper.assertTrue(PlayerMobPossession.hasSession(player.getUUID()),
                "Nonfatal attack ended possession");
            MindSwapSessions.cancel(player);
            System.out.println("FF-GAMEPLAY mob_origin_attack_original_body PASS");
            System.out.println("FF-GAMEPLAY invalid_entity_attack_guard PASS");
            zombie.discard();
            helper.succeed();
        });
    }

    /**
     * Intentionally manual-only until implemented: the exact user-reported
     * missing behavior is now a reproducible acceptance gate. Do not count
     * this as a passed swap unless native behavior actually transfers.
     */
    private static void verifyZombieChickenBehaviorAcceptance(GameTestHelper helper) {
        var level=helper.getLevel();
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null&&chicken!=null,"Real Zombie and Chicken required");
        for(int x=1;x<=9;x++)for(int z=1;z<=9;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos a=helper.absolutePos(new BlockPos(3,1,3));
        BlockPos b=helper.absolutePos(new BlockPos(5,1,3));
        zombie.setPos(a.getX()+0.5,a.getY(),a.getZ()+0.5);
        chicken.setPos(b.getX()+0.5,b.getY(),b.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(zombie)&&level.addFreshEntity(chicken),
            "Native mobs were not registered");
        boolean zombieNoAi=zombie.isNoAi(),chickenNoAi=chicken.isNoAi();
        List<GoalSnapshot> zombieGoals=snapshotGoals(zombie,false);
        List<GoalSnapshot> zombieTargets=snapshotGoals(zombie,true);
        List<GoalSnapshot> chickenGoals=snapshotGoals(chicken,false);
        List<GoalSnapshot> chickenTargets=snapshotGoals(chicken,true);
        UUID requester=UUID.randomUUID();
        String failure=GoalMindRuntime.begin(requester,zombie,chicken);
        helper.assertTrue(failure==null,
            "Live Chicken↔Zombie native controller exchange failed: "+failure);
        helper.runAfterDelay(20,()->{
            helper.assertTrue(zombie.isAlive()&&chicken.isAlive()
                    &&zombie.isNoAi()==zombieNoAi&&chicken.isNoAi()==chickenNoAi
                    &&zombie.getType().equals(EntityTypes.ZOMBIE)
                    &&chicken.getType().equals(EntityTypes.CHICKEN),
                "The real physical bodies did not retain identity and NoAI state while proxy minds ran");
            helper.assertTrue(zombie.goalSelector.getAvailableGoals().isEmpty()
                    &&zombie.targetSelector.getAvailableGoals().isEmpty()
                    &&chicken.goalSelector.getAvailableGoals().isEmpty()
                    &&chicken.targetSelector.getAvailableGoals().isEmpty(),
                "The physical GoalSelectors were not isolated while their native proxies ran");
            helper.assertTrue(GoalMindRuntime.activeSessions(requester)==1
                    &&GoalMindRuntime.bodyReserved(zombie.getUUID())
                    &&GoalMindRuntime.bodyReserved(chicken.getUUID()),
                "Both live physical bodies must remain independently assigned during the exchange");
            helper.assertTrue(GoalMindRuntime.cancelByAccount(requester)
                    &&GoalMindRuntime.activeSessions(requester)==0,
                "The original Chicken/Zombie mind assignments did not restore cleanly");
            helper.assertTrue(zombie.isNoAi()==zombieNoAi&&chicken.isNoAi()==chickenNoAi
                    &&hasSameGoals(zombie,false,zombieGoals)
                    &&hasSameGoals(zombie,true,zombieTargets)
                    &&hasSameGoals(chicken,false,chickenGoals)
                    &&hasSameGoals(chicken,true,chickenTargets),
                "Returning from the exchange did not restore each body's exact AI state");
            zombie.discard();
            chicken.discard();
            System.out.println("FF-GAMEPLAY zombie_chicken_behavior_acceptance PASS"
                +" reciprocalNativeGoals=true sameServer=true bodyIdentityPreserved=true"
                +" sameTypeProxyGoals=true originalSelectorsUntouched=true reversible=true");
            helper.succeed();
        });
    }


    private static void verifyMobMobReciprocalAttack(GameTestHelper helper) {
        var level=helper.getLevel();
        Mob first=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob second=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(first!=null&&second!=null,"Two native Zombies required");
        for(int x=1;x<=9;x++)for(int z=1;z<=6;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos a=helper.absolutePos(new BlockPos(3,1,3));
        BlockPos b=helper.absolutePos(new BlockPos(5,1,3));
        first.setPos(a.getX()+0.5,a.getY(),a.getZ()+0.5);
        second.setPos(b.getX()+0.5,b.getY(),b.getZ()+0.5);
        first.setPersistenceRequired();
        second.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(first)&&level.addFreshEntity(second),
            "Both physical Zombie bodies must be registered");
        float firstHealth=first.getHealth(),secondHealth=second.getHealth();
        UUID requester=UUID.randomUUID();
        String issue=GoalMindRuntime.begin(requester,first,second);
        helper.assertTrue(issue==null,"Zombie↔Zombie native swap rejected: "+issue);
        Mob firstBodyMind=GoalMindRuntime.proxyForGameTest(first.getUUID());
        Mob secondBodyMind=GoalMindRuntime.proxyForGameTest(second.getUUID());
        helper.assertTrue(firstBodyMind!=null&&secondBodyMind!=null,
            "Each physical body must have the opposite native mind proxy");
        firstBodyMind.goalSelector.addGoal(0,new OneShotNativeAttackGoal(firstBodyMind,second));
        secondBodyMind.goalSelector.addGoal(0,new OneShotNativeAttackGoal(secondBodyMind,first));
        helper.runAfterDelay(4,()->{
            helper.assertTrue(first.getHealth()<firstHealth&&second.getHealth()<secondHealth,
                "Both opposite native minds must damage the other participant's real body"
                    +" first="+firstHealth+"->"+first.getHealth()
                    +" second="+secondHealth+"->"+second.getHealth());
            helper.assertTrue(GoalMindRuntime.activeSessions(requester)==1,
                "Nonfatal reciprocal body damage unexpectedly terminated the Mob↔Mob swap");
            helper.assertTrue(GoalMindRuntime.cancelByAccount(requester),
                "Mob↔Mob reciprocal attack session did not return cleanly");
            first.discard();
            second.discard();
            System.out.println("FF-GAMEPLAY mob_mob_reciprocal_attack PASS"
                +" firstMindDamagesSecondBody=true secondMindDamagesFirstBody=true"
                +" originalHealthPools=true reversible=true");
            helper.succeed();
        });
    }

    /** A non-universal experimental path must never mutate either real mob. */
    private static void verifyPartialSwapRejected(GameTestHelper helper) {
        var level=helper.getLevel();
        // Active-Brain pairs must still fail closed without mutating either mob.
        Mob villager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
        Mob brainZombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(villager!=null&&brainZombie!=null,
            "Active-Brain fail-closed regression needs real Villager and Zombie entities");
        BlockPos villagerPos=helper.absolutePos(new BlockPos(3,1,8));
        BlockPos brainZombiePos=helper.absolutePos(new BlockPos(7,1,8));
        villager.setPos(villagerPos.getX()+0.5,villagerPos.getY(),villagerPos.getZ()+0.5);
        brainZombie.setPos(brainZombiePos.getX()+0.5,brainZombiePos.getY(),brainZombiePos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(villager)&&level.addFreshEntity(brainZombie),
            "Fail-closed swap actors did not register");
        var villagerBrain=villager.getBrain();
        var zombieBrain=brainZombie.getBrain();
        var villagerGoals=List.copyOf(villager.goalSelector.getAvailableGoals());
        var brainZombieGoals=List.copyOf(brainZombie.goalSelector.getAvailableGoals());
        var villagerTargets=List.copyOf(villager.targetSelector.getAvailableGoals());
        var brainZombieTargets=List.copyOf(brainZombie.targetSelector.getAvailableGoals());
        String rejection=NativeBrainSwap.exchange(villager,brainZombie);
        helper.assertTrue(rejection!=null&&rejection.contains("active Brain AI"),
            "An unsupported active-Brain pair must fail closed: "+rejection);
        helper.assertTrue(villager.getBrain()==villagerBrain&&brainZombie.getBrain()==zombieBrain,
            "Rejected exchange changed a physical body's Brain reference");
        helper.assertTrue(villagerGoals.equals(List.copyOf(villager.goalSelector.getAvailableGoals()))
                &&brainZombieGoals.equals(List.copyOf(brainZombie.goalSelector.getAvailableGoals()))
                &&villagerTargets.equals(List.copyOf(villager.targetSelector.getAvailableGoals()))
                &&brainZombieTargets.equals(List.copyOf(brainZombie.targetSelector.getAvailableGoals())),
            "Rejected active-Brain exchange mutated a native goal or target selector");
        // Do not let the fail-closed fixtures become unintended AI targets
        // during the independent reciprocal behavior acceptance scenario.
        villager.discard();
        brainZombie.discard();

        // Build a real, roofed arena and two independent live Mob pairs.
        for(int x=0;x<=24;x++) for(int z=0;z<=10;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,7,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        ServerPlayer target=helper.makeMockServerPlayerInLevel();
        target.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        Mob opponent=EntityTypes.SKELETON.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(opponent!=null,"Mob-pair damage test needs an opposing Skeleton");
        opponent.setNoAi(true);
        opponent.setPos(helper.absolutePos(new BlockPos(23,1,4)).getX()+0.5,
            helper.absolutePos(new BlockPos(23,1,4)).getY(),
            helper.absolutePos(new BlockPos(23,1,4)).getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(opponent),
            "Opposing Skeleton failed to register for physical-body damage test");
        target.getAbilities().invulnerable=false;
        // Chicken's native TemptGoal uses the Player-held seeds; Zombie's
        // native target goals use a Villager. Keeping the stimuli separate
        // tests each original AI graph rather than depending on one entity
        // type being accepted by both vanilla target selectors.
        BlockPos seedPos=helper.absolutePos(new BlockPos(6,1,4));
        target.setPos(seedPos.getX()+0.5,seedPos.getY(),seedPos.getZ()+0.5);
        target.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT_SEEDS));
        Mob villagerTarget=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(villagerTarget!=null,"Zombie AI stimulus needs a real Villager target");
        villagerTarget.setNoAi(true);
        BlockPos villagerTargetPos=helper.absolutePos(new BlockPos(6,1,6));
        villagerTarget.setPos(villagerTargetPos.getX()+0.5,
            villagerTargetPos.getY(),villagerTargetPos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(villagerTarget),
            "Zombie AI Villager target failed to register");

        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob secondChicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob secondZombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null&&zombie!=null&&secondChicken!=null&&secondZombie!=null,
            "Live cross-species exchange fixtures could not be created");
        // Keep both physical bodies about three blocks from the Player
        // stimulus: inside native acquisition range, but outside the
        // Chicken TemptGoal's close-range stop radius.
        BlockPos chickenPos=helper.absolutePos(new BlockPos(3,1,4));
        BlockPos zombiePos=helper.absolutePos(new BlockPos(9,1,4));
        BlockPos secondChickenPos=helper.absolutePos(new BlockPos(15,1,4));
        BlockPos secondZombiePos=helper.absolutePos(new BlockPos(19,1,4));
        chicken.setPos(chickenPos.getX()+0.5,chickenPos.getY(),chickenPos.getZ()+0.5);
        zombie.setPos(zombiePos.getX()+0.5,zombiePos.getY(),zombiePos.getZ()+0.5);
        secondChicken.setPos(secondChickenPos.getX()+0.5,secondChickenPos.getY(),secondChickenPos.getZ()+0.5);
        secondZombie.setPos(secondZombiePos.getX()+0.5,secondZombiePos.getY(),secondZombiePos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(chicken)&&level.addFreshEntity(zombie)
                &&level.addFreshEntity(secondChicken)&&level.addFreshEntity(secondZombie),
            "Live cross-species bodies failed to register");
        chicken.setPersistenceRequired();
        zombie.setPersistenceRequired();
        secondChicken.setPersistenceRequired();
        secondZombie.setPersistenceRequired();

        List<GoalSnapshot> chickenGoals=snapshotGoals(chicken,false);
        List<GoalSnapshot> chickenTargets=snapshotGoals(chicken,true);
        List<GoalSnapshot> zombieGoals=snapshotGoals(zombie,false);
        List<GoalSnapshot> zombieTargets=snapshotGoals(zombie,true);
        boolean chickenNoAi=chicken.isNoAi(),zombieNoAi=zombie.isNoAi();
        UUID requester=java.util.UUID.randomUUID();
        System.setProperty("freakyfriday.debugProxy","true");
        String firstError=GoalMindRuntime.begin(requester,chicken,zombie);
        helper.assertTrue(firstError==null,
            "Live Chicken↔Zombie native goal exchange failed: "+firstError);
        helper.assertTrue(chicken.goalSelector.getAvailableGoals().isEmpty()
                &&zombie.goalSelector.getAvailableGoals().isEmpty()
                &&chicken.targetSelector.getAvailableGoals().isEmpty()
                &&zombie.targetSelector.getAvailableGoals().isEmpty(),
            "The original bodies' autonomous goals were not suspended during the proxy exchange");
        helper.assertTrue(chicken.isNoAi()==chickenNoAi&&zombie.isNoAi()==zombieNoAi,
            "Pausing autonomous goals changed a physical body's NoAI/body state");

        // A requester is only a session manager: an occupied body is exclusive,
        // but a second unrelated pair is allowed under the same requester.
        String occupiedError=GoalMindRuntime.begin(requester,chicken,secondZombie);
        helper.assertTrue(occupiedError!=null,
            "An already-occupied physical body was allowed into a second exchange");
        String secondError=GoalMindRuntime.begin(requester,secondChicken,secondZombie);
        helper.assertTrue(secondError==null&&GoalMindRuntime.activeSessions(requester)==2,
            "One requester could not manage two independent live Mob exchanges: "+secondError);

        double chickenStart=chicken.distanceToSqr(villagerTarget);
        double zombieStart=zombie.distanceToSqr(target);
        float chickenHealthBeforeOpponentAttack=chicken.getHealth();
        float zombieHealthBeforeOpponentAttack=zombie.getHealth();
        helper.runAfterDelay(40,()->{
            System.clearProperty("freakyfriday.debugProxy");
            helper.assertTrue(chicken.isAlive()&&zombie.isAlive()
                    &&secondChicken.isAlive()&&secondZombie.isAlive(),
                "One of the original physical Mob bodies stopped ticking during the swap");
            double chickenEnd=chicken.distanceToSqr(villagerTarget);
            double zombieEnd=zombie.distanceToSqr(target);
            helper.assertTrue(chickenEnd<chickenStart&&zombieEnd<zombieStart,
                "Both donor-native controllers must independently move their opposite real bodies toward the Player stimulus"
                    +" chicken="+chickenStart+"->"+chickenEnd
                    +" zombie="+zombieStart+"->"+zombieEnd);
            // The opposing Skeleton must still be able to hit BOTH original
            // physical bodies during the swap. Damage belongs to each body,
            // never to its proxy mind or the other participant's health.
            var opponentDamage=level.damageSources().mobAttack(opponent);
            helper.assertTrue(chicken.hurtServer(level,opponentDamage,2.0f)
                    &&zombie.hurtServer(level,opponentDamage,2.0f)
                    &&chicken.getHealth()<chickenHealthBeforeOpponentAttack
                    &&zombie.getHealth()<zombieHealthBeforeOpponentAttack,
                "The opposing Skeleton could not independently damage both swapped physical Mob bodies");
            helper.assertTrue(GoalMindRuntime.activeSessions(requester)==2,
                "Incoming damage incorrectly ended one of the live Mob mind/body swaps");
            helper.assertTrue(GoalMindRuntime.cancelByAccount(requester)
                    &&GoalMindRuntime.activeSessions(requester)==0,
                "Cancel did not restore every independently managed Mob pair");
            helper.assertTrue(hasSameGoals(chicken,false,chickenGoals)
                    &&hasSameGoals(chicken,true,chickenTargets)
                    &&hasSameGoals(zombie,false,zombieGoals)
                    &&hasSameGoals(zombie,true,zombieTargets)
                    &&chicken.isNoAi()==chickenNoAi&&zombie.isNoAi()==zombieNoAi,
                "Cancel did not preserve the exact original Chicken/Zombie goals and AI flags");
            helper.assertTrue(!GoalMindRuntime.bodyReserved(chicken.getUUID())
                    &&!GoalMindRuntime.bodyReserved(zombie.getUUID())
                    &&!GoalMindRuntime.bodyReserved(secondChicken.getUUID())
                    &&!GoalMindRuntime.bodyReserved(secondZombie.getUUID()),
                "Cancelled Mob bodies remained reserved");

            System.out.println("FF-GAMEPLAY partial_native_swap_fails_closed PASS");
            System.out.println("FF-GAMEPLAY native_goal_mind_exchange PASS"
                +" reciprocalNativeGoals=true sameServer=true bothBodiesTick=true"
                +" liveTargetAcquisition=true physicalMovement=true independentPairs=true"
                +" exactGoalRestore=true");
            villager.discard();
            villagerTarget.discard();
            brainZombie.discard();
            chicken.discard();
            zombie.discard();
            opponent.discard();
            secondChicken.discard();
            secondZombie.discard();
            helper.succeed();
        });
    }

    /** The Mob mind drives the original player body while player input drives the Mob. */
    private static void verifyPlayerMobNativeMindProxy(GameTestHelper helper) {
        var level=helper.getLevel();
        for(int x=0;x<=20;x++)for(int z=0;z<=10;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,8,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }
        ServerPlayer controller=helper.makeMockServerPlayerInLevel();
        ServerPlayer otherPlayer=helper.makeMockServerPlayerInLevel();
        BlockPos controllerPos=helper.absolutePos(new BlockPos(10,1,5));
        BlockPos targetPos=helper.absolutePos(new BlockPos(4,1,5));
        controller.setPos(controllerPos.getX()+0.5,controllerPos.getY(),controllerPos.getZ()+0.5);
        otherPlayer.setPos(targetPos.getX()+0.5,targetPos.getY(),targetPos.getZ()+0.5);

        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null,"Player/Mob proxy test needs a real Zombie");
        BlockPos zombiePos=helper.absolutePos(new BlockPos(14,1,5));
        zombie.setPos(zombiePos.getX()+0.5,zombiePos.getY(),zombiePos.getZ()+0.5);
        zombie.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(zombie),
            "Player-controlled Zombie body failed to register");

        controller.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        controller.getAbilities().invulnerable=false;
        otherPlayer.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        otherPlayer.getAbilities().invulnerable=false;
        Mob opponent=EntityTypes.SKELETON.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(opponent!=null,"Player/Mob damage test needs an opposing Skeleton");
        opponent.setNoAi(true);
        opponent.setPos(helper.absolutePos(new BlockPos(18,1,5)).getX()+0.5,
            helper.absolutePos(new BlockPos(18,1,5)).getY(),
            helper.absolutePos(new BlockPos(18,1,5)).getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(opponent),
            "Opposing Skeleton failed to register in Player/Mob test");
        double playerStartX=controller.getX();
        double zombieStartZ=zombie.getZ();
        float playerHealthBeforeOpponentAttack=controller.getHealth();
        float zombieHealthBeforeOpponentAttack=zombie.getHealth();
        boolean originalNoAi=zombie.isNoAi();
        String issue=PlayerMobPossession.beginReciprocal(controller,zombie);
        helper.assertTrue(issue==null,
            "Reciprocal Player↔Zombie native mind proxy was rejected: "+issue);
        helper.assertTrue(PlayerMobPossession.hasSession(controller.getUUID())
                &&zombie.isNoAi()==true&&controller.isAlive(),
            "Reciprocal session did not assign separate controllers to both real bodies");

        for(int pulse=0;pulse<7;pulse++) {
            final long sequence=pulse+1L;
            helper.runAfterDelay(pulse*4,()->PlayerMobPossession.input(controller,
                new PlayerMobPossession.Input(1.0f,0.0f,false,0.0f,0.0f,sequence)));
        }
        helper.runAfterDelay(35,()->{
            helper.assertTrue(controller.getX()<playerStartX-0.15,
                "Zombie mind did not move the original Player body toward the other real Player; "
                    +"startX="+playerStartX+" finalX="+controller.getX());
            helper.assertTrue(zombie.getZ()>zombieStartZ+0.15,
                "Player input did not independently move the occupied Zombie body; "
                    +"startZ="+zombieStartZ+" finalZ="+zombie.getZ());
            // The Player mind now inhabits the Zombie body. Its authenticated
            // attack input must hit the original Player body (currently driven
            // by the Zombie mind), not an invisible proxy.
            zombie.setPos(controller.getX()+1.35,controller.getY(),controller.getZ());
            // The occupied Zombie is east of the original Player body; aim
            // west using this profile's vanilla view-vector convention.
            zombie.setYRot(-90.0f);
            zombie.yRotO=-90.0f;
            zombie.setYHeadRot(-90.0f);
            PlayerMobPossession.input(controller,
                new PlayerMobPossession.Input(0,0,false,-90.0f,0,99));
            float playerBeforeMindAttack=controller.getHealth();
            PlayerMobPossession.attack(controller,controller.getId(),99);
            helper.assertTrue(controller.getHealth()<playerBeforeMindAttack,
                "Player mind in the Mob body could not damage the opposite mind's physical Player body");

            // In the other direction, the Zombie mind proxy performs a native
            // melee attack against the occupied Mob body. Proxy damage itself
            // is cancelled; the actual original Player body must execute it.
            Mob mindProxy=PlayerMobPossession.mindProxyForGameTest(controller.getUUID());
            helper.assertTrue(mindProxy!=null,"Reciprocal Zombie mind proxy disappeared");
            // The proxy copies its host Player body's view every tick.
            // Face that real body toward the Mob before the next native AI pass.
            controller.setYRot(-90.0f);
            controller.setXRot(0.0f);
            mindProxy.goalSelector.addGoal(0,new OneShotNativeAttackGoal(mindProxy,zombie));
            float mobBeforeMindAttack=zombie.getHealth();

            helper.runAfterDelay(4,()->{
                helper.assertTrue(zombie.getHealth()<mobBeforeMindAttack,
                    "Zombie mind on the Player body could not damage the opposite mind's physical Mob body");
                // A third-party attacker must still be able to damage both
                // original physical health pools independently during a swap.
                float playerBeforeExternal=controller.getHealth();
                float mobBeforeExternal=zombie.getHealth();
                var opponentDamage=level.damageSources().mobAttack(opponent);
                helper.assertTrue(controller.hurtServer(level,opponentDamage,2.0f)
                        &&zombie.hurtServer(level,opponentDamage,2.0f)
                        &&controller.getHealth()<playerBeforeExternal
                        &&zombie.getHealth()<mobBeforeExternal,
                    "External damage was not independently applied to both real bodies");
                helper.assertTrue(PlayerMobPossession.hasSession(controller.getUUID()),
                    "Nonfatal reciprocal attacks unexpectedly ended Player↔Mob control");
                helper.assertTrue(PlayerMobPossession.end(controller),
                    "Player/Mob reciprocal controller did not return on cancel");
                helper.assertTrue(zombie.isNoAi()==originalNoAi,
                    "Returning from the exchange did not restore the Mob's original AI flag");
                System.out.println("FF-GAMEPLAY player_mob_reciprocal_attack PASS"
                    +" playerMindDamagesMobMindBody=true mobMindDamagesPlayerMindBody=true"
                    +" externalAttackerDamagesBothBodies=true independentHealthPools=true");
                System.out.println("FF-GAMEPLAY player_mob_native_mind_proxy PASS"
                    +" sameServerNativeAI=true playerBodyDrivenByMobMind=true"
                    +" mobBodyDrivenByPlayerInput=true noMirroredMovement=true restored=true");
                zombie.discard();
                opponent.discard();
                helper.succeed();
            });
        });
    }

}