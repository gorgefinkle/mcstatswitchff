package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes public Mob body services resolve against the current physical body. */
@Mixin(Mob.class)
abstract class MobBodyServicesMixin {
    @Inject(method = "getNavigation", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$physicalNavigation(CallbackInfoReturnable<PathNavigation> callback) {
        Mob controller = (Mob) (Object) this;
        Mob body = (Mob) MindBodyContext.bodyFor(controller);
        if (body != controller) callback.setReturnValue(body.getNavigation());
    }

    @Inject(method = "getMoveControl", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$physicalMoveControl(CallbackInfoReturnable<MoveControl> callback) {
        Mob controller = (Mob) (Object) this;
        Mob body = (Mob) MindBodyContext.bodyFor(controller);
        if (body != controller) callback.setReturnValue(body.getMoveControl());
    }

    @Inject(method = "getLookControl", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$physicalLookControl(CallbackInfoReturnable<LookControl> callback) {
        Mob controller = (Mob) (Object) this;
        Mob body = (Mob) MindBodyContext.bodyFor(controller);
        if (body != controller) callback.setReturnValue(body.getLookControl());
    }

    @Inject(method = "getJumpControl", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$physicalJumpControl(CallbackInfoReturnable<JumpControl> callback) {
        Mob controller = (Mob) (Object) this;
        Mob body = (Mob) MindBodyContext.bodyFor(controller);
        if (body != controller) callback.setReturnValue(body.getJumpControl());
    }

    @Inject(method = "getSensing", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$physicalSensing(CallbackInfoReturnable<Sensing> callback) {
        Mob controller = (Mob) (Object) this;
        Mob body = (Mob) MindBodyContext.bodyFor(controller);
        if (body != controller) callback.setReturnValue(body.getSensing());
    }
}
