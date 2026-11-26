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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
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

    /**
     * GameTest's mock player factory may leave the entity in creative ability
     * state even after setGameMode(SURVIVAL). Make the real Player body
     * damageable and give its native melee attack a full cooldown, so these
     * tests exercise combat routing rather than mock-player setup artifacts.
     */
    private static void makeDamageableSurvivalPlayer(ServerPlayer player) {
        player.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        player.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.SURVIVAL);
        player.getAbilities().invulnerable=false;
        player.getAbilities().instabuild=false;
        player.getAbilities().mayBuild=true;
        boolean primed=false;
        for(Class<?> type=player.getClass();type!=null;type=type.getSuperclass()) {
            for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                String name=field.getName().toLowerCase(java.util.Locale.ROOT);
                if(field.getType()!=int.class||!name.contains("attackstrengthticker"))continue;
                try {
                    if(!field.trySetAccessible())continue;
                    field.setInt(player,100);
                    primed=true;
                } catch(ReflectiveOperationException|RuntimeException ignored) {
                    // Continue searching mapped superclass fields.
                }
            }
        }
        if(!primed)throw new IllegalStateException(
            "Could not prime native Player melee cooldown for reciprocal combat GameTest");
        if(player.isSpectator()||player.isInvulnerable()
                ||player.getAbilities().invulnerable
                ||player.getAbilities().instabuild
                ||player.getAttackStrengthScale(0.5f)<0.9f)
            throw new IllegalStateException(
                "Mock Player did not enter damageable Survival/full melee state"
                +" creative="+player.isCreative()
                +" spectator="+player.isSpectator()
                +" invulnerable="+player.isInvulnerable()
                +" abilitiesInvulnerable="+player.getAbilities().invulnerable
                +" instabuild="+player.getAbilities().instabuild
                +" attackScale="+player.getAttackStrengthScale(0.5f)
                +" gameMode="+player.gameMode.getGameModeForPlayer()
                +" class="+player.getClass().getName());
    }

    /** Clear the vanilla damage immunity timer across mapped server versions. */
    private static void clearHurtCooldown(LivingEntity body) {
        int cleared=0;
        for(Class<?> type=body.getClass();type!=null;type=type.getSuperclass()) {
            for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                String name=field.getName().toLowerCase(java.util.Locale.ROOT);
                // 26.3 renamed the vanilla hurt timer to a floating-point
                // damageCooldownTime field. Include that mapped storage as
                // well as the older int invulnerability/hurt timers. This is
                // only called by GameTest fixtures, never by production combat.
                boolean timer=name.contains("invulnerab")||name.contains("hurt")
                    ||name.contains("damagecooldown");
                if(!timer)continue;
                try {
                    if(!field.trySetAccessible())continue;
                    if(field.getType()==int.class) {
                        field.setInt(body,0);
                        cleared++;
                    } else if(field.getType()==float.class) {
                        field.setFloat(body,0.0f);
                        cleared++;
                    } else if(field.getType()==double.class) {
                        field.setDouble(body,0.0d);
                        cleared++;
                    }
                } catch(ReflectiveOperationException|RuntimeException ignored) {
                    // Continue searching mapped superclass fields.
                }
            }
        }
        if(cleared==0)throw new IllegalStateException(
            "Could not clear vanilla invulnerability cooldown for "+body.getType());
    }

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
            // This is a one-shot acceptance stimulus, not an attack-cooldown
            // test. Other native controllers can legitimately touch the same
            // target between fixture setup and this scheduled goal tick, so
            // clear the target's vanilla damage-immunity window at the exact
            // hit tick. Production combat never clears or bypasses cooldowns.
            clearHurtCooldown(target);
            float before=target.getHealth();
            boolean accepted=attacker.doHurtTarget((ServerLevel)attacker.level(),target);
            System.out.println("[FF-ONESHOT-ATTACK] attacker="+attacker.getType()
                +" attackerId="+attacker.getUUID()
                +" target="+target.getType()
                +" targetId="+target.getUUID()
                +" accepted="+accepted
                +" health="+before+"->"+target.getHealth()
                +" distanceSq="+attacker.distanceToSqr(target)
                +" attackerNoAi="+attacker.isNoAi()
                +" targetNoAi="+(target instanceof Mob targetMob&&targetMob.isNoAi()));
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

    private static String targetProbe(net.minecraft.server.level.ServerLevel level,
                                      Mob proxy,LivingEntity target) {
        double follow=proxy.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.FOLLOW_RANGE);
        boolean conditions=net.minecraft.world.entity.ai.targeting.TargetingConditions
            .forCombat().range(follow).test(level,proxy,target);
        boolean playerList=target instanceof net.minecraft.world.entity.player.Player player
            &&level.players().contains(player);
        boolean entityQuery=level.getEntitiesOfClass(
            target.getClass(),proxy.getBoundingBox().inflate(follow),entity->true).contains(target);
        Vec3 from=proxy.getEyePosition(),to=target.getEyePosition();
        var ray=level.clip(new net.minecraft.world.level.ClipContext(from,to,
            net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE,proxy));
        String rayBlock=ray.getType()==net.minecraft.world.phys.HitResult.Type.MISS
            ?"MISS":level.getBlockState(ray.getBlockPos()).toString();
        String effectiveAi="unknown";
        try {
            var method=proxy.getClass().getMethod("isEffectiveAi");
            effectiveAi=String.valueOf(method.invoke(proxy));
        } catch(ReflectiveOperationException ignored) {}
        String actualGoalConditions="none";
        for(WrappedGoal wrapped:proxy.targetSelector.getAvailableGoals()) {
            Goal goal=wrapped.getGoal();
            if(!goal.getClass().getSimpleName().contains("NearestAttackableTargetGoal"))continue;
            boolean matchesTargetType=false;
            for(Class<?> type=goal.getClass();type!=null;type=type.getSuperclass()) {
                for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                    if(!field.getName().equals("targetType"))continue;
                    try {
                        if(!field.trySetAccessible())continue;
                        Object value=field.get(goal);
                        matchesTargetType=value instanceof Class<?> targetClass
                            &&targetClass.isAssignableFrom(target.getClass());
                    } catch(ReflectiveOperationException|RuntimeException ignored) {}
                }
            }
            if(!matchesTargetType)continue;
            for(Class<?> type=goal.getClass();type!=null;type=type.getSuperclass()) {
                for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                    if(!field.getType().getName().equals(
                            "net.minecraft.world.entity.ai.targeting.TargetingConditions"))continue;
                    try {
                        if(!field.trySetAccessible())continue;
                        Object conditionsObject=field.get(goal);
                        var method=conditionsObject.getClass().getMethod(
                            "test",net.minecraft.server.level.ServerLevel.class,
                            LivingEntity.class,LivingEntity.class);
                        actualGoalConditions=String.valueOf(method.invoke(
                            conditionsObject,level,proxy,target));
                    } catch(ReflectiveOperationException|RuntimeException failure) {
                        actualGoalConditions=failure.getClass().getSimpleName();
                    }
                }
            }
        }
        return "target="+target.getType()+" follow="+follow
            +" actualGoalConditions="+actualGoalConditions
            +" targeterCanAttack="+proxy.canAttack(target)
            +" targetVisible="+target.canBeSeenByAnyone()
            +" los="+proxy.getSensing().hasLineOfSight(target)
            +" conditions="+conditions
            +" ray="+ray.getType()+" rayBlock="+rayBlock
            +" effectiveAI="+effectiveAi+" noAI="+proxy.isNoAi()
            +" from="+from+" to="+to
            +" playerList="+playerList+" entityQuery="+entityQuery;
    }

    private static String targetGoalOwners(Mob proxy) {
        StringBuilder result=new StringBuilder();
        for(WrappedGoal wrapped:proxy.targetSelector.getAvailableGoals()) {
            Goal goal=wrapped.getGoal();
            if(!goal.getClass().getSimpleName().contains("NearestAttackableTargetGoal"))continue;
            Object owner=null,targetType=null;
            for(Class<?> type=goal.getClass();type!=null;type=type.getSuperclass()) {
                for(java.lang.reflect.Field field:type.getDeclaredFields()) {
                    if(!field.getName().equals("mob")&&!field.getName().equals("targetType")
                            &&!LivingEntity.class.isAssignableFrom(field.getType()))continue;
                    try {
                        if(!field.trySetAccessible())continue;
                        if(field.getName().equals("mob"))owner=field.get(goal);
                        else if(field.getName().equals("targetType"))targetType=field.get(goal);
                        else if(field.get(goal)!=null)targetType=field.getName()+"="+field.get(goal);
                    } catch(ReflectiveOperationException|RuntimeException ignored) {}
                }
            }
            if(result.length()>0)result.append("; ");
            result.append(goal.getClass().getSimpleName())
                .append(" ownerIsProxy=").append(owner==proxy)
                .append(" targetType=").append(targetType);
        }
        return result.toString();
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
        VILLAGER_ZOMBIE_BRAIN_BEHAVIOR=FUNCTIONS.register(
            "villager_zombie_brain_swap",
            ()->PlayerMobGameTests::verifyVillagerZombieBrainSwap);
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
            player.discard();
            verifyPlayerBodyActionPort(helper);
            // The native reciprocal Player↔Mob attack gate is chained here
            // so CI executes it deterministically; its own sequence is kept
            // below the normal GameTest timeout.
            verifyPlayerMobNativeMindProxy(helper);
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
        player.discard();
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
        makeDamageableSurvivalPlayer(player);
        helper.assertTrue(!player.getAbilities().invulnerable&&!player.isInvulnerable()
                &&!player.getAbilities().instabuild,
            "Attack test requires a damageable, non-invulnerable Player body");
        String issue=PlayerMobPossession.begin(player,zombie);
        helper.assertTrue(issue==null,"Zombie possession rejected: "+issue);
        float initial=player.getHealth();
        PlayerMobPossession.input(player,new PlayerMobPossession.Input(0,0,false,90,0,1));
        // A new ServerPlayer may ignore ordinary hostile damage during the
        // initial spawn-protection window. Attacking at tick 3 tests that
        // protection, not possession. Wait for ordinary survival damage.
        // This GameTest-created ServerPlayer is not a normal online
        // PlayerList entry. The server's hostile-damage protection window for
        // these mock players also outlasts the first 50 ticks.
        helper.runAfterDelay(70,()->{
            net.minecraft.world.entity.Entity target=
                PossessionCombat.findValidMeleeTarget(level,zombie,player.getId(),player);
            helper.assertTrue(target==player,
                "Authenticated original body was rejected as a melee target"
                  +" dist="+zombie.distanceTo(player)
                  +" yaw="+zombie.getYRot()
                  +" sight="+zombie.hasLineOfSight(player));
            // The mock original Player may retain its spawn-protection
            // hurt timer even though the entity has remained in the level.
            clearHurtCooldown(player);
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
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        // Keep both behavior pairs inside the allocated test structure. The
        // creative FakePlayer is a valid Chicken TemptGoal stimulus, while
        // Mob.asValidTarget correctly excludes it from Zombie target selection.
        for(int x=0;x<=7;x++)for(int z=0;z<=7;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,7,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(int y=1;y<7;y++) {
                boolean wall=x==0||x==7||z==0||z==7;
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,z)),
                    wall?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                        :net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }

        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob golem=EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null&&chicken!=null&&golem!=null,
            "Real Zombie, Chicken and Iron Golem required");

        BlockPos chickenBodyPos=helper.absolutePos(new BlockPos(2,1,2));
        BlockPos zombieBodyPos=helper.absolutePos(new BlockPos(2,1,6));
        BlockPos golemPos=helper.absolutePos(new BlockPos(6,1,2));
        BlockPos seedPlayerPos=helper.absolutePos(new BlockPos(6,1,6));
        zombie.setPos(zombieBodyPos.getX()+0.5,zombieBodyPos.getY(),zombieBodyPos.getZ()+0.5);
        chicken.setPos(chickenBodyPos.getX()+0.5,chickenBodyPos.getY(),chickenBodyPos.getZ()+0.5);
        golem.setPos(golemPos.getX()+0.5,golemPos.getY(),golemPos.getZ()+0.5);
        golem.setNoAi(true);
        zombie.setPersistenceRequired();
        chicken.setPersistenceRequired();
        golem.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(zombie)&&level.addFreshEntity(chicken)
                &&level.addFreshEntity(golem),
            "Native Zombie, Chicken or Golem failed to register");

        String fakeName="FFSeed"+UUID.randomUUID().toString().replace("-","").substring(0,6);
        var stimulus=net.neoforged.neoforge.common.util.FakePlayerFactory.get(
            level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),fakeName));
        stimulus.setPos(seedPlayerPos.getX()+0.5,seedPlayerPos.getY(),seedPlayerPos.getZ()+0.5);
        stimulus.setNoGravity(true);
        // Creative is deliberate: Chicken TemptGoal still sees a player
        // holding seeds, but the Zombie mind's native target rules ignore it.
        stimulus.setGameMode(net.minecraft.world.level.GameType.CREATIVE);
        stimulus.gameMode.changeGameModeForPlayer(net.minecraft.world.level.GameType.CREATIVE);
        stimulus.setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.WHEAT_SEEDS));
        stimulus.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.WHEAT_SEEDS));
        level.addNewPlayer(stimulus);

        boolean zombieNoAi=zombie.isNoAi(),chickenNoAi=chicken.isNoAi();
        List<GoalSnapshot> zombieGoals=snapshotGoals(zombie,false);
        List<GoalSnapshot> zombieTargets=snapshotGoals(zombie,true);
        List<GoalSnapshot> chickenGoals=snapshotGoals(chicken,false);
        List<GoalSnapshot> chickenTargets=snapshotGoals(chicken,true);
        UUID zombieId=zombie.getUUID(),chickenId=chicken.getUUID();
        double chickenStart=chicken.distanceToSqr(golem);
        double zombieStart=zombie.distanceToSqr(stimulus);
        boolean chickenMeleeAvailable=MinecraftBodyPortFactory.create(chicken)
            .supports(BodyActionGate.ATTACK_ENTITY);
        UUID requester=UUID.randomUUID();
        Runnable cleanup=()->{
            System.clearProperty("freakyfriday.debugProxy");
            if(GoalMindRuntime.activeSessions(requester)>0)
                GoalMindRuntime.cancelByAccount(requester);
            if(!stimulus.isRemoved())
                level.removePlayerImmediately(stimulus,Entity.RemovalReason.DISCARDED);
            if(!zombie.isRemoved())zombie.discard();
            if(!chicken.isRemoved())chicken.discard();
            if(!golem.isRemoved())golem.discard();
        };

        try {
            helper.assertTrue(stimulus.isAlive()
                    &&level.players().contains(stimulus)
                    &&stimulus.getMainHandItem().is(Items.WHEAT_SEEDS)
                    &&stimulus.getOffhandItem().is(Items.WHEAT_SEEDS),
                "The registered creative player must be a visible Wheat Seeds stimulus");
            System.setProperty("freakyfriday.debugProxy","true");
            String failure=GoalMindRuntime.begin(requester,zombie,chicken);
            helper.assertTrue(failure==null,
                "Live Chicken↔Zombie native controller exchange failed: "+failure);

            Mob zombieMind=GoalMindRuntime.proxyForGameTest(chickenId);
            Mob chickenMind=GoalMindRuntime.proxyForGameTest(zombieId);
            helper.assertTrue(zombieMind!=null&&chickenMind!=null,
                "Both detached native minds must be running on opposite physical bodies");
            // A closed test arena isolates this Zombie from actors in other
            // concurrently running GameTests; its native Follow Range remains
            // unchanged from the donor mob.
            // Native ZombieAttackGoal has a real attack/navigation cadence. It
            // can be running during the window and idle on its final tick once
            // the target is in reach or its cooldown is active. Sample the
            // complete interval instead of mistaking one idle tick for failure.
            boolean[] zombieAttackGoalSeenRunning={false};
            double[] closestChickenToGolem={chickenStart};
            for(int sample=1;sample<75;sample++) {
                helper.runAfterDelay(sample,()->{
                    closestChickenToGolem[0]=Math.min(closestChickenToGolem[0],
                        chicken.distanceToSqr(golem));
                    if(zombieMind.goalSelector.getAvailableGoals().stream()
                            .anyMatch(w->w.getGoal().getClass().getSimpleName().equals("ZombieAttackGoal")
                                &&w.isRunning()))
                        zombieAttackGoalSeenRunning[0]=true;
                });
            }
            helper.runAfterDelay(75,()->{
                try {
                    helper.assertTrue(zombie.isAlive()&&chicken.isAlive()
                            &&zombie.isNoAi()&&chicken.isNoAi()
                            &&zombie.getType().equals(EntityTypes.ZOMBIE)
                            &&chicken.getType().equals(EntityTypes.CHICKEN)
                            &&level.getEntity(zombieId)==zombie&&level.getEntity(chickenId)==chicken,
                        "The two original physical bodies did not retain their identity while both minds ran");
                    helper.assertTrue(zombieMind.getTarget()==golem,
                        "Zombie mind did not acquire its native Iron Golem target in the Chicken body; target="
                            +zombieMind.getTarget());
                    double chickenEnd=chicken.distanceToSqr(golem);
                    double zombieEnd=zombie.distanceToSqr(stimulus);
                    boolean chickenTemptRunning=chickenMind.goalSelector.getAvailableGoals().stream()
                        .anyMatch(w->w.getGoal().getClass().getSimpleName().equals("TemptGoal")
                            &&w.isRunning());
                    boolean zombieAttackGoalPresent=zombieMind.goalSelector.getAvailableGoals().stream()
                        .anyMatch(w->w.getGoal().getClass().getSimpleName().equals("ZombieAttackGoal"));
                    boolean zombieAttackGoalRunning=zombieMind.goalSelector.getAvailableGoals().stream()
                        .anyMatch(w->w.getGoal().getClass().getSimpleName().equals("ZombieAttackGoal")
                            &&w.isRunning());
                    helper.assertTrue(chickenTemptRunning,
                        "Chicken mind did not start its native TemptGoal for the registered seed-holding player");
                    helper.assertTrue(zombieAttackGoalPresent
                            &&(zombieAttackGoalSeenRunning[0]||zombieAttackGoalRunning),
                        "Zombie mind never ran its native ZombieAttackGoal against the real Iron Golem");

                    boolean chickenHasWanted=chickenMind.getMoveControl().hasWanted();
                    System.out.println("[FF-CHICKEN-AI-STATE] playerRegistered="
                        +level.players().contains(stimulus)
                        +" playerAlive="+stimulus.isAlive()
                        +" playerCreative="+stimulus.isCreative()
                        +" temptRunning="+chickenTemptRunning
                        +" navigationDone="+chickenMind.getNavigation().isDone()
                        +" navigationPath="+(chickenMind.getNavigation().getPath()!=null)
                        +" moveWanted="+chickenHasWanted
                        +" proxyPosition="+chickenMind.position()
                        +" physicalZombie="+zombie.position()
                        +" playerPosition="+stimulus.position()
                        +" proxyOnGround="+chickenMind.onGround()
                        +" bodyOnGround="+zombie.onGround()
                        +" zombieAttackGoalRunning="+zombieAttackGoalRunning
                        +" zombieAttackGoalSeenRunning="+zombieAttackGoalSeenRunning[0]
                        +" chickenMeleeAvailable="+chickenMeleeAvailable
                        +" moveDelta="+chickenMind.getDeltaMovement());
                    System.clearProperty("freakyfriday.debugProxy");

                    System.out.println("[FF-ZOMBIE-CHICKEN-PURSUIT] start="+chickenStart
                        +" closest="+closestChickenToGolem[0]+" final="+chickenEnd
                        +" attackGoalSeen="+zombieAttackGoalSeenRunning[0]);
                    helper.assertTrue(closestChickenToGolem[0]<chickenStart-0.5,
                        "Zombie mind never moved the real Chicken body toward its Golem target; "
                            +"start="+chickenStart+" closest="+closestChickenToGolem[0]
                            +" final="+chickenEnd);
                    helper.assertTrue(zombieEnd<zombieStart-0.5,
                        "Chicken mind did not move the real Zombie body toward the seed-holding player; "
                            +"distance="+zombieStart+"->"+zombieEnd);
                    // The Chicken body has no vanilla ATTACK_DAMAGE attribute.
                    // The body adapter is authoritative: it must report the
                    // attack unavailable, and the detached Zombie proxy must
                    // not appear as the Golem's attacker. Do not compare total
                    // Golem health here because other live GameTest fixtures
                    // share the ServerLevel and may damage unrelated targets.
                    if(chickenMeleeAvailable) {
                        helper.assertTrue(golem.getLastHurtByMob()==zombieMind,
                            "A Chicken body with native melee capability did not execute the Zombie mind's attack");
                    } else {
                        helper.assertTrue(golem.getLastHurtByMob()!=zombieMind,
                            "The Zombie mind proxy bypassed the Chicken body's unavailable melee capability");
                    }
                    helper.assertTrue(GoalMindRuntime.activeSessions(requester)==1
                            &&GoalMindRuntime.bodyReserved(zombieId)
                            &&GoalMindRuntime.bodyReserved(chickenId),
                        "Both physical bodies must remain independently assigned during the reciprocal behavior test");
                    helper.assertTrue(GoalMindRuntime.cancelByAccount(requester)
                            &&GoalMindRuntime.activeSessions(requester)==0,
                        "The original Chicken/Zombie controllers did not restore cleanly");
                    helper.assertTrue(zombie.isNoAi()==zombieNoAi&&chicken.isNoAi()==chickenNoAi
                            &&hasSameGoals(zombie,false,zombieGoals)
                            &&hasSameGoals(zombie,true,zombieTargets)
                            &&hasSameGoals(chicken,false,chickenGoals)
                            &&hasSameGoals(chicken,true,chickenTargets),
                        "Returning from the exchange did not restore each body's exact original AI state");

                    System.out.println("FF-GAMEPLAY zombie_chicken_behavior_acceptance PASS"
                        +" zombieMindPursuesGolemInChickenBody=true"
                        +" chickenMindTemptsPlayerInZombieBody=true"
                        +" zombieAttackGoalRunning="+zombieAttackGoalRunning
                        +" chickenMeleeAvailable="+chickenMeleeAvailable
                        +" bodyCapabilityRespected=true"
                        +" simultaneousIndependentBodies=true originalAIrestored=true");
                    helper.succeed();
                } finally {
                    cleanup.run();
                }
            });
        } catch(RuntimeException failure) {
            cleanup.run();
            throw failure;
        }
    }

    static void verifyMobMobReciprocalAttack(GameTestHelper helper) {
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
        float firstProxyHealth=firstBodyMind.getHealth();
        float secondProxyHealth=secondBodyMind.getHealth();

        // Phase 1: each mind attacks the OTHER detached mind proxy. Damage
        // must be redirected to the physical body currently hosting that mind.
        firstBodyMind.goalSelector.addGoal(0,new OneShotNativeAttackGoal(firstBodyMind,secondBodyMind));
        secondBodyMind.goalSelector.addGoal(0,new OneShotNativeAttackGoal(secondBodyMind,firstBodyMind));
        helper.runAfterDelay(4,()->{
            helper.assertTrue(first.getHealth()<firstHealth&&second.getHealth()<secondHealth,
                "Attacks on the opposite mind proxies did not damage both real host bodies"
                    +" first="+firstHealth+"->"+first.getHealth()
                    +" second="+secondHealth+"->"+second.getHealth());
            helper.assertTrue(firstBodyMind.getHealth()==firstProxyHealth
                    &&secondBodyMind.getHealth()==secondProxyHealth,
                "Mind-proxy attacks consumed detached proxy health instead of real host-body health");

            // Phase 2: both minds now attack the opposite physical body
            // directly. This proves the body remains targetable separately
            // from the mind proxy.
            float firstAfterMindHit=first.getHealth();
            float secondAfterMindHit=second.getHealth();
            firstBodyMind.goalSelector.addGoal(0,new OneShotNativeAttackGoal(firstBodyMind,second));
            secondBodyMind.goalSelector.addGoal(0,new OneShotNativeAttackGoal(secondBodyMind,first));
            helper.runAfterDelay(10,()->{
                helper.assertTrue(first.getHealth()<firstAfterMindHit
                        &&second.getHealth()<secondAfterMindHit,
                    "Both opposite native minds must also damage the other physical body directly");
                helper.assertTrue(firstBodyMind.getHealth()==firstProxyHealth
                        &&secondBodyMind.getHealth()==secondProxyHealth,
                    "Direct body attacks modified detached mind proxy health");
                helper.assertTrue(GoalMindRuntime.activeSessions(requester)==1,
                    "Nonfatal reciprocal attacks unexpectedly terminated the Mob↔Mob swap");
                helper.assertTrue(GoalMindRuntime.cancelByAccount(requester),
                    "Mob↔Mob reciprocal attack session did not return cleanly");
                first.discard();
                second.discard();
                System.out.println("FF-GAMEPLAY mob_mob_reciprocal_attack PASS"
                    +" bothMindsAttackOppositeMindProxy=true bothMindsAttackOppositeBody=true"
                    +" proxyDamageRedirected=true independentBodyHealthPools=true reversible=true");
                helper.succeed();
            });
        });
    }

    /** A non-universal experimental path must never mutate either real mob. */
    /**
     * The Villager is a Brain-only mob, the Zombie is Goal-driven. After the
     * swap, the original Villager body must be driven by Zombie AI and move
     * toward the Villager mind now hosted by the original Zombie body. The
     * original Zombie body must run the Villager Brain and flee from the
     * Zombie mind now hosted by the original Villager body.
     */
    /**
     * Cross-species identity acceptance:
     * - Zombie mind on the original Villager body attacks an independent
     *   physical Villager, but does NOT classify the swapped partner (whose
     *   physical body is still a Zombie) as a Villager.
     * - Villager mind on the original Zombie body flees an independent
     *   physical Zombie, but does NOT classify the swapped partner (whose
     *   physical body is still a Villager) as a Zombie.
     * This proves target identity follows the BODY species, not the donor mind.
     */
    private static void verifyVillagerZombieBrainSwap(GameTestHelper helper) {
        ServerLevel level=helper.getLevel();
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        for(int x=0;x<=18;x++)for(int z=0;z<=8;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,7,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            for(int y=1;y<7;y++) {
                boolean wall=x==0||x==18||z==0||z==8;
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,z)),
                    wall?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                        :net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }

        Mob physicalVillager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
        Mob physicalZombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob stimulusVillager=EntityTypes.VILLAGER.create(level,EntitySpawnReason.COMMAND);
        Mob stimulusZombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(physicalVillager!=null&&physicalZombie!=null
                &&stimulusVillager!=null&&stimulusZombie!=null,
            "Villager/Zombie swap requires two swapped bodies and two independent AI stimuli");

        BlockPos villagerBodyPos=helper.absolutePos(new BlockPos(5,1,4));
        BlockPos zombieBodyPos=helper.absolutePos(new BlockPos(12,1,4));
        BlockPos villagerStimulusPos=helper.absolutePos(new BlockPos(2,1,4));
        BlockPos zombieStimulusPos=helper.absolutePos(new BlockPos(16,1,4));
        physicalVillager.setPos(villagerBodyPos.getX()+0.5,villagerBodyPos.getY(),
            villagerBodyPos.getZ()+0.5);
        physicalZombie.setPos(zombieBodyPos.getX()+0.5,zombieBodyPos.getY(),
            zombieBodyPos.getZ()+0.5);
        stimulusVillager.setPos(villagerStimulusPos.getX()+0.5,villagerStimulusPos.getY(),
            villagerStimulusPos.getZ()+0.5);
        stimulusZombie.setPos(zombieStimulusPos.getX()+0.5,zombieStimulusPos.getY(),
            zombieStimulusPos.getZ()+0.5);
        // Fixed stimuli: only the swapped native minds decide and move.
        stimulusVillager.setNoAi(true);
        stimulusZombie.setNoAi(true);
        physicalVillager.setPersistenceRequired();
        physicalZombie.setPersistenceRequired();
        stimulusVillager.setPersistenceRequired();
        stimulusZombie.setPersistenceRequired();
        helper.assertTrue(!physicalVillager.getBrain().isBrainDead()
                &&physicalZombie.getBrain().isBrainDead(),
            "Fixture must exercise a native Villager Brain against a Goal-driven Zombie");
        helper.assertTrue(level.addFreshEntity(physicalVillager)
                &&level.addFreshEntity(physicalZombie)
                &&level.addFreshEntity(stimulusVillager)
                &&level.addFreshEntity(stimulusZombie),
            "Villager/Zombie behavior actors failed to register");

        UUID requester=UUID.randomUUID();
        UUID villagerId=physicalVillager.getUUID(),zombieId=physicalZombie.getUUID();
        var villagerBrain=physicalVillager.getBrain();
        var zombieBrain=physicalZombie.getBrain();
        List<GoalSnapshot> villagerGoals=snapshotGoals(physicalVillager,false);
        List<GoalSnapshot> villagerTargets=snapshotGoals(physicalVillager,true);
        List<GoalSnapshot> zombieGoals=snapshotGoals(physicalZombie,false);
        List<GoalSnapshot> zombieTargets=snapshotGoals(physicalZombie,true);
        boolean villagerNoAi=physicalVillager.isNoAi(),zombieNoAi=physicalZombie.isNoAi();
        Vec3 villagerStart=physicalVillager.position(),zombieStart=physicalZombie.position();

        String issue=GoalMindRuntime.begin(requester,physicalVillager,physicalZombie);
        helper.assertTrue(issue==null,
            "Native Villager Brain↔Zombie Goal swap was rejected: "+issue);
        Mob zombieMindProxy=GoalMindRuntime.proxyForPhysicalBody(villagerId);
        Mob villagerMindProxy=GoalMindRuntime.proxyForPhysicalBody(zombieId);
        Mob zombieMindAlias=GoalMindRuntime.mindAliasForPhysicalBody(villagerId);
        Mob villagerMindAlias=GoalMindRuntime.mindAliasForPhysicalBody(zombieId);
        helper.assertTrue(zombieMindProxy!=null&&villagerMindProxy!=null
                &&zombieMindProxy.getType().equals(EntityTypes.ZOMBIE)
                &&villagerMindProxy.getType().equals(EntityTypes.VILLAGER),
            "Both incoming minds must run on same-type native proxies");
        // The hidden target identities deliberately use the PHYSICAL host
        // types: Villager for the Zombie mind's opposite body, Zombie for the
        // Villager mind's opposite body. Mind type must not rewrite anatomy.
        helper.assertTrue(zombieMindAlias!=null&&villagerMindAlias!=null
                &&zombieMindAlias.getType().equals(physicalVillager.getType())
                &&villagerMindAlias.getType().equals(physicalZombie.getType())
                &&zombieMindAlias.isInvisible()&&villagerMindAlias.isInvisible(),
            "Mind target identities were not typed from their original physical bodies");
        helper.assertTrue(physicalVillager.isNoAi()&&physicalZombie.isNoAi(),
            "Original autonomous Brain/Goal controllers were not paused during the exchange");

        double villagerBodyStartX=physicalVillager.getX();
        double zombieBodyStartX=physicalZombie.getX();
        double villagerToStimulusStart=physicalVillager.position()
            .subtract(stimulusVillager.position()).horizontalDistanceSqr();
        double zombieToThreatStart=physicalZombie.position()
            .subtract(stimulusZombie.position()).horizontalDistanceSqr();
        double[] greatestZombieThreatDistance={zombieToThreatStart};
        boolean[] villagerSawHostile={false};
        boolean[] villagerHadWalkTarget={false};
        for(int sample=1;sample<95;sample++) {
            helper.runAfterDelay(sample,()->{
                greatestZombieThreatDistance[0]=Math.max(greatestZombieThreatDistance[0],
                    physicalZombie.position().subtract(stimulusZombie.position())
                        .horizontalDistanceSqr());
                villagerSawHostile[0]|=villagerMindProxy.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE).isPresent();
                villagerHadWalkTarget[0]|=villagerMindProxy.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET).isPresent();
            });
        }
        helper.runAfterDelay(95,()->{
            try {
                Entity zombieMindTarget=zombieMindProxy.getTarget();
                double villagerBodyDeltaX=physicalVillager.getX()-villagerBodyStartX;
                double zombieBodyDeltaX=physicalZombie.getX()-zombieBodyStartX;
                double villagerToStimulusEnd=physicalVillager.position()
                    .subtract(stimulusVillager.position()).horizontalDistanceSqr();
                double zombieToThreatEnd=physicalZombie.position()
                    .subtract(stimulusZombie.position()).horizontalDistanceSqr();
                helper.assertTrue(zombieMindTarget==stimulusVillager,
                    "Zombie mind on the Villager body did not acquire the independent physical Villager stimulus; "
                        +"target="+(zombieMindTarget==null?"null":zombieMindTarget.getType())
                        +" expected="+stimulusVillager.getType()
                        +" probe={"+targetProbe(level,zombieMindProxy,stimulusVillager)+"}"
                        +" targetGoals={"+targetGoalOwners(zombieMindProxy)+"}"
                        +" running="+zombieMindProxy.targetSelector.getAvailableGoals().stream()
                            .map(w->w.getGoal().getClass().getSimpleName()+"[running="+w.isRunning()+"]")
                            .toList()
                        +" proxyPos="+zombieMindProxy.position()
                        +" stimulusPos="+stimulusVillager.position()
                        +" stimulusAlive="+stimulusVillager.isAlive()
                        +" stimulusNoAi="+stimulusVillager.isNoAi());
                helper.assertTrue(zombieMindTarget!=villagerMindAlias
                        &&zombieMindTarget!=physicalZombie,
                    "Zombie mind incorrectly treated its swapped Zombie partner as a Villager target");
                helper.assertTrue(villagerToStimulusEnd<villagerToStimulusStart-0.5,
                    "Zombie mind did not make the original Villager body approach the real Villager target; "
                        +"distanceSq="+villagerToStimulusStart+"->"+villagerToStimulusEnd
                        +" xDelta="+villagerBodyDeltaX);
                var nearestVisible=villagerMindProxy.getBrain().getMemory(
                    net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_VISIBLE_LIVING_ENTITIES);
                boolean targetableByBrainSensor=
                    net.minecraft.world.entity.ai.sensing.Sensor.isEntityTargetable(
                        level,villagerMindProxy,stimulusZombie);
                boolean stimulusRegistered=level.getEntity(stimulusZombie.getUUID())==stimulusZombie;
                int nearestVisibleCount=nearestVisible.map(v->v.nearbyEntities().size()).orElse(-1);
                boolean stimulusInVisible=nearestVisible.map(v->v.contains(stimulusZombie)).orElse(false);
                helper.assertTrue(greatestZombieThreatDistance[0]>zombieToThreatStart+0.5,
                    "Villager mind never increased distance from the independent Zombie threat; "
                        +"startSq="+zombieToThreatStart
                        +" greatestSq="+greatestZombieThreatDistance[0]
                        +" finalSq="+zombieToThreatEnd
                        +" xDelta="+zombieBodyDeltaX
                        +" hostileEverSeen="+villagerSawHostile[0]
                        +" walkTargetEverSet="+villagerHadWalkTarget[0]
                        +" nearestHostileNow="+villagerMindProxy.getBrain().getMemory(
                            net.minecraft.world.entity.ai.memory.MemoryModuleType.NEAREST_HOSTILE)
                        +" walkTargetNow="+villagerMindProxy.getBrain().getMemory(
                            net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET)
                        +" activeActivity="+villagerMindProxy.getBrain().getActiveNonCoreActivity()
                        +" runningBehaviors="+villagerMindProxy.getBrain().getRunningBehaviors().stream()
                            .map(b->b.getClass().getSimpleName()).toList()
                        +" nearestVisibleCount="+nearestVisibleCount
                        +" stimulusInVisible="+stimulusInVisible
                        +" targetableByBrainSensor="+targetableByBrainSensor
                        +" stimulusRegistered="+stimulusRegistered
                        +" stimulusAlive="+stimulusZombie.isAlive()
                        +" stimulusNoAi="+stimulusZombie.isNoAi()
                        +" stimulusInvisible="+stimulusZombie.isInvisible()
                        +" proxyPos="+villagerMindProxy.position()
                        +" physicalBodyPos="+physicalZombie.position()
                        +" threatPos="+stimulusZombie.position());

                // The swapped pair itself is not a valid threat/target by its
                // physical species: Zombie mind sees a Zombie, and Villager
                // mind sees a Villager. The two separate stimuli above prove
                // that both native AI controllers still recognize real bodies.
                helper.assertTrue(physicalVillager.getType().equals(EntityTypes.VILLAGER)
                        &&physicalZombie.getType().equals(EntityTypes.ZOMBIE)
                        &&villagerMindAlias.getType().equals(EntityTypes.ZOMBIE)
                        &&zombieMindAlias.getType().equals(EntityTypes.VILLAGER),
                    "Mind ownership changed either body's native target identity");

                float villagerBodyHealth=physicalVillager.getHealth();
                float zombieBodyHealth=physicalZombie.getHealth();
                var villagerAttack=level.damageSources().mobAttack(physicalVillager);
                var zombieAttack=level.damageSources().mobAttack(physicalZombie);
                clearHurtCooldown(physicalZombie);
                boolean zombieBodyHit=physicalZombie.hurtServer(level,villagerAttack,2.0f);
                clearHurtCooldown(physicalVillager);
                boolean villagerBodyHit=physicalVillager.hurtServer(level,zombieAttack,2.0f);
                helper.assertTrue(zombieBodyHit&&villagerBodyHit
                        &&physicalZombie.getHealth()<zombieBodyHealth
                        &&physicalVillager.getHealth()<villagerBodyHealth,
                    "Opposite physical bodies could not damage both original body health pools");

                // Attacking the mind identity must resolve to the physical
                // body carrying that mind; proxy/alias health is never spent.
                float zombieMindHostHealth=physicalVillager.getHealth();
                float villagerMindHostHealth=physicalZombie.getHealth();
                float zombieAliasHealth=zombieMindAlias.getHealth();
                float villagerAliasHealth=villagerMindAlias.getHealth();
                clearHurtCooldown(physicalVillager);
                clearHurtCooldown(zombieMindAlias);
                boolean zombieMindHit=zombieMindAlias.hurtServer(
                    level,zombieAttack,2.0f);
                clearHurtCooldown(physicalZombie);
                clearHurtCooldown(villagerMindAlias);
                boolean villagerMindHit=villagerMindAlias.hurtServer(
                    level,villagerAttack,2.0f);
                helper.assertTrue(physicalVillager.getHealth()<zombieMindHostHealth
                        &&physicalZombie.getHealth()<villagerMindHostHealth
                        &&zombieMindAlias.getHealth()==zombieAliasHealth
                        &&villagerMindAlias.getHealth()==villagerAliasHealth,
                    "An opposite hit on either mind identity did not damage its original physical host");

                helper.assertTrue(level.getEntity(villagerId)==physicalVillager
                        &&level.getEntity(zombieId)==physicalZombie
                        &&physicalVillager.getBrain()==villagerBrain
                        &&physicalZombie.getBrain()==zombieBrain,
                    "The exchange replaced a physical entity or moved its Brain off the real body");
                helper.assertTrue(GoalMindRuntime.cancelByAccount(requester)
                        &&GoalMindRuntime.activeSessions(requester)==0,
                    "Villager/Zombie mind return did not end the live swap");
                helper.assertTrue(physicalVillager.isNoAi()==villagerNoAi
                        &&physicalZombie.isNoAi()==zombieNoAi
                        &&hasSameGoals(physicalVillager,false,villagerGoals)
                        &&hasSameGoals(physicalVillager,true,villagerTargets)
                        &&hasSameGoals(physicalZombie,false,zombieGoals)
                        &&hasSameGoals(physicalZombie,true,zombieTargets),
                    "Cancel did not restore both original native Brain/Goal states");
                helper.assertTrue(zombieMindProxy.isRemoved()&&villagerMindProxy.isRemoved()
                        &&zombieMindAlias.isRemoved()&&villagerMindAlias.isRemoved(),
                    "Cancel did not remove every proxy and physical-type target identity");

                System.out.println("FF-GAMEPLAY villager_zombie_brain_swap PASS"
                    +" zombieMindTargetsRealVillagerOnly=true"
                    +" zombieMindDoesNotTargetSwappedZombieBody=true"
                    +" villagerMindFleesRealZombieOnly=true"
                    +" villagerMindDoesNotFleeSwappedVillagerBody=true"
                    +" originalBodySpeciesPreserved=true"
                    +" bothPhysicalBodiesDamageable=true"
                    +" bothMindIdentitiesDamageRedirected=true"
                    +" nativeBrainCopied=true restored=true");
                physicalVillager.discard();
                physicalZombie.discard();
                stimulusVillager.discard();
                stimulusZombie.discard();
                helper.succeed();
            } catch(RuntimeException failure) {
                GoalMindRuntime.cancelByAccount(requester);
                if(!physicalVillager.isRemoved())physicalVillager.discard();
                if(!physicalZombie.isRemoved())physicalZombie.discard();
                if(!stimulusVillager.isRemoved())stimulusVillager.discard();
                if(!stimulusZombie.isRemoved())stimulusZombie.discard();
                throw failure;
            }
        });
    }


    private static void verifyPartialSwapRejected(GameTestHelper helper) {
        var level=helper.getLevel();
        // Zombie target-selection goals are disabled in Peaceful. The fixture
        // explicitly exercises the real hostile target AI under Normal
        // difficulty; this changes only the GameTest world's difficulty.
        level.getServer().setDifficulty(net.minecraft.world.Difficulty.NORMAL,true);
        // Brain-driven Villager↔Goal-driven Zombie behavior is now covered by
        // villager_zombie_brain_swap; this test focuses on independent Goal
        // exchanges, exclusive body reservations and controller restoration.
        // Build a real, roofed arena and two independent live Mob pairs.
        for(int x=0;x<=24;x++) for(int z=0;z<=10;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,7,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            // The default GameTest template may leave barrier blocks inside
            // this larger custom arena. Clear its playable volume so the
            // native LOS ray sees only the explicitly built floor and roof.
            for(int y=1;y<7;y++) {
                boolean wall=x==0||x==24||z==0||z==10;
                level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,z)),
                    wall?net.minecraft.world.level.block.Blocks.STONE.defaultBlockState()
                        :net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
        }
        // This fixture deliberately uses a registered hostile target rather
        // than a GameTest mock player, which is not part of ServerLevel.players().

        Mob opponent=EntityTypes.SKELETON.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(opponent!=null,"Mob-pair damage test needs an opposing Skeleton");
        opponent.setNoAi(true);
        opponent.setPos(helper.absolutePos(new BlockPos(23,1,4)).getX()+0.5,
            helper.absolutePos(new BlockPos(23,1,4)).getY(),
            helper.absolutePos(new BlockPos(23,1,4)).getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(opponent),
            "Opposing Skeleton failed to register for physical-body damage test");
        // The Zombie controller has one unambiguous, real Iron Golem target.
        Mob zombieTarget=EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombieTarget!=null,"Zombie mind target fixture requires an Iron Golem");
        zombieTarget.setNoAi(true);
        // Keep the Golem directly along the proxy's unobstructed X axis;
        // diagonal paths in this template cross its internal barrier boundary.
        BlockPos zombieTargetPos=helper.absolutePos(new BlockPos(4,1,4));
        zombieTarget.setPos(zombieTargetPos.getX()+0.5,zombieTargetPos.getY(),
            zombieTargetPos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(zombieTarget),
            "Iron Golem target for Zombie mind failed to register");

        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob secondZombieA=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob secondZombieB=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null&&zombie!=null&&secondZombieA!=null&&secondZombieB!=null,
            "Live cross-species exchange fixtures could not be created");
        // Keep the Zombie mind within native target range of the Iron Golem.
        BlockPos chickenPos=helper.absolutePos(new BlockPos(1,1,4));
        BlockPos zombiePos=helper.absolutePos(new BlockPos(11,1,4));
        BlockPos secondZombieAPos=helper.absolutePos(new BlockPos(15,1,4));
        BlockPos secondZombieBPos=helper.absolutePos(new BlockPos(19,1,4));
        chicken.setPos(chickenPos.getX()+0.5,chickenPos.getY(),chickenPos.getZ()+0.5);
        zombie.setPos(zombiePos.getX()+0.5,zombiePos.getY(),zombiePos.getZ()+0.5);
        secondZombieA.setPos(secondZombieAPos.getX()+0.5,secondZombieAPos.getY(),secondZombieAPos.getZ()+0.5);
        secondZombieB.setPos(secondZombieBPos.getX()+0.5,secondZombieBPos.getY(),secondZombieBPos.getZ()+0.5);
        // These are staged for a later, separate pair test. Keep their own
        // vanilla AI asleep until that pair is actually reserved.
        secondZombieA.setNoAi(true);
        secondZombieB.setNoAi(true);
        helper.assertTrue(level.addFreshEntity(chicken)&&level.addFreshEntity(zombie)
                &&level.addFreshEntity(secondZombieA)&&level.addFreshEntity(secondZombieB),
            "Live cross-species bodies failed to register");
        chicken.setPersistenceRequired();
        zombie.setPersistenceRequired();
        secondZombieA.setPersistenceRequired();
        secondZombieB.setPersistenceRequired();

        List<GoalSnapshot> chickenGoals=snapshotGoals(chicken,false);
        List<GoalSnapshot> chickenTargets=snapshotGoals(chicken,true);
        List<GoalSnapshot> zombieGoals=snapshotGoals(zombie,false);
        List<GoalSnapshot> zombieTargets=snapshotGoals(zombie,true);
        boolean chickenNoAi=chicken.isNoAi(),zombieNoAi=zombie.isNoAi();
        UUID requester=java.util.UUID.randomUUID();
        String firstError=GoalMindRuntime.begin(requester,chicken,zombie);
        helper.assertTrue(firstError==null,
            "Live Chicken↔Zombie native goal exchange failed: "+firstError);
        // The Zombie mind must acquire the real Iron Golem through its own
        // native target selector; no target is injected into the proxy.
        Mob zombieMind=GoalMindRuntime.proxyForGameTest(chicken.getUUID());
        helper.assertTrue(zombieMind!=null,"Zombie mind proxy for Chicken body is missing");
        System.out.println("[FF-TARGET] MobMob "+targetProbe(level,zombieMind,zombieTarget)
            +" goals=["+targetGoalOwners(zombieMind)+"]");
        helper.assertTrue(chicken.goalSelector.getAvailableGoals().isEmpty()
                &&zombie.goalSelector.getAvailableGoals().isEmpty()
                &&chicken.targetSelector.getAvailableGoals().isEmpty()
                &&zombie.targetSelector.getAvailableGoals().isEmpty(),
            "The original bodies' autonomous goals were not suspended during the proxy exchange");
        helper.assertTrue(chicken.isNoAi()&&zombie.isNoAi(),
            "Original autonomous Goal controllers were not paused for the live swap");

        // A requester is only a session manager: an occupied body is exclusive,
        // but a second unrelated pair is allowed under the same requester.
        String occupiedError=GoalMindRuntime.begin(requester,chicken,secondZombieB);
        helper.assertTrue(occupiedError!=null,
            "An already-occupied physical body was allowed into a second exchange");

        double chickenStart=chicken.distanceToSqr(zombieTarget);
        // Track the closest approach, not only the final position. A native
        // melee goal can reach the target and then stop/repath or be pushed
        // away by the target's collision response before the 75-tick window
        // ends. The acceptance is that native AI actually drove the body
        // toward its selected target during the live exchange.
        double[] chickenClosest={chickenStart};
        for(int sample=1;sample<75;sample++) {
            helper.runAfterDelay(sample,()->{
                chickenClosest[0]=Math.min(chickenClosest[0],
                    chicken.distanceToSqr(zombieTarget));
            });
        }
        // Native nearest-target goals have an internal random acquisition
        // interval. Allow enough real server ticks for the unmodified vanilla
        // selector to make its ordinary acquisition attempt.
        helper.runAfterDelay(75,()->{
            helper.assertTrue(chicken.isAlive()&&zombie.isAlive()
                    &&secondZombieA.isAlive()&&secondZombieB.isAlive(),
                "One of the original physical Mob bodies stopped ticking during the swap");
            double chickenEnd=chicken.distanceToSqr(zombieTarget);
            String targetGoalState=zombieMind.targetSelector.getAvailableGoals().stream()
                .map(w->w.getGoal().getClass().getSimpleName()
                    +"[running="+w.isRunning()+"]")
                .toList().toString();
            System.out.println("[FF-TARGET-STATE] MobMob target="+zombieMind.getTarget()
                +" goals="+targetGoalState);
            helper.assertTrue(zombieMind.getTarget()==zombieTarget,
                "Zombie mind's native target selector did not acquire its real Iron Golem stimulus; "
                    +"target="+zombieMind.getTarget()+" goals="+targetGoalState);
            System.out.println("[FF-MOBMOB-PURSUIT] chickenToGolemStart="+chickenStart
                +" closest="+chickenClosest[0]+" final="+chickenEnd);
            helper.assertTrue(chickenClosest[0]<chickenStart-0.5,
                "Zombie mind never moved the Chicken physical body toward the real Iron Golem"
                    +" chickenToGolemStart="+chickenStart
                    +" closest="+chickenClosest[0]+" final="+chickenEnd);
            // The Chicken body remains alive while its incoming Zombie mind
            // independently pursues the real Golem. The reciprocal Villager↔Zombie
            // acceptance below checks a Brain-driven mind in the other direction.
            helper.assertTrue(zombie.isAlive(),
                "The physical Zombie carrying Chicken mind did not remain alive");

            // Start the second independent pair only after the cross-species
            // target behavior has been observed.
            zombieTarget.discard();
            List<GoalSnapshot> secondZombieAGoals=snapshotGoals(secondZombieA,false);
            List<GoalSnapshot> secondZombieATargets=snapshotGoals(secondZombieA,true);
            List<GoalSnapshot> secondZombieBGoals=snapshotGoals(secondZombieB,false);
            List<GoalSnapshot> secondZombieBTargets=snapshotGoals(secondZombieB,true);
            boolean secondZombieANoAi=secondZombieA.isNoAi();
            boolean secondZombieBNoAi=secondZombieB.isNoAi();
            secondZombieA.setPos(helper.absolutePos(new BlockPos(16,1,4)).getX()+0.5,
                helper.absolutePos(new BlockPos(16,1,4)).getY(),
                helper.absolutePos(new BlockPos(16,1,4)).getZ()+0.5);
            secondZombieB.setPos(helper.absolutePos(new BlockPos(18,1,4)).getX()+0.5,
                helper.absolutePos(new BlockPos(18,1,4)).getY(),
                helper.absolutePos(new BlockPos(18,1,4)).getZ()+0.5);
            String secondError=GoalMindRuntime.begin(requester,secondZombieA,secondZombieB);
            helper.assertTrue(secondError==null&&GoalMindRuntime.activeSessions(requester)==2,
                "One requester could not manage two independent live Mob exchanges: "+secondError);

            Mob secondZombieMindA=GoalMindRuntime.proxyForGameTest(secondZombieA.getUUID());
            Mob secondZombieMindB=GoalMindRuntime.proxyForGameTest(secondZombieB.getUUID());
            helper.assertTrue(secondZombieMindA!=null&&secondZombieMindB!=null,
                "Both second-pair Zombie mind proxies are required for combat");
            float secondZombieAHealthBeforeMindAttack=secondZombieA.getHealth();
            float secondZombieBHealthBeforeMindAttack=secondZombieB.getHealth();
            float secondZombieMindAProxyHealth=secondZombieMindA.getHealth();
            float secondZombieMindBProxyHealth=secondZombieMindB.getHealth();

            // Phase 1: each PHYSICAL body attacks the opposite detached mind.
            // A proxy has no independent health: its hit must be redirected
            // to the real physical body currently hosting that mind.
            secondZombieA.doHurtTarget(level,secondZombieMindB);
            secondZombieB.doHurtTarget(level,secondZombieMindA);
            helper.runAfterDelay(1,()->{
                helper.assertTrue(secondZombieA.getHealth()<secondZombieAHealthBeforeMindAttack
                        &&secondZombieB.getHealth()<secondZombieBHealthBeforeMindAttack,
                    "Opposite physical bodies could not damage the minds they were swapped with");
                helper.assertTrue(secondZombieMindA.getHealth()==secondZombieMindAProxyHealth
                        &&secondZombieMindB.getHealth()==secondZombieMindBProxyHealth,
                    "Damage to a detached mind changed proxy health instead of its physical host");

                // Phase 2: each detached mind attacks the opposite REAL
                // physical body. Its assigned physical host executes the hit.
                float secondAAfterMindHit=secondZombieA.getHealth();
                float secondBAfterMindHit=secondZombieB.getHealth();
                clearHurtCooldown(secondZombieA);
                clearHurtCooldown(secondZombieB);
                // Put the two bodies comfortably inside vanilla melee reach,
                // preserve their facing, and make the hit cooldown state
                // explicit before each incoming native mind attempts a hit.
                BlockPos phase2A=helper.absolutePos(new BlockPos(16,1,4));
                BlockPos phase2B=helper.absolutePos(new BlockPos(17,1,4));
                secondZombieA.setPos(phase2A.getX()+0.5,phase2A.getY(),phase2A.getZ()+0.5);
                secondZombieB.setPos(phase2B.getX()+0.5,phase2B.getY(),phase2B.getZ()+0.5);
                secondZombieA.setYRot(-90.0f);
                secondZombieB.setYRot(90.0f);
                secondZombieA.setYHeadRot(-90.0f);
                secondZombieB.setYHeadRot(90.0f);
                Mob secondZombieAliasA=GoalMindRuntime.mindAliasForPhysicalBody(
                    secondZombieA.getUUID());
                Mob secondZombieAliasB=GoalMindRuntime.mindAliasForPhysicalBody(
                    secondZombieB.getUUID());
                helper.assertTrue(secondZombieAliasA!=null&&secondZombieAliasB!=null,
                    "Both second-pair physical-body mind aliases are required for native attack routing");
                // Native AI attacks the identity of the opposite physical
                // body, not the original host object. The damage event then
                // resolves that alias hit to the real host. Hitting the host
                // object directly would reserve its hurt timer on 26.3 before
                // the host-owned attack can execute.
                secondZombieMindA.goalSelector.addGoal(0,
                    new OneShotNativeAttackGoal(secondZombieMindA,secondZombieAliasB));
                secondZombieMindB.goalSelector.addGoal(0,
                    new OneShotNativeAttackGoal(secondZombieMindB,secondZombieAliasA));
                System.setProperty("freakyfriday.debugProxy","true");
                helper.runAfterDelay(20,()->{
                    try {
                    helper.assertTrue(secondZombieA.getHealth()<secondAAfterMindHit
                            &&secondZombieB.getHealth()<secondBAfterMindHit,
                        "Both opposite native minds must also damage the other real physical body directly");
                    helper.assertTrue(secondZombieMindA.getHealth()==secondZombieMindAProxyHealth
                            &&secondZombieMindB.getHealth()==secondZombieMindBProxyHealth,
                        "Direct physical-body attacks modified detached mind proxy health");

                    // A third-party attacker must also hit BOTH original
                    // physical bodies in both active pairs.
                    float chickenHealthBeforeExternal=chicken.getHealth();
                    float zombieHealthBeforeExternal=zombie.getHealth();
                    float secondAHealthBeforeExternal=secondZombieA.getHealth();
                    float secondBHealthBeforeExternal=secondZombieB.getHealth();
                    clearHurtCooldown(chicken);
                    clearHurtCooldown(zombie);
                    clearHurtCooldown(secondZombieA);
                    clearHurtCooldown(secondZombieB);
                    var opponentDamage=level.damageSources().mobAttack(opponent);
                    boolean chickenHit=chicken.hurtServer(level,opponentDamage,2.0f);
                    boolean zombieHit=zombie.hurtServer(level,opponentDamage,2.0f);
                    boolean secondAHit=secondZombieA.hurtServer(level,opponentDamage,2.0f);
                    boolean secondBHit=secondZombieB.hurtServer(level,opponentDamage,2.0f);
                    helper.assertTrue(chickenHit&&zombieHit&&secondAHit&&secondBHit
                            &&chicken.getHealth()<chickenHealthBeforeExternal
                            &&zombie.getHealth()<zombieHealthBeforeExternal
                            &&secondZombieA.getHealth()<secondAHealthBeforeExternal
                            &&secondZombieB.getHealth()<secondBHealthBeforeExternal,
                        "The opposing Skeleton could not independently damage every swapped physical body");
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
                    helper.assertTrue(hasSameGoals(secondZombieA,false,secondZombieAGoals)
                            &&hasSameGoals(secondZombieA,true,secondZombieATargets)
                            &&hasSameGoals(secondZombieB,false,secondZombieBGoals)
                            &&hasSameGoals(secondZombieB,true,secondZombieBTargets)
                            &&secondZombieA.isNoAi()==secondZombieANoAi
                            &&secondZombieB.isNoAi()==secondZombieBNoAi,
                        "Cancel did not restore the second pair's exact selectors and NoAI flags");
                    helper.assertTrue(!GoalMindRuntime.bodyReserved(chicken.getUUID())
                            &&!GoalMindRuntime.bodyReserved(zombie.getUUID())
                            &&!GoalMindRuntime.bodyReserved(secondZombieA.getUUID())
                            &&!GoalMindRuntime.bodyReserved(secondZombieB.getUUID()),
                        "Cancelled Mob bodies remained reserved");

                    System.out.println("FF-GAMEPLAY mob_mob_reciprocal_attack PASS"
                        +" bothMindProxiesAttackOppositeMind=true"
                        +" bothMindsAttackOppositeBodies=true proxyDamageRedirected=true"
                        +" independentPhysicalHealthPools=true");
                    System.out.println("FF-GAMEPLAY partial_native_swap_fails_closed PASS"
                        +" oppositeMindsDamageRealBodies=true externalDamageHitsBothBodies=true"
                        +" proxyHealthUntouched=true");
                    System.out.println("FF-GAMEPLAY native_goal_mind_exchange PASS"
                        +" reciprocalNativeGoals=true sameServer=true bothBodiesTick=true"
                        +" nativeTargetAcquisition=true nativeGoalProjection=true independentPairs=true"
                        +" exactGoalRestore=true");
                    chicken.discard();
                    zombie.discard();
                    opponent.discard();
                    secondZombieA.discard();
                    secondZombieB.discard();
                    helper.succeed();
                    } finally {
                        System.clearProperty("freakyfriday.debugProxy");
                    }
                });
            });
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
        BlockPos controllerPos=helper.absolutePos(new BlockPos(10,1,5));
        // Keep the target on the same side of the GameTest barrier as the
        // unregistered proxy. The test boundary itself is solid and must not
        // be mistaken for an AI LOS failure.
        BlockPos targetPos=helper.absolutePos(new BlockPos(13,1,5));
        controller.setPos(controllerPos.getX()+0.5,controllerPos.getY(),controllerPos.getZ()+0.5);
        Mob zombieTarget=EntityTypes.IRON_GOLEM.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombieTarget!=null,"Zombie mind needs a real Iron Golem target");
        zombieTarget.setNoAi(true);
        zombieTarget.setPos(targetPos.getX()+0.5,targetPos.getY(),targetPos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(zombieTarget),
            "Zombie mind's Iron Golem target failed to register");

        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null,"Player/Mob proxy test needs a real Zombie");
        BlockPos zombiePos=helper.absolutePos(new BlockPos(14,1,5));
        zombie.setPos(zombiePos.getX()+0.5,zombiePos.getY(),zombiePos.getZ()+0.5);
        zombie.setPersistenceRequired();
        helper.assertTrue(level.addFreshEntity(zombie),
            "Player-controlled Zombie body failed to register");

        makeDamageableSurvivalPlayer(controller);
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
        Mob mindProxy=PlayerMobPossession.mindProxyForGameTest(controller.getUUID());
        helper.assertTrue(mindProxy!=null,
            "Reciprocal Zombie mind proxy was not created for the original Player body");
        System.out.println("[FF-TARGET] PlayerMob "+targetProbe(level,mindProxy,zombieTarget)
            +" goals=["+targetGoalOwners(mindProxy)+"]");
        // No target is injected: the Zombie mind must acquire the registered
        // Iron Golem through its own native target selector.
        helper.assertTrue(PlayerMobPossession.hasSession(controller.getUUID())
                &&zombie.isNoAi()==true&&controller.isAlive(),
            "Reciprocal session did not assign separate controllers to both real bodies");

        for(int pulse=0;pulse<7;pulse++) {
            final long sequence=pulse+1L;
            helper.runAfterDelay(pulse*4,()->PlayerMobPossession.input(controller,
                new PlayerMobPossession.Input(1.0f,0.0f,false,0.0f,0.0f,sequence)));
        }
        helper.runAfterDelay(35,()->{
            helper.assertTrue(mindProxy.getTarget()==zombieTarget,
                "Zombie mind's native target selector did not acquire the registered Iron Golem");
            helper.assertTrue(controller.getX()>playerStartX+0.15,
                "Zombie mind did not move the original Player body toward the real Iron Golem target; "
                    +"startX="+playerStartX+" finalX="+controller.getX());
            helper.assertTrue(zombie.getZ()>zombieStartZ+0.15,
                "Player input did not independently move the occupied Zombie body; "
                    +"startZ="+zombieStartZ+" finalZ="+zombie.getZ());
            zombieTarget.discard();
            // Mock ServerPlayers retain their initial spawn-protection
            // window. Keep the live reciprocal session running until it has
            // elapsed before validating the Mob body's melee hit on its host.
            helper.runAfterDelay(30,()->{
                // The Player mind now inhabits the Zombie body. Its authenticated
                // attack input must hit the original Player body (currently driven
                // by the Zombie mind), not an invisible proxy. Face the body toward
                // the original Player and prime its physical attack cooldown.
                zombie.setPos(controller.getX()+1.35,controller.getY(),controller.getZ());
                zombie.setYRot(90.0f);
                zombie.yRotO=90.0f;
                zombie.setYHeadRot(90.0f);
                PlayerMobPossession.input(controller,
                    new PlayerMobPossession.Input(0,0,false,90.0f,0,99));
                float playerBeforeMindAttack=controller.getHealth();
                PlayerMobPossession.attack(controller,controller.getId(),99);
                helper.assertTrue(controller.getHealth()<playerBeforeMindAttack,
                    "Player mind in the Mob body could not damage the opposite mind's physical Player body");

                // The opposite physical Mob body can also hit the detached
                // Zombie mind itself. That proxy has no separate health pool:
                // damage must be redirected to the Player body hosting it.
                helper.runAfterDelay(11,()->{
                clearHurtCooldown(controller);
                float playerBeforeProxyHit=controller.getHealth();
                float proxyHealthBeforeProxyHit=mindProxy.getHealth();
                boolean proxyHitAccepted=zombie.doHurtTarget(level,mindProxy);
                System.out.println("[FF-ATTACK] physicalZombieHitProxy accepted="+proxyHitAccepted
                    +" zombiePos="+zombie.position()+" proxyPos="+mindProxy.position()
                    +" distanceSq="+zombie.distanceToSqr(mindProxy)
                    +" proxyHealth="+mindProxy.getHealth()
                    +" playerHealth="+controller.getHealth()
                    +" beforePlayerHealth="+playerBeforeProxyHit);
                helper.assertTrue(controller.getHealth()<playerBeforeProxyHit,
                    "Opposing Mob body could not damage the Player body hosting the detached mind"
                        +" accepted="+proxyHitAccepted+" player="+playerBeforeProxyHit
                        +"->"+controller.getHealth()+" proxyHealth="+proxyHealthBeforeProxyHit
                        +"->"+mindProxy.getHealth());
                helper.assertTrue(mindProxy.getHealth()==proxyHealthBeforeProxyHit,
                    "Damage to the detached mind proxy was not redirected to its real Player host");

                // In the other direction, the Zombie mind proxy performs a native
                // melee attack against the occupied Mob body. Proxy damage itself
                // is cancelled; the actual original Player body must execute it.
                helper.assertTrue(!mindProxy.isRemoved(),
                    "Reciprocal Zombie mind proxy disappeared during the live session");
                // The proxy copies its host Player body's view every tick.
                // Face that real body toward the Mob before the next native AI pass.
                controller.setYRot(-90.0f);
                controller.setXRot(0.0f);
                makeDamageableSurvivalPlayer(controller);
                // The mock Zombie may have an outstanding native hurt window
                // from earlier setup/fixture ticks; clear it before asking the
                // incoming Zombie mind to execute its reciprocal melee action.
                clearHurtCooldown(zombie);
                mindProxy.goalSelector.addGoal(0,new OneShotNativeAttackGoal(mindProxy,zombie));
                float mobBeforeMindAttack=zombie.getHealth();
                float proxyHealthBeforeAttack=mindProxy.getHealth();

                helper.runAfterDelay(10,()->{
                    helper.assertTrue(zombie.getHealth()<mobBeforeMindAttack,
                        "Zombie mind on the Player body could not damage the opposite mind's physical Mob body");
                    helper.assertTrue(mindProxy.getHealth()==proxyHealthBeforeAttack,
                        "The detached mind proxy was damaged instead of the original Player/Mob physical body");

                    // Clear only the vanilla damage-immunity timers. This is
                    // the same fixture setup used by the other combat gates;
                    // it avoids spending eleven extra GameTest ticks waiting
                    // for a mock player's nonstandard tick cadence.
                    clearHurtCooldown(controller);
                    clearHurtCooldown(zombie);
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
                        +" playerMindAttacksPlayerBody=true mobMindAttacksMobBody=true"
                        +" oppositeMobBodyHitsDetachedMind=true proxyDamageRedirected=true"
                        +" externalAttackerDamagesBothBodies=true independentHealthPools=true");
                    System.out.println("FF-GAMEPLAY player_mob_native_mind_proxy PASS"
                        +" sameServerNativeAI=true playerBodyDrivenByMobMind=true"
                        +" mobBodyDrivenByPlayerInput=true noMirroredMovement=true restored=true");
                    zombie.discard();
                    opponent.discard();
                    controller.discard();
                    zombieTarget.discard();
                    helper.succeed();
                });
                });
            });
        });
    }

}