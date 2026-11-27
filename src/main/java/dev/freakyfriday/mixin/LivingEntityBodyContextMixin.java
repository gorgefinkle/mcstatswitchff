package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes common health and attribute observations/writes to the physical body. */
@Mixin(LivingEntity.class)
abstract class LivingEntityBodyContextMixin {
    private LivingEntity freakyfriday$body() {
        return (LivingEntity) MindBodyContext.bodyFor((LivingEntity) (Object) this);
    }

    @Inject(method = "getHealth", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyHealth(CallbackInfoReturnable<Float> callback) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) callback.setReturnValue(body.getHealth());
    }

    @Inject(method = "setHealth", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$setBodyHealth(float health, CallbackInfo callback) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) {
            body.setHealth(health);
            callback.cancel();
        }
    }

    @Inject(method = "getMaxHealth", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyMaxHealth(CallbackInfoReturnable<Float> callback) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) callback.setReturnValue(body.getMaxHealth());
    }

    @Inject(method = "getAttributes", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyAttributes(CallbackInfoReturnable<AttributeMap> callback) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) callback.setReturnValue(body.getAttributes());
    }
}
