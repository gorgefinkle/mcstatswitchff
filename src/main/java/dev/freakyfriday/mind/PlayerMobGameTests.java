package dev.freakyfriday.mind;

import dev.freakyfriday.FreakyFriday;
import java.util.UUID;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
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
        ZOMBIE_CHICKEN_BEHAVIOR=FUNCTIONS.register(
            "zombie_chicken_behavior_acceptance",
            ()->PlayerMobGameTests::verifyZombieChickenBehaviorAcceptance);

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
            "Rejected reciprocal pair did not clear its pending HUD selection");
        helper.assertTrue(!PlayerMobPossession.hasSession(ownerId)
                && !PlayerMobPossession.hasSession(UUID.randomUUID()),
            "One-way possession started despite reciprocal runtime being unavailable");
        helper.assertTrue(!chicken.isNoAi() && chicken.getBrain()==originalBrain
                && originalGoals.equals(List.copyOf(chicken.goalSelector.getAvailableGoals()))
                && originalTargets.equals(List.copyOf(chicken.targetSelector.getAvailableGoals()))
                && level.getEntity(bodyId)==chicken,
            "Rejected player/mob request mutated the original physical mob or its AI");
        System.out.println("FF-GAMEPLAY player_mob_reciprocal_gate PASS"
            +" oneWayPossessionStarted=false originalBodyAndAiUnchanged=true");
        chicken.discard();
        MindSwapSessions.clearOnLogout(ownerId);
        verifyPlayerBodyActionPort(helper);
        helper.succeed();
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

        float health=target.getHealth();
        var attacked=BodyActionGate.dispatch(port,new BodyActionGate.Intent(
            BodyActionGate.ATTACK_ENTITY,java.util.Map.of(
                "targetId",target.getUUID().toString())));
        helper.assertTrue(attacked.status()==BodyActionGate.Status.EXECUTED
                && target.getHealth()<health,
            "Player body did not execute the mapped attack through its native player attack pipeline");
        helper.assertTrue(level.getEntity(player.getUUID())==player,
            "Player action adapter replaced the original ServerPlayer body");
        System.out.println("FF-GAMEPLAY player_body_action_port PASS"
            +" nativeMove=true nativeLook=true nativeAttack=true originalPlayerPreserved=true");
        target.discard();
    }

    /**
     * Real collision-world behavioral regression: the SAME chicken changes
     * speed with its native movement attribute, while a stone wall continues
     * to block physical movement. No numerical mock movement is accepted.
     */
    private static void verifyNativeSpeedCollision(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null,"Chicken factory failed in native-speed regression");
        for(int x=1;x<=7;x++)for(int z=1;z<=9;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        // Full-height physical wall in the forward direction, unlike an
        // artificial setPos clamp. Real Entity.move must resolve its collision.
        for(int x=1;x<=7;x++)for(int y=1;y<=3;y++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,y,5)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos start=helper.absolutePos(new BlockPos(3,1,2));
        chicken.setPos(start.getX()+0.5,start.getY(),start.getZ()+0.5);
        var speed=chicken.getAttribute(
            net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED);
        helper.assertTrue(speed!=null,"Chicken lacks native movement speed");
        speed.setBaseValue(0.08);
        helper.assertTrue(level.addFreshEntity(chicken),
            "Real Chicken was not registered for speed/collision");
        UUID bodyId=chicken.getUUID(),owner=player.getUUID();
        float health=chicken.getHealth();
        double startZ=chicken.getZ();
        try {
            String error=PlayerMobPossession.begin(player,chicken);
            helper.assertTrue(error==null,"Native-speed possession rejected: "+error);
            // Keep input current throughout each physical tick window.
            for(int tick=1;tick<=8;tick++) {
                final int sequence=tick;
                helper.runAfterDelay(tick,()->PlayerMobPossession.input(player,
                    new PlayerMobPossession.Input(1,0,false,0,0,sequence)));
            }
            final double[] slow={Double.NaN};
            helper.runAfterDelay(10,()->{
                slow[0]=chicken.getZ()-startZ;
                helper.assertTrue(slow[0]>0.10 && slow[0]<1.1,
                    "Slow native speed failed: distance="+slow[0]);
                speed.setBaseValue(0.40);
            });
            for(int tick=11;tick<=23;tick++) {
                final int sequence=tick;
                helper.runAfterDelay(tick,()->PlayerMobPossession.input(player,
                    new PlayerMobPossession.Input(1,0,false,0,0,sequence)));
            }
            helper.runAfterDelay(25,()->{
                double total=chicken.getZ()-startZ;
                double fastDistance=total-slow[0];
                // The chicken's width must not enter the solid wall.
                double wallFront=helper.absolutePos(new BlockPos(3,1,5)).getZ();
                helper.assertTrue(Double.isFinite(slow[0])
                        && fastDistance>slow[0]+0.30,
                    "Native movement attribute did not increase travel: slow="
                        +slow[0]+" fast="+fastDistance);
                helper.assertTrue(chicken.getBoundingBox().maxZ<=wallFront+0.0001,
                    "Chicken passed through a solid vanilla block wall: box="
                        +chicken.getBoundingBox()+" wallZ="+wallFront);
                helper.assertTrue(level.getEntity(bodyId)==chicken
                        && Float.compare(health,chicken.getHealth())==0,
                    "Native-speed test replaced/damaged physical chicken");
                helper.assertTrue(PlayerMobPossession.end(player),
                    "Native-speed test cannot cancel active possession");
                helper.assertTrue(!chicken.isNoAi()
                        && !PlayerMobPossession.hasSession(owner),
                    "Native-speed cancel failed to restore AI and ownership");
                System.out.println("FF-GAMEPLAY native_body_speed PASS"
                    +" slow="+slow[0]+" accelerated="+fastDistance);
                System.out.println("FF-GAMEPLAY solid_wall_collision PASS"
                    +" maxZ="+chicken.getBoundingBox().maxZ+" wallZ="+wallFront);
                helper.succeed();
                chicken.discard();
            });
        } catch (RuntimeException failure) {
            PlayerMobPossession.clearOnLogout(owner);
            chicken.discard();
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
        // Keep the GameTest's real physics arena in player simulation range.
        // Otherwise a COMMAND-spawned Zombie can disappear before tick 57
        // in a playerless far-away GameTest structure.
        BlockPos playerAnchor=helper.absolutePos(new BlockPos(1,1,2));
        player.setPos(playerAnchor.getX()+0.5,playerAnchor.getY(),
            playerAnchor.getZ()+0.5);
        for (int x=1;x<=9;x++)for (int z=1;z<=6;z++) {
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            // Prevent daytime Zombie ignition from masquerading as fall damage.
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,17,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        }

        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob ocelot=EntityTypes.OCELOT.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null && ocelot!=null,
            "Native chicken/ocelot factories required");
        // Delay Zombie creation until its actual test window: a no-AI
        // entity still experiences gravity and would fall to its death
        // before reaching the test at tick 42.
        Mob[] bodies={chicken,ocelot};
        Mob[] zombieBody={null};
        for (int i=0;i<bodies.length;i++) {
            BlockPos origin=helper.absolutePos(new BlockPos(2+2*i,12,2));
            bodies[i].setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
            bodies[i].setNoAi(true); // Freeze ordinary wandering
            // NoAI does not disable gravity; hold comparison body up until
            // it actually becomes possessed to measure a fresh fall.
            if(i>0)bodies[i].setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(bodies[i]),
                "Native fall test could not register entity "+i);
        }
        double startY=chicken.getY();
        float ocelotHealth=ocelot.getHealth();
        float[] zombieHealth={0.0f};
        String error=PlayerMobPossession.begin(player,chicken);
        helper.assertTrue(error==null,"Chicken fall session rejected: "+error);
        final double[] chickenDrop={0.0};
        helper.runAfterDelay(12,()->{
            chickenDrop[0]=startY-chicken.getY();
            helper.assertTrue(chickenDrop[0]>0.2 && chickenDrop[0]<2.75,
                "Possessed chicken did not flap slowly: distance="+chickenDrop[0]);
            helper.assertTrue(PlayerMobPossession.end(player),"Cannot end chicken session");
            ocelot.setNoGravity(false);
            ocelot.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            String next=PlayerMobPossession.begin(player,ocelot);
            helper.assertTrue(next==null,"Ocelot fall session rejected: "+next);
        });
        helper.runAfterDelay(25,()->{
            double drop=startY-ocelot.getY();
            helper.assertTrue(drop>chickenDrop[0]+1.2,
                "Normal-gravity ocelot inherited chicken glide: chicken="
                    +chickenDrop[0]+" ocelot="+drop);
        });
        helper.runAfterDelay(42,()->{
            helper.assertTrue(ocelot.onGround(),
                "Normal-gravity ocelot did not land in 30 ticks");
            helper.assertTrue(Float.compare(ocelot.getHealth(),ocelotHealth)==0,
                "Vanilla fall-immune ocelot took damage in possessed body");
            helper.assertTrue(PlayerMobPossession.end(player),"Cannot end ocelot session");
            Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(zombie!=null,"Native zombie factory failed");
            BlockPos zombieOrigin=helper.absolutePos(new BlockPos(6,12,2));
            zombie.setPos(zombieOrigin.getX()+0.5,zombieOrigin.getY(),
                zombieOrigin.getZ()+0.5);
            // Let the world manager actually register the body before the
            // physical possession/fall starts. NoGravity is a test-only
            // staging guard and is switched off before the session begins.
            zombie.setNoGravity(true);
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            helper.assertTrue(level.addFreshEntity(zombie),"Zombie did not register");
            zombieHealth[0]=zombie.getHealth();
            zombieBody[0]=zombie;
        });
        helper.runAfterDelay(57,()->{
            Mob zombie=zombieBody[0];
            helper.assertTrue(zombie!=null && level.getEntity(zombie.getUUID())==zombie,
                "Zombie is not registered in real world before possession");
            BlockPos zombieOrigin=helper.absolutePos(new BlockPos(6,12,2));
            zombie.setPos(zombieOrigin.getX()+0.5,zombieOrigin.getY(),
                zombieOrigin.getZ()+0.5);
            zombie.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            zombie.setNoGravity(false);
            String next=PlayerMobPossession.begin(player,zombie);
            helper.assertTrue(next==null,"Zombie fall session rejected: "+next);
        });
        helper.runAfterDelay(87,()->{
            Mob zombie=zombieBody[0];
            helper.assertTrue(zombie!=null,"Zombie not constructed for fall test");
            double drop=startY-zombie.getY();
            helper.assertTrue(drop>chickenDrop[0]+1.2,
                "Zombie acquired chicken glide: chicken="+chickenDrop[0]
                    +" zombie="+drop);
        });
        helper.runAfterDelay(115,()->{
            Mob zombie=zombieBody[0];
            helper.assertTrue(zombie!=null,"Missing zombie when checking landing");
            // Entity.move may clear onGround after a zero-Y horizontal
            // update, and the Zombie may already have *died* on impact.
            // The behavior we actually require is that its physical health
            // decreased from genuine native fall damage, unlike Ocelot.
            helper.assertTrue(zombie.getHealth()<zombieHealth[0],
                "Zombie did not take vanilla fall damage; finalHealth="
                    +zombie.getHealth()+" initial="+zombieHealth[0]
                    +" onGround="+zombie.onGround()+" y="+zombie.getY());
            if (PlayerMobPossession.hasSession(player.getUUID())) {
                helper.assertTrue(PlayerMobPossession.end(player),
                    "Cannot end surviving zombie session");
            } else {
                helper.assertTrue(!zombie.isAlive() && player.isAlive(),
                    "Fall-dead zombie must have automatically returned controller");
            }
            System.out.println("FF-GAMEPLAY native_fall_profiles PASS"
                +" chickenSlowDrop="+chickenDrop[0]
                +" ocelotImmune=true zombieDamaged=true"
                +" noCrossBodyGlide=true");
            for (Mob mob:bodies)mob.discard();
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
                    && !PlayerMobPossession.hasSession(owner),
                "Incomplete reciprocal player/mob runtime did not fail closed");
            helper.assertTrue(!chicken.isNoAi(),
                "Rejected pair disabled native Chicken AI");
            System.out.println("FF-GAMEPLAY stale_selection_and_reciprocal_gate PASS");
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
        helper.assertTrue(zombie!=null&&chicken!=null,
            "Real Zombie and Chicken required");
        for(int x=1;x<=9;x++)for(int z=1;z<=9;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos a=helper.absolutePos(new BlockPos(3,1,3));
        BlockPos b=helper.absolutePos(new BlockPos(5,1,3));
        zombie.setPos(a.getX()+0.5,a.getY(),a.getZ()+0.5);
        chicken.setPos(b.getX()+0.5,b.getY(),b.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(zombie)&&level.addFreshEntity(chicken),
            "Native mobs were not registered");
        String failure=NativeBrainSwap.exchange(zombie,chicken);
        helper.assertTrue(failure==null,
            "Cross-species whole-mind swap NOT IMPLEMENTED: "+failure);
        // A successful exchange cannot be certified by swapping two pointers
        // alone. Future acceptance must prove actual hostile target pursuit
        // by the chicken and passive chicken-like behavior by the Zombie.
        helper.fail("Cross-species behavior is not yet certified by native AI execution");
    }

    /** A non-universal experimental path must never mutate either real mob. */
    private static void verifyPartialSwapRejected(GameTestHelper helper) {
        var level=helper.getLevel();
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null&&chicken!=null,
            "Fail-closed swap regression needs real Zombie and Chicken entities");
        BlockPos zombiePos=helper.absolutePos(new BlockPos(2,1,2));
        BlockPos chickenPos=helper.absolutePos(new BlockPos(5,1,2));
        zombie.setPos(zombiePos.getX()+0.5,zombiePos.getY(),zombiePos.getZ()+0.5);
        chicken.setPos(chickenPos.getX()+0.5,chickenPos.getY(),chickenPos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(zombie)&&level.addFreshEntity(chicken),
            "Fail-closed swap actors did not register");
        var zombieBrain=zombie.getBrain();
        var chickenBrain=chicken.getBrain();
        var zombieGoals=List.copyOf(zombie.goalSelector.getAvailableGoals());
        var chickenGoals=List.copyOf(chicken.goalSelector.getAvailableGoals());
        var zombieTargets=List.copyOf(zombie.targetSelector.getAvailableGoals());
        var chickenTargets=List.copyOf(chicken.targetSelector.getAvailableGoals());
        String rejection=NativeBrainSwap.exchange(zombie,chicken);
        helper.assertTrue(rejection!=null&&rejection.contains("Universal reciprocal native mind transfer"),
            "Incomplete native AI runtime was reported as a successful exchange: "+rejection);
        helper.assertTrue(zombie.getBrain()==zombieBrain&&chicken.getBrain()==chickenBrain,
            "Rejected exchange changed a physical body's Brain reference");
        helper.assertTrue(zombieGoals.equals(List.copyOf(zombie.goalSelector.getAvailableGoals()))
                && chickenGoals.equals(List.copyOf(chicken.goalSelector.getAvailableGoals()))
                && zombieTargets.equals(List.copyOf(zombie.targetSelector.getAvailableGoals()))
                && chickenTargets.equals(List.copyOf(chicken.targetSelector.getAvailableGoals())),
            "Rejected exchange mutated a native goal or target selector");
        System.out.println("FF-GAMEPLAY partial_native_swap_fails_closed PASS");
        zombie.discard();
        chicken.discard();
        helper.succeed();
    }

}
