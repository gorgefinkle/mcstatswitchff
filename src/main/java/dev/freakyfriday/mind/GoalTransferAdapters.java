package dev.freakyfriday.mind;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.AvoidEntityGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveThroughVillageGoal;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.minecraft.world.entity.ai.goal.RangedBowAttackGoal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.SwellGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
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
        // NearestAttackableTargetGoal owns mutable TargetingConditions built
        // from recipient follow range. Recreate that helper using the original
        // target class, cadence, visibility/reach settings and validated selector.
        if (source.getClass() == NearestAttackableTargetGoal.class) {
            return adaptNearestAttackable((NearestAttackableTargetGoal<?>) source, recipient);
        }

        // MoveThroughVillageGoal accepts a BooleanSupplier for whether
        // the body can deal with doors. Vanilla Zombie passes this::canBreakDoors,
        // which is an owner-bound lambda. Recreate that capability query for the
        // recipient rather than carrying the donor's captured this reference.
        if (source.getClass() == MoveThroughVillageGoal.class) {
            return adaptMoveThroughVillage((MoveThroughVillageGoal) source, recipient);
        }

        // The vanilla AvoidEntityGoal owns a TargetingConditions instance
        // assembled from two predicates. Reconstruct that helper instead of
        // cloning private TargetingConditions internals or keeping its old
        // path/navigation objects. Only the base goal is translated here;
        // specialized subclasses may add body-specific semantics.
        if (source.getClass() == AvoidEntityGoal.class) {
            return adaptAvoidEntity((AvoidEntityGoal<?>) source, recipient);
        }

        // PanicGoal's normal graph is portable: its default damage-tag
        // function captures only a TagKey constant, which the graph copier
        // now validates and safely shares. More specialized panic subclasses
        // still use their full owner-field graph and fail closed if needed.

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
        // Non-static member goals carry their owning species in a synthetic
        // this$0 field. Class metadata exposes that enclosing type directly,
        // even when reflection policy hides the synthetic field itself.
        Class<?> enclosing = source.getClass().getEnclosingClass();
        if (enclosing != null && Mob.class.isAssignableFrom(enclosing)
                && !enclosing.isInstance(recipient)) return true;

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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Goal adaptNearestAttackable(
            NearestAttackableTargetGoal<?> source, Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        DynamicObjectGraph.requireShareableDependency(
            source.targetConditions.selector, "NearestAttackableTargetGoal.targetSelector");

        // The native goal stores reducedTickDelay(input) in randomInterval.
        // Doubling this normalized value lets the constructor's reduction
        // reproduce the same tick cadence exactly for non-negative intervals.
        int constructorInterval = source.randomInterval * 2;
        NearestAttackableTargetGoal replacement = new NearestAttackableTargetGoal(
            recipient,
            source.targetType,
            constructorInterval,
            source.mustSee,
            source.mustReach,
            source.targetConditions.selector);
        replacement.setUnseenMemoryTicks(source.unseenMemoryTicks);
        replacement.setFlags(source.getFlags().clone());
        return replacement;
    }

    private static Goal adaptMoveThroughVillage(
            MoveThroughVillageGoal source, Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        if (!(recipient instanceof PathfinderMob pathfinder)) {
            throw new DynamicObjectGraph.Uncapturable(
                "Village traversal requires a PathfinderMob recipient.");
        }

        BooleanSupplier doorCapability = recipientDoorCapability(recipient);
        MoveThroughVillageGoal replacement = new MoveThroughVillageGoal(
            pathfinder,
            source.speedModifier,
            source.onlyAtNight,
            source.distanceToPoi,
            doorCapability);
        replacement.setFlags(source.getFlags().clone());
        return replacement;
    }

    private static BooleanSupplier recipientDoorCapability(Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        final Method capability;
        try {
            capability = recipient.getClass().getMethod("canBreakDoors");
        } catch (NoSuchMethodException absent) {
            // Bodies without an explicit door-breaking capability must plan
            // village routes without assuming they can open/break doors.
            return () -> false;
        }
        if (capability.getParameterCount() != 0
                || capability.getReturnType() != boolean.class
                || !capability.trySetAccessible()) {
            throw new DynamicObjectGraph.Uncapturable(
                "Recipient exposes an incompatible canBreakDoors capability method.");
        }
        return () -> {
            try {
                return (boolean) capability.invoke(recipient);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException(
                    "Could not query the recipient body's door-breaking capability", failure);
            }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Goal adaptAvoidEntity(AvoidEntityGoal<?> source, Mob recipient)
            throws DynamicObjectGraph.Uncapturable {
        if (!(recipient instanceof PathfinderMob pathfinder)) {
            throw new DynamicObjectGraph.Uncapturable(
                "Avoid-entity behavior requires a PathfinderMob recipient.");
        }
        DynamicObjectGraph.requireShareableDependency(source.avoidPredicate, "AvoidEntityGoal.avoidPredicate");
        DynamicObjectGraph.requireShareableDependency(
            source.predicateOnAvoidEntity, "AvoidEntityGoal.predicateOnAvoidEntity");

        AvoidEntityGoal replacement = new AvoidEntityGoal(
            pathfinder,
            source.avoidClass,
            (Predicate) source.avoidPredicate,
            source.maxDist,
            source.walkSpeedModifier,
            source.sprintSpeedModifier,
            (Predicate) source.predicateOnAvoidEntity);
        replacement.setFlags(source.getFlags().clone());
        return replacement;
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
