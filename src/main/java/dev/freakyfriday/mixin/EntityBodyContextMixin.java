package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Redirect common spatial reads and motion writes to the physical body. */
@Mixin(Entity.class)
abstract class EntityBodyContextMixin {
    private Entity freakyfriday$body() {
        return (Entity) MindBodyContext.bodyFor((Entity) (Object) this);
    }

    @Inject(method = "level", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyLevel(CallbackInfoReturnable<Level> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.level());
    }

    @Inject(method = "position", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyPosition(CallbackInfoReturnable<Vec3> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.position());
    }

    @Inject(method = "blockPosition", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyBlockPosition(CallbackInfoReturnable<BlockPos> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.blockPosition());
    }

    @Inject(method = "getBoundingBox", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyBoundingBox(CallbackInfoReturnable<AABB> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getBoundingBox());
    }

    @Inject(method = "getX", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyX(CallbackInfoReturnable<Double> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getX());
    }

    @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyY(CallbackInfoReturnable<Double> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getY());
    }

    @Inject(method = "getZ", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyZ(CallbackInfoReturnable<Double> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getZ());
    }

    @Inject(method = "getEyeY", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyEyeY(CallbackInfoReturnable<Double> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getEyeY());
    }

    @Inject(method = "getEyeHeight", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyEyeHeight(CallbackInfoReturnable<Float> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getEyeHeight());
    }

    @Inject(method = "getBbWidth", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyWidth(CallbackInfoReturnable<Float> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getBbWidth());
    }

    @Inject(method = "getBbHeight", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyHeight(CallbackInfoReturnable<Float> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getBbHeight());
    }

    @Inject(method = "getYRot", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyYaw(CallbackInfoReturnable<Float> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getYRot());
    }

    @Inject(method = "getXRot", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyPitch(CallbackInfoReturnable<Float> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getXRot());
    }

    @Inject(method = "getDeltaMovement", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$bodyVelocity(CallbackInfoReturnable<Vec3> callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) callback.setReturnValue(body.getDeltaMovement());
    }

    @Inject(method = "setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$setBodyVelocity(Vec3 movement, CallbackInfo callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) {
            body.setDeltaMovement(movement);
            callback.cancel();
        }
    }

    @Inject(method = "setDeltaMovement(DDD)V", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$setBodyVelocity(double x, double y, double z, CallbackInfo callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) {
            body.setDeltaMovement(x, y, z);
            callback.cancel();
        }
    }

    @Inject(method = "setYRot", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$setBodyYaw(float rotation, CallbackInfo callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) {
            body.setYRot(rotation);
            callback.cancel();
        }
    }

    @Inject(method = "setXRot", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$setBodyPitch(float rotation, CallbackInfo callback) {
        Entity body = freakyfriday$body();
        if (body != (Entity) (Object) this) {
            body.setXRot(rotation);
            callback.cancel();
        }
    }
}
