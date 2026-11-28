package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Runs the controller's complete native aiStep chain from the physical body's
 * LivingEntity tick. ServerPlayer-only aiStep work then runs separately; its
 * base LivingEntity AI/movement step is suppressed to avoid double travel.
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
        if (physicalBody instanceof ServerPlayer) {
            physicalBody.aiStep();
        }
    }
}
