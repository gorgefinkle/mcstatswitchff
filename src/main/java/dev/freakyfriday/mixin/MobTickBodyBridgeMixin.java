package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * When an original Mob controller runs its concrete tick override, redirect
 * its superclass LivingEntity tick to the assigned physical LivingEntity body.
 */
@Mixin(Mob.class)
abstract class MobTickBodyBridgeMixin {
    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tick()V")
    )
    private void freakyfriday$runPhysicalLivingTick(LivingEntity controller) {
        LivingEntity physicalBody = (LivingEntity) MindBodyContext.bodyFor(controller);
        ((LivingEntityTickInvoker) (Object) physicalBody).freakyfriday$invokeLivingEntityTick();
    }
}
