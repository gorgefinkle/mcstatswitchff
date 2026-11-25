package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real-server proof that native Mob goal brains swap on the real bodies. */
public final class NativeGoalMindGameTests {
    private NativeGoalMindGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        RECIPROCAL_GOALS = FUNCTIONS.register(
            "native_goal_mind_exchange",()->NativeGoalMindGameTests::verify);

    private record GoalRef(int priority,Goal goal) {}

    private static List<GoalRef> read(Mob body,boolean targets) {
        List<GoalRef> result=new ArrayList<>();
        var selector=targets?body.targetSelector:body.goalSelector;
        for(WrappedGoal entry:selector.getAvailableGoals())
            result.add(new GoalRef(entry.getPriority(),entry.getGoal()));
        return List.copyOf(result);
    }

    private static boolean same(Mob body,boolean targets,List<GoalRef> original) {
        List<GoalRef> actual=read(body,targets);
        if(actual.size()!=original.size()) return false;
        for(GoalRef expected:original) {
            if(actual.stream().noneMatch(found->
                    found.priority()==expected.priority()&&found.goal()==expected.goal()))
                return false;
        }
        return true;
    }

    private static void verify(GameTestHelper helper) {
        var level=helper.getLevel();
        Mob chicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob zombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        Mob secondChicken=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob secondZombie=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(chicken!=null&&zombie!=null&&secondChicken!=null&&secondZombie!=null,
            "Native AI exchange test entities could not be created");

        var origin=helper.absolutePos(new net.minecraft.core.BlockPos(5,2,5));
        chicken.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
        zombie.setPos(origin.getX()+3.5,origin.getY(),origin.getZ()+0.5);
        secondChicken.setPos(origin.getX()+7.5,origin.getY(),origin.getZ()+0.5);
        secondZombie.setPos(origin.getX()+10.5,origin.getY(),origin.getZ()+0.5);
        helper.assertTrue(level.addFreshEntity(chicken)&&level.addFreshEntity(zombie)
                &&level.addFreshEntity(secondChicken)&&level.addFreshEntity(secondZombie),
            "Native AI exchange test entities failed to join the live server level");

        List<GoalRef> chickenGoals=read(chicken,false);
        List<GoalRef> chickenTargets=read(chicken,true);
        List<GoalRef> zombieGoals=read(zombie,false);
        List<GoalRef> zombieTargets=read(zombie,true);
        boolean chickenNoAi=chicken.isNoAi(),zombieNoAi=zombie.isNoAi();
        helper.assertTrue(!chickenGoals.isEmpty()&&!zombieGoals.isEmpty()
                &&GoalMindRuntime.supports(chicken,zombie),
            "Chicken/Zombie must expose transferable native goal selectors in this target profile");

        UUID requester=UUID.randomUUID();
        String firstError=GoalMindRuntime.begin(requester,chicken,zombie);
        helper.assertTrue(firstError==null,
            "Live Chicken↔Zombie goal exchange failed: "+firstError);
        helper.assertTrue(GoalMindRuntime.activeSessions(requester)==1
                &&GoalMindRuntime.bodyReserved(chicken.getUUID())
                &&GoalMindRuntime.bodyReserved(zombie.getUUID()),
            "The live reciprocal session did not reserve exactly its two physical bodies");
        helper.assertTrue(!chicken.isRemoved()&&!zombie.isRemoved()
                &&chicken.getUUID()!=null&&zombie.getUUID()!=null
                &&chicken.getType().equals(EntityTypes.CHICKEN)
                &&zombie.getType().equals(EntityTypes.ZOMBIE),
            "The exchange replaced or removed a physical body");
        helper.assertTrue(chicken.isNoAi()==chickenNoAi&&zombie.isNoAi()==zombieNoAi,
            "The exchange must not freeze either body's native tick");
        helper.assertTrue(!same(chicken,false,chickenGoals)
                &&!same(zombie,false,zombieGoals),
            "The two original goal sets were not independently replaced");

        String occupiedError=GoalMindRuntime.begin(requester,chicken,secondZombie);
        helper.assertTrue(occupiedError!=null,
            "A physical body already assigned to a mind was accepted into another pair");

        String secondError=GoalMindRuntime.begin(requester,secondChicken,secondZombie);
        helper.assertTrue(secondError==null&&GoalMindRuntime.activeSessions(requester)==2,
            "One requester could not manage two independent Mob pairs: "+secondError);

        helper.runAfterDelay(5,()->{
            helper.assertTrue(chicken.isAlive()&&zombie.isAlive()
                    &&secondChicken.isAlive()&&secondZombie.isAlive(),
                "Native Mob bodies stopped ticking or died during reciprocal ownership");
            helper.assertTrue(GoalMindRuntime.cancelByAccount(requester)
                    &&GoalMindRuntime.activeSessions(requester)==0
                    &&!GoalMindRuntime.bodyReserved(chicken.getUUID())
                    &&!GoalMindRuntime.bodyReserved(zombie.getUUID())
                    &&!GoalMindRuntime.bodyReserved(secondChicken.getUUID())
                    &&!GoalMindRuntime.bodyReserved(secondZombie.getUUID()),
                "Cancel did not release all independent body reservations");
            helper.assertTrue(same(chicken,false,chickenGoals)
                    &&same(chicken,true,chickenTargets)
                    &&same(zombie,false,zombieGoals)
                    &&same(zombie,true,zombieTargets),
                "Cancel did not restore the exact original Chicken/Zombie goal graphs");
            chicken.discard();zombie.discard();secondChicken.discard();secondZombie.discard();
            System.out.println("FF-GAMEPLAY native_goal_mind_exchange PASS"
                +" liveSameServerAI=true bodyIdentityPreserved=true bothNativeTicksActive=true"
                +" independentPairs=true transactionalReturn=true");
            // Chain the real reciprocal Mob↔Mob combat gate from this
            // already-executed GameTest so CI cannot silently skip it.
            PlayerMobGameTests.verifyMobMobReciprocalAttack(helper);
        });
    }
}
