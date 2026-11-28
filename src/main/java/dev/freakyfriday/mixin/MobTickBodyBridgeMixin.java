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
 * Separates the two concrete tick chains during a mind/body exchange.
 *
 * The controller's concrete Mob.tick() and its subclass tick overrides run
 * first, but its LivingEntity superclass tick is suppressed. The physical
 * body's concrete tick is then called by MindHostRegistry and reaches this
 * redirect with no controller identity match, so its true LivingEntity base
 * tick runs exactly once.
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
    private void freakyfriday$separateControllerAndBodyTicks(LivingEntity receiver) {
        // Do not execute the donor's physical superclass tick. Its subclass
        // tick has already updated donor-owned AI state; the assigned body's
        // full concrete tick is run immediately after that controller phase.
        if (MindBodyContext.isExecutingController(receiver)) return;

        // This is the physical body's Mob.tick() invocation. Its superclass
        // LivingEntity tick is the one physical base tick for this body.
        LivingEntity physicalBody = (LivingEntity) MindBodyContext.bodyFor(receiver);
        ((LivingEntityTickInvoker) (Object) physicalBody).freakyfriday$invokeLivingEntityTick();
    }
}
