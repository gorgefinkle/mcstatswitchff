package dev.freakyfriday.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;

/**
 * During the recipient-specific AI phase, retain its concrete/subclass aiStep
 * code while suppressing only the shared LivingEntity base movement phase.
 */
@Mixin(LivingEntity.class)
abstract class LivingEntityPhysicalAiStepMixin {
    @WrapMethod(method = "aiStep")
    private void freakyfriday$skipDuplicatePhysicalBaseAiStep(Operation<Void> original) {
        LivingEntity physicalBody = (LivingEntity) (Object) this;
        if (MindBodyContext.isExecutingPhysicalBodyAiStep(physicalBody)) return;
        original.call();
    }
}
