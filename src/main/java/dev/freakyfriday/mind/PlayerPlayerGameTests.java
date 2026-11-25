package dev.freakyfriday.mind;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * REAL two-ServerPlayer reciprocal movement, not just command route metadata.
 * Both physical player entities are moved by the OPPOSITE player's input via
 * Minecraft's collision resolver and can independently end both sessions.
 *
 * Mock GameTest players have no graphical camera or real connected clients:
 * this test does not claim a visual camera, inventory, or combat transfer.
 */
public final class PlayerPlayerGameTests {
    private PlayerPlayerGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");
    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        PLAYER_EXCHANGE = FUNCTIONS.register(
            "reciprocal_player_control",()->PlayerPlayerGameTests::verify);

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        BODY_DEATH_EJECTS_BOTH=FUNCTIONS.register(
            "player_death_ejects_both",()->PlayerPlayerGameTests::verifyDeathEjectsBoth);

    private static void verify(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer fred=helper.makeMockServerPlayerInLevel();
        ServerPlayer sally=helper.makeMockServerPlayerInLevel();
        helper.assertTrue(fred!=null && sally!=null &&fred!=sally
                && !fred.getUUID().equals(sally.getUUID()),
            "Two unique real ServerPlayer entities required for reciprocal control");
        for(int x=1;x<=11;x++) for(int z=1;z<=11;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                Blocks.STONE.defaultBlockState());
        BlockPos fredOrigin=helper.absolutePos(new BlockPos(3,1,3));
        BlockPos sallyOrigin=helper.absolutePos(new BlockPos(7,1,3));
        fred.setPos(fredOrigin.getX()+0.5,fredOrigin.getY(),fredOrigin.getZ()+0.5);
        sally.setPos(sallyOrigin.getX()+0.5,sallyOrigin.getY(),sallyOrigin.getZ()+0.5);
        UUID fredId=fred.getUUID(),sallyId=sally.getUUID();
        double fredX=fred.getX(),fredZ=fred.getZ();
        double sallyX=sally.getX(),sallyZ=sally.getZ();
        fred.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        sally.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
        fred.getAbilities().invulnerable=false;
        sally.getAbilities().invulnerable=false;
        Mob opponent=EntityTypes.SKELETON.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(opponent!=null,"Player-pair damage test needs an opposing Skeleton");
        opponent.setNoAi(true);
        BlockPos opponentPos=helper.absolutePos(new BlockPos(9,1,7));
        opponent.setPos(opponentPos.getX()+0.5,opponentPos.getY(),opponentPos.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(opponent),
            "Opposing Skeleton failed to register in Player↔Player test");
        float fredHealth=fred.getHealth(),sallyHealth=sally.getHealth();
        try {
            String failed=PlayerPlayerExchange.begin(fred,sally);
            helper.assertTrue(failed==null,
                "Immediate no-consent player-body swap rejected: "+failed);
            helper.assertTrue(PlayerPlayerExchange.active(fredId)
                    && PlayerPlayerExchange.active(sallyId)
                    && sallyId.equals(PlayerPlayerExchange.occupied(fredId))
                    && fredId.equals(PlayerPlayerExchange.occupied(sallyId)),
                "Both original physical players were not mapped to opposite controllers");
            helper.assertTrue(PlayerCommandBodyRouting.hasRoute(fredId)
                    && PlayerCommandBodyRouting.hasRoute(sallyId),
                "Reciprocal physical control did not activate command body routing");
            // Distinct forward vectors prove the OTHER account drives each
            // unchanged real player body. No packet can specify target ID.
            for (int tick=1;tick<=25;tick++) {
                final int seq=tick;
                helper.runAfterDelay(tick,()->{
                    PlayerPlayerExchange.input(fred,new PlayerMobPossession.Input(
                        1,0,false,0,0,seq));        // Fred moves Sally +Z
                    PlayerPlayerExchange.input(sally,new PlayerMobPossession.Input(
                        1,0,false,-90,0,seq));      // Sally moves Fred +X
                });
            }
            helper.runAfterDelay(29,()->{
                helper.assertTrue(fred.getX()>fredX+0.40,
                    "Sally's input did not actually move Fred body: before="
                        +fredX+" after="+fred.getX());
                helper.assertTrue(sally.getZ()>sallyZ+0.40,
                    "Fred's input did not actually move Sally body: before="
                        +sallyZ+" after="+sally.getZ());
                helper.assertTrue(Math.abs(fred.getZ()-fredZ)<1.0
                        && Math.abs(sally.getX()-sallyX)<1.0,
                    "Opposite controllers' movement was routed to incorrect axes");
                helper.assertTrue(fred.getUUID().equals(fredId)
                        && sally.getUUID().equals(sallyId),
                    "Reciprocal control replaced an actual physical player");
                // Each incoming mind must be able to attack the OTHER
                // participant's real physical body through its own authenticated
                // account route: Fred's mind attacks Fred's original body from
                // Sally's body; Sally's mind does the reciprocal attack.
                double fredDx=fred.getX()-sally.getX();
                double fredDz=fred.getZ()-sally.getZ();
                float fredAttackYaw=(float)Math.toDegrees(Math.atan2(-fredDx,fredDz));
                PlayerPlayerExchange.input(fred,new PlayerMobPossession.Input(
                    0,0,false,fredAttackYaw,0,26));
                float fredBeforeMindAttack=fred.getHealth();
                PlayerPlayerExchange.attack(fred,fred.getId(),1);
                helper.assertTrue(fred.getHealth()<fredBeforeMindAttack,
                    "Fred's mind in Sally's body could not attack Fred's original physical body"
                        +" actorPos="+sally.position()+" targetPos="+fred.position()
                        +" distance="+sally.distanceTo(fred)+" yaw="+sally.getYRot()
                        +" sight="+sally.hasLineOfSight(fred)
                        +" dot="+sally.getViewVector(1.0f).dot(
                            fred.getBoundingBox().getCenter().subtract(sally.getEyePosition()).normalize())
                        +" candidateId="+fred.getId()+" actorId="+sally.getId()
                        +" targetAlive="+fred.isAlive()+" targetRemoved="+fred.isRemoved()
                        +" resolved="+(PossessionCombat.findValidMeleeTarget(
                            level,sally,fred.getId(),fred)==fred));

                double sallyDx=sally.getX()-fred.getX();
                double sallyDz=sally.getZ()-fred.getZ();
                float sallyAttackYaw=(float)Math.toDegrees(Math.atan2(-sallyDx,sallyDz));
                PlayerPlayerExchange.input(sally,new PlayerMobPossession.Input(
                    0,0,false,sallyAttackYaw,0,26));
                float sallyBeforeMindAttack=sally.getHealth();
                PlayerPlayerExchange.attack(sally,sally.getId(),1);
                helper.assertTrue(sally.getHealth()<sallyBeforeMindAttack,
                    "Sally's mind in Fred's body could not attack Sally's original physical body"
                        +" actorPos="+fred.position()+" targetPos="+sally.position()
                        +" distance="+fred.distanceTo(sally)+" yaw="+fred.getYRot()
                        +" sight="+fred.hasLineOfSight(sally)
                        +" dot="+fred.getViewVector(1.0f).dot(
                            sally.getBoundingBox().getCenter().subtract(fred.getEyePosition()).normalize())
                        +" candidateId="+sally.getId()+" actorId="+fred.getId()
                        +" targetAlive="+sally.isAlive()+" targetRemoved="+sally.isRemoved()
                        +" resolved="+(PossessionCombat.findValidMeleeTarget(
                            level,fred,sally.getId(),sally)==sally));

                // ServerPlayer spawn protection is still active in the first
                // movement window. Wait beyond it and the melee hurt cooldown,
                // then test a third-party attacker against both physical bodies.
                helper.runAfterDelay(50,()->{
                    var opponentDamage=level.damageSources().mobAttack(opponent);
                    float fredBeforeExternal=fred.getHealth();
                    float sallyBeforeExternal=sally.getHealth();
                    boolean fredHit=fred.hurtServer(level,opponentDamage,2.0f);
                    boolean sallyHit=sally.hurtServer(level,opponentDamage,2.0f);
                    helper.assertTrue(fredHit&&sallyHit
                            &&fred.getHealth()<fredBeforeExternal
                            &&sally.getHealth()<sallyBeforeExternal,
                        "The opposing Skeleton could not independently damage both original player bodies"
                            +" fredHit="+fredHit+" sallyHit="+sallyHit
                            +" fredHealth="+fred.getHealth()+" sallyHealth="+sally.getHealth());
                    helper.assertTrue(PlayerPlayerExchange.active(fredId)
                            &&PlayerPlayerExchange.active(sallyId),
                        "Nonfatal opponent damage incorrectly ended reciprocal Player↔Player control");
                    helper.assertTrue(PlayerPlayerExchange.cancel(sally),
                        "The other swapped player cannot end the exchange");
                    helper.assertTrue(!PlayerPlayerExchange.active(fredId)
                            && !PlayerPlayerExchange.active(sallyId)
                            && !PlayerCommandBodyRouting.hasRoute(fredId)
                            && !PlayerCommandBodyRouting.hasRoute(sallyId),
                        "Cancel left body control or command routing swapped");
                    System.out.println("FF-GAMEPLAY player_player_reciprocal_attack PASS"
                        +" fredMindDamagesFredBody=true sallyMindDamagesSallyBody=true"
                        +" externalAttackerDamagesBoth=true independentHealthPools=true");
                    System.out.println("FF-GAMEPLAY player_player_reciprocal_movement PASS"
                        +" fredBodyFromSally="+(fred.getX()-fredX)
                        +" sallyBodyFromFred="+(sally.getZ()-sallyZ)
                        +" intactPhysicalPlayers=true opponentDamagesBothBodies=true independentHealthPools=true");
                    System.out.println("FF-GAMEPLAY player_player_cancel_restores_both PASS"
                        +" immediateExchange=true noConsentRequired=true");
                    helper.succeed();
                    opponent.discard();
                });
            });
        } catch(RuntimeException failure) {
            PlayerPlayerExchange.clearOnLogout(fredId);
            PlayerPlayerExchange.clearOnLogout(sallyId);
            throw failure;
        }
    }

