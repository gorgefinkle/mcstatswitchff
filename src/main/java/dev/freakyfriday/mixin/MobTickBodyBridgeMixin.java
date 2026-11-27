package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import dev.freakyfriday.mind.LivingEntityTickInvoker;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Executes the original controller's concrete Mob.tick(), but its superclass
 * LivingEntity.tick() is invoked on the assigned physical body.
 *
 * The injected helper uses invokespecial through this Mob hierarchy. A normal
 * @Invoker would use virtual dispatch and call Mob.tick() again, recursing.
 */
@Mixin(Mob.class)
abstract class MobTickBodyBridgeMixin extends LivingEntity implements LivingEntityTickInvoker {
    protected MobTickBodyBridgeMixin(EntityType<? extends LivingEntity> type, Level level) {
        super(type, level);
    }

    @Override
    @Unique
    public void freakyfriday$invokeLivingEntityTick() {
        super.tick();
    }

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;tick()V")
    )
    private void freakyfriday$runPhysicalLivingTick(LivingEntity controller) {
        LivingEntity physicalBody = (LivingEntity) MindBodyContext.bodyFor(controller);
        ((LivingEntityTickInvoker) (Object) physicalBody).freakyfriday$invokeLivingEntityTick();
    }
}
