package dev.freakyfriday.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;

/**
 * During a mind-controller aiStep, retain the base LivingEntity AI/physics
 * phase but do not execute Mob's recipient-specific upkeep against a different
 * body's physical state (for example, Zombie daylight burning on a Creeper).
 *
 * Concrete subclass overrides still run before/after their call to
 * super.aiStep(). The physical recipient runs its own full Mob.aiStep upkeep
 * in the separate body phase.
 */
@Mixin(Mob.class)
abstract class MobAiStepBodyOwnershipMixin extends LivingEntity {
    protected MobAiStepBodyOwnershipMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @WrapMethod(method = "aiStep")
    private void freakyfriday$controllerRunsSharedAiButNotMobBodyUpkeep(Operation<Void> original) {
        Mob controller = (Mob) (Object) this;
        if (MindBodyContext.isExecutingController(controller)) {
            // serverAiStep is invoked by LivingEntity.aiStep and remains intact:
            // goals, sensing, navigation, controls and customServerAiStep survive.
            // Only Mob's post-super body upkeep is skipped for the donor.
            super.aiStep();
            return;
        }
        original.call();
    }
}