    /**
     * Killing either occupied original ServerPlayer body must immediately
     * dissolve BOTH reciprocal routes. Survivor regains their own physical
     * body rather than remaining inside somebody's dying/respawning player.
     */
    private static void verifyDeathEjectsBoth(GameTestHelper helper) {
        var level=helper.getLevel();
        ServerPlayer fred=helper.makeMockServerPlayerInLevel();
        ServerPlayer sally=helper.makeMockServerPlayerInLevel();
        helper.assertTrue(fred!=null && sally!=null && fred!=sally
                && !fred.getUUID().equals(sally.getUUID()),
            "Death-ejection needs two distinct real player bodies");
        for (int x=1;x<=8;x++)for (int z=1;z<=5;z++)
            level.setBlockAndUpdate(helper.absolutePos(new BlockPos(x,0,z)),
                Blocks.STONE.defaultBlockState());
        BlockPos a=helper.absolutePos(new BlockPos(2,1,2));
        BlockPos b=helper.absolutePos(new BlockPos(5,1,2));
        fred.setPos(a.getX()+0.5,a.getY(),a.getZ()+0.5);
        sally.setPos(b.getX()+0.5,b.getY(),b.getZ()+0.5);
        UUID first=fred.getUUID(),second=sally.getUUID();
        String error=PlayerPlayerExchange.begin(fred,sally);
        helper.assertTrue(error==null,"Death-ejection swap rejected: "+error);
        helper.assertTrue(PlayerPlayerExchange.active(first)
                && PlayerPlayerExchange.active(second),
            "Reciprocal controllers were not both active before death");
        // A real player's LivingEntity.isAlive() becomes false from zero
        // health. The server tick fallback must clear both even if another
        // mod intercepted the normal LivingDeathEvent.
        helper.runAfterDelay(3,()->fred.setHealth(0.0f));
        helper.runAfterDelay(6,()->{
            helper.assertTrue(!fred.isAlive(),"Test player body's death not registered");
            helper.assertTrue(sally.isAlive(),"Other physical player incorrectly died");
            helper.assertTrue(!PlayerPlayerExchange.active(first)
                    && !PlayerPlayerExchange.active(second)
                    && !PlayerCommandBodyRouting.hasRoute(first)
                    && !PlayerCommandBodyRouting.hasRoute(second),
                "Death of one occupied player failed to eject BOTH controllers");
            System.out.println("FF-GAMEPLAY player_death_ejects_both PASS"
                +" deadBody="+first+" survivorBody="+second);
            helper.succeed();
        });
    }

}
