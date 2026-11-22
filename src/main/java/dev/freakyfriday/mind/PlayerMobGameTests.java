package dev.freakyfriday.mind;

import dev.freakyfriday.FreakyFriday;
import java.util.UUID;
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

    private static void verifyPlayerControlsChicken(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND,
            new ItemStack(FreakyFriday.BOOK.get()));
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null,"Actual Chicken physical entity factory failed");
        // A reproducible, actual collision platform. The default empty
        // structure has no reliable survival floor for a walking Chicken.
        for(int x=1;x<=6;x++)for(int z=1;z<=7;z++)
            level.setBlockAndUpdate(
                helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
        BlockPos origin=helper.absolutePos(new BlockPos(2,1,2));
        chicken.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
        chicken.setNoAi(false);
        helper.assertTrue(level.addFreshEntity(chicken),
            "Real Chicken did not register in the GameTest level");
        UUID id=chicken.getUUID();
        float health=chicken.getHealth();
        double startZ=chicken.getZ();
        var ownerId=player.getUUID();
        try {
            // Same entrypoint as a real book right-click, then the actual
            // Brigadier command that Ctrl+S sends from the player's client.
            MindSwapSessions.selectFromInteraction(player,chicken);
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)
                    ==MindSwapSessions.SELECTION_TIMEOUT_TICKS,
                "Selecting first mob did not start 8-second HUD selection countdown");
            var commandSource=level.getServer().createCommandSourceStack().withEntity(player);
            try {
                helper.assertTrue(level.getServer().getCommands().getDispatcher().execute(
                    "freakyfriday self",commandSource)==1,
                    "Ctrl+S /freakyfriday self command rejected by real Brigadier");
            } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                throw new AssertionError("Real self selection command failed",e);
            }
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)==0,
                "Completing mob/player pairing did not clear first-body HUD");
            System.out.println("FF-GAMEPLAY selection_hud_completed_pair_clears PASS");
            helper.assertTrue(PlayerMobPossession.hasSession(ownerId)
                    && id.equals(PlayerMobPossession.possessedBody(ownerId)),
                "Book pair did NOT activate genuine server-side Chicken control");
            helper.assertTrue(chicken.isNoAi(),
                "Original Chicken AI continued fighting player's movement");
            helper.assertTrue(level.getEntity(id)==chicken,
                "Playable Chicken body was replaced instead of possessed");

            helper.assertTrue(!PlayerMobPossession.hasSession(UUID.randomUUID()),
                "Unrelated player inherited another account's body");
            boolean rejected=false;
            try {
                new PlayerMobPossession.Input(Float.NaN,0,false,0,0,1);
            } catch (IllegalArgumentException badPacket) {
                rejected=true;
            }
            helper.assertTrue(rejected,"Unsafe NaN input was accepted");

            for(int i=1;i<=7;i++) {
                final int seq=i;
                helper.runAfterDelay(i*2,()->PlayerMobPossession.input(player,
                    new PlayerMobPossession.Input(1,0,false,0,0,seq)));
            }
            helper.runAfterDelay(25,()->{
                helper.assertTrue(chicken.getZ()>startZ+0.35,
                    "Chicken did NOT move under authenticated player's input"
                    +" startZ="+startZ+" actualZ="+chicken.getZ());
                helper.assertTrue(level.getEntity(id)==chicken
                    && Float.compare(health,chicken.getHealth())==0,
                    "Possession changed physical Chicken identity or health");
                helper.assertTrue(PlayerMobPossession.hasSession(ownerId),
                    "Possession session dropped during actual movement");
                try {
                    helper.assertTrue(level.getServer().getCommands().getDispatcher().execute(
                        "freakyfriday cancel",commandSource)==1,
                        "Real return-to-body /cancel command rejected by Brigadier");
                } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
                    throw new AssertionError("Real possession cancel command failed",e);
                }
                helper.assertTrue(!PlayerMobPossession.hasSession(ownerId),
                    "Cancel failed to end playable possession");
                helper.assertTrue(!chicken.isNoAi(),
                    "Cancel did not restore genuine Chicken AI");
                helper.assertTrue(level.getEntity(id)==chicken,
                    "Cancel discarded the original physical Chicken");
                System.out.println("FF-GAMEPLAY player_chicken_movement PASS"
                    +" actualDistance="+(chicken.getZ()-startZ)
                    +" realPhysicalEntity=true playerInput=true");
                System.out.println("FF-GAMEPLAY player_chicken_cancel_restore PASS");
                System.out.println("FF-GAMEPLAY player_mob_possession PASS"
                    +" cameraClientIntegrationNotProven=true"
                    +" chickenMindInPlayerBody=false");
                helper.succeed();
                chicken.discard();
                MindSwapSessions.clearOnLogout(ownerId);
            });
        } catch (RuntimeException failure) {
            MindSwapSessions.clearOnLogout(ownerId);
            chicken.discard();
            throw failure;
        }
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
    private static void verifyFallProfiles(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
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
        // Do not spawn the second candidate 162 ticks early. A Skeleton can
        // burn or despawn between those ticks, causing an unrelated failure
        // when testing expiration/reselection.
        UUID owner=player.getUUID();
        MindSwapSessions.select(player,player);
        helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)
                ==MindSwapSessions.SELECTION_TIMEOUT_TICKS,
            "First selected body failed to enter HUD countdown state");
        helper.assertTrue(!PlayerMobPossession.hasSession(owner),"First self was interpreted as completed swap");
        helper.runAfterDelay((int)MindSwapSessions.SELECTION_TIMEOUT_TICKS+2,()->{
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)==0,
                "Timed-out first selection remained active after eight seconds");
            Mob skeleton=EntityTypes.SKELETON.create(level,EntitySpawnReason.COMMAND);
            helper.assertTrue(skeleton!=null,"Cannot spawn fresh test Skeleton");
            BlockPos floor=helper.absolutePos(new BlockPos(2,0,2));
            level.setBlockAndUpdate(floor,
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            skeleton.setPos(floor.getX()+0.5,floor.getY()+1,floor.getZ()+0.5);
            skeleton.setNoAi(true);
            skeleton.setNoGravity(true);
            helper.assertTrue(level.addFreshEntity(skeleton),"Fresh Skeleton not registered");
            MindSwapSessions.selectFromInteraction(player,skeleton);
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)
                    ==MindSwapSessions.SELECTION_TIMEOUT_TICKS,
                "Next first selected mob did not restart HUD countdown");
            helper.assertTrue(!PlayerMobPossession.hasSession(owner),
                "Stale self unexpectedly caused an automatic swap");
            System.out.println("FF-GAMEPLAY stale_self_selection_cannot_swap PASS");
            MindSwapSessions.select(player,player);
            helper.assertTrue(skeleton.getUUID().equals(PlayerMobPossession.possessedBody(owner)),
                "Explicit second body did not start playable possession");
            MindSwapSessions.cancel(player);
            helper.assertTrue(MindSwapSessions.selectionRemainingTicks(player)==0,
                "Cancel failed to dismiss first-body selection");
            helper.assertTrue(!PlayerMobPossession.hasSession(owner),"Cancel failed");
            System.out.println("FF-GAMEPLAY selection_hud_expire_and_cancel PASS");
            System.out.println("FF-GAMEPLAY explicit_two_body_selection PASS");
            skeleton.discard();
            helper.succeed();
        });
    }

    /** Zombie-origin attack must damage actual original player, not kick them. */
    private static void verifyMobOriginAttack(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer player=helper.makeMockServerPlayerInLevel();
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(zombie!=null,"Cannot spawn Zombie");
        for(int x=1;x<=8;x++)for(int z=1;z<=8;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
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
        helper.runAfterDelay(3,()->{
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

}
