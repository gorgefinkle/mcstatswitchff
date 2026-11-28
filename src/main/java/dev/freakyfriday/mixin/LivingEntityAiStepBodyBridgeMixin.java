package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Runs controller AI and recipient-specific AI in separate phases:
 * - controller.aiStep() keeps the donor's full native decision behavior;
 * - physicalBody.aiStep() keeps species-specific recipient behavior;
 * - shared LivingEntity movement/physics executes only in the controller phase.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityAiStepBodyBridgeMixin {
    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;aiStep()V")
    )
    private void freakyfriday$runControllerAndBodyAiSteps(LivingEntity physicalBody) {
        LivingEntity controller = (LivingEntity) MindBodyContext.controllerFor(physicalBody);
        if (controller == physicalBody) {
            controller.aiStep();
            return;
        }

        controller.aiStep();
        try (MindBodyContext.BodyAiStepScope ignored =
                MindBodyContext.physicalBodyAiStep(physicalBody)) {
            physicalBody.aiStep();
        }
    }
}
