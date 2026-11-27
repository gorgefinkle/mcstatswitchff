package dev.freakyfriday.mind;

import java.util.EnumSet;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.item.Items;

/**
 * A transferable attack intention whose execution is selected from the
 * recipient body's real capabilities and equipment.
 *
 * The donor contributes attack cadence, movement speed, pursuit policy and
 * scheduler flags. The recipient contributes the actual attack mechanism:
 * bow-capable bodies holding a bow use their native ranged attack contract;
 * all other PathfinderMob bodies use native melee attack behavior.
 *
 * This intentionally does not clone a Skeleton-only or Zombie-only Goal.
 * The selected delegate is created lazily on the server thread and is always
 * bound to this physical body.
 */
public final class RecipientAttackIntentGoal extends Goal {
    private final PathfinderMob body;
    private final double speedModifier;
    private final int rangedAttackInterval;
    private final float rangedAttackRadius;
    private final boolean meleeFollowingTargetEvenIfNotSeen;

    private Goal activeDriver;
    private boolean driverStarted;
    private boolean rangedMode;

    public RecipientAttackIntentGoal(
            PathfinderMob body,
            double speedModifier,
            int rangedAttackInterval,
            float rangedAttackRadius,
            boolean meleeFollowingTargetEvenIfNotSeen,
            EnumSet<Goal.Flag> flags) {
        this.body = body;
        this.speedModifier = speedModifier;
        this.rangedAttackInterval = Math.max(1, rangedAttackInterval);
        this.rangedAttackRadius = Math.max(1.0F, rangedAttackRadius);
        this.meleeFollowingTargetEvenIfNotSeen = meleeFollowingTargetEvenIfNotSeen;
        setFlags(flags.clone());
    }

    private boolean canUseRangedBodyAction() {
        return body instanceof RangedAttackMob
            && (body.getMainHandItem().is(Items.BOW)
                || body.getOffhandItem().is(Items.BOW));
    }

    private Goal createDriver(boolean ranged) {
        if (ranged) {
            @SuppressWarnings("unchecked")
            Mob rangedBody = (Mob) body;
            return new RangedBowAttackGoal<>(
                (Mob & RangedAttackMob) rangedBody,
                speedModifier,
                rangedAttackInterval,
                rangedAttackRadius);
        }
        return new MeleeAttackGoal(
            body, speedModifier, meleeFollowingTargetEvenIfNotSeen);
    }

    private Goal driverForCurrentBody() {
        boolean wantsRanged = canUseRangedBodyAction();
        if (activeDriver == null || !driverStarted && wantsRanged != rangedMode) {
            activeDriver = createDriver(wantsRanged);
            rangedMode = wantsRanged;
        }
        return activeDriver;
    }

    @Override
    public boolean canUse() {
        return driverForCurrentBody().canUse();
    }

    @Override
    public boolean canContinueToUse() {
        return activeDriver != null && activeDriver.canContinueToUse();
    }

    @Override
    public void start() {
        Goal driver = driverForCurrentBody();
        driver.start();
        driverStarted = true;
    }

    @Override
    public void stop() {
        if (activeDriver != null && driverStarted) activeDriver.stop();
        activeDriver = null;
        driverStarted = false;
    }

    @Override
    public void tick() {
        Goal driver = driverForCurrentBody();
        if (!driverStarted) {
            driver.start();
            driverStarted = true;
        }
        driver.tick();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return activeDriver != null && activeDriver.requiresUpdateEveryTick();
    }

    @Override
    public boolean isInterruptable() {
        return activeDriver == null || activeDriver.isInterruptable();
    }
}
