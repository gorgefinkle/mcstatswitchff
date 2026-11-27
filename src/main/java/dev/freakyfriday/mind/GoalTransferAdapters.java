package dev.freakyfriday.mind;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.monster.RangedAttackMob;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.block.Block;

/**
 * Semantic ports for vanilla goals whose executable implementation is bound
 * to a different species. This is deliberately an adapter registry, not a
 * permissive reflection fallback: unrecognized owner-specific goals still
 * reject the complete exchange.
 *
 * Each port keeps the donor's behavior intent/configuration where it has a
 * portable meaning, but rebuilds the executable action against the recipient
 * body. A ported goal starts inactive so its new driver receives a normal
 * Goal.start() lifecycle on its first scheduler activation.
 */
public final class GoalTransferAdapters {
    private GoalTransferAdapters() {}

    /**
     * @return a recipient-bound replacement, or null when ordinary complete
     *         graph capture should handle the source goal unchanged
     */
    public static Goal adapt(Goal source, Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        // Generic block-removal goals can be reconstructed against any
        // pathfinding recipient when their concrete implementation carries
        // an incompatible species owner (for example an inner Zombie goal).
        if (source instanceof RemoveBlockGoal && hasIncompatibleMobOwner(source, recipient))
            return adaptBlockRemoval(source, recipient);

        // Creeper swelling is an offensive action, but its goal is bound to
        // the Creeper implementation. A non-Creeper gets ordinary attack
        // intent; a Creeper body can use its own SwellGoal driver.
        if (source instanceof SwellGoal && !(recipient instanceof Creeper))
            return adaptAttack(source, recipient);

        // Species-specialized melee goals (ZombieAttackGoal, Skeleton's
        // anonymous melee fallback, and analogous mod goals) are translated
        // only when their captured owner field cannot accept the recipient.
        if (source instanceof MeleeAttackGoal && hasIncompatibleMobOwner(source, recipient))
            return adaptAttack(source, recipient);

        // Bow goals require the recipient to implement the ranged attack
        // contract and physically hold a bow. Otherwise preserve the attack
        // intent and resolve an action supported by the receiving body.
        if (source instanceof RangedBowAttackGoal<?> ranged
                && (!(recipient instanceof RangedAttackMob) || !holdsBow(recipient))) {
            return adaptAttack(ranged, recipient);
        }

        return null;
    }

    private static boolean hasIncompatibleMobOwner(Goal source, Mob recipient) {
        for (Class<?> declaring = source.getClass();
             declaring != null && declaring != Object.class;
             declaring = declaring.getSuperclass()) {
            for (Field field : declaring.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())
                        || !Mob.class.isAssignableFrom(field.getType())
                        || !field.trySetAccessible()) continue;
                try {
                    Object value = field.get(source);
                    if (value instanceof Mob && !field.getType().isInstance(recipient))
                        return true;
                } catch (IllegalAccessException ignored) {
                    // The strict graph copier will reject inaccessible or
                    // incompatible fields before touching either selector.
                }
            }
        }
        return false;
    }

    private static Goal adaptBlockRemoval(Goal source, Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        if (!(source instanceof RemoveBlockGoal removeBlock)
                || !(recipient instanceof PathfinderMob pathfinder)) {
            throw new DynamicObjectGraph.Uncapturable(
                "Turtle-egg removal intent requires a PathfinderMob recipient.");
        }

        Block block = removeBlock.blockToRemove;
        int verticalRange = ((MoveToBlockGoal) source).verticalSearchRange;
        double speed = ((MoveToBlockGoal) source).speedModifier;
        double acceptedDistance = ((MoveToBlockGoal) source).acceptedDistance();

        RecipientRemoveBlockGoal replacement = new RecipientRemoveBlockGoal(
            block, pathfinder, speed, verticalRange, acceptedDistance);
        replacement.setFlags(source.getFlags().clone());
        return replacement;
    }

    private static Goal adaptAttack(Goal source, Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        if (!(recipient instanceof PathfinderMob pathfinder)) {
            throw new DynamicObjectGraph.Uncapturable(
                "Attack intent requires a PathfinderMob recipient; this body has no compatible pathfinding driver.");
        }

        final double speed;
        final boolean followTarget;
        final int rangedInterval;
        final float rangedRadius;

        if (source instanceof MeleeAttackGoal) {
            speed = ((MeleeAttackGoal) source).speedModifier;
            followTarget = ((MeleeAttackGoal) source).followingTargetEvenIfNotSeen;
        } else if (source instanceof RangedBowAttackGoal<?>) {
            speed = ((RangedBowAttackGoal<?>) source).speedModifier;
            followTarget = false;
        } else if (source instanceof SwellGoal) {
            speed = 1.0;
            followTarget = false;
        } else {
            throw new DynamicObjectGraph.Uncapturable(
                "No attack-intent adapter for " + source.getClass().getName());
        }

        if (source instanceof RangedBowAttackGoal<?> ranged) {
            rangedInterval = ranged.attackIntervalMin;
            rangedRadius = (float) Math.sqrt(ranged.attackRadiusSqr);
        } else {
            rangedInterval = recipient.level().getDifficulty() == Difficulty.HARD ? 20 : 40;
            rangedRadius = 15.0F;
        }

        RecipientAttackIntentGoal replacement = new RecipientAttackIntentGoal(
            pathfinder,
            speed,
            rangedInterval,
            rangedRadius,
            followTarget,
            source.getFlags().clone());
        return replacement;
    }

    private static boolean holdsBow(Mob mob) {
        return mob.getMainHandItem().is(net.minecraft.world.item.Items.BOW)
            || mob.getOffhandItem().is(net.minecraft.world.item.Items.BOW);
    }

    private static final class RecipientRemoveBlockGoal extends RemoveBlockGoal {
        private final double transferredAcceptedDistance;

        private RecipientRemoveBlockGoal(
                Block block,
                PathfinderMob recipient,
                double speed,
                int verticalRange,
                double acceptedDistance) {
            super(block, recipient, speed, verticalRange);
            this.transferredAcceptedDistance = acceptedDistance;
        }

        @Override
        public double acceptedDistance() {
            return transferredAcceptedDistance;
        }
    }
}
