package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Runs the controller host's complete aiStep override chain from the physical
 * body's LivingEntity tick. The native host's Goal/Brain objects remain intact.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityAiStepBodyBridgeMixin {
    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;aiStep()V")
    )
    private void freakyfriday$runControllerAiStep(LivingEntity physicalBody) {
        LivingEntity controller = (LivingEntity) MindBodyContext.controllerFor(physicalBody);
        controller.aiStep();
    }
}
