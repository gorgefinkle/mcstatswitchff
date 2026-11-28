package dev.freakyfriday.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.freakyfriday.mind.MindBodyContext;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes common health, attributes, equipment, and item-use state to the physical body. */
@Mixin(LivingEntity.class)
abstract class LivingEntityBodyContextMixin {
    private LivingEntity freakyfriday$body() {
        return (LivingEntity) MindBodyContext.bodyFor((LivingEntity) (Object) this);
    }

    @WrapMethod(method = "swing(Lnet/minecraft/world/InteractionHand;)V")
    private void freakyfriday$swingPhysicalBody(InteractionHand hand, Operation<Void> original) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) {
            body.swing(hand);
            return;
        }
        original.call(hand);
    }

    @WrapMethod(method = "getItemBySlot")
    private ItemStack freakyfriday$getBodyEquipment(EquipmentSlot slot, Operation<ItemStack> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getItemBySlot(slot) : original.call(slot);
    }

    @WrapMethod(method = "setItemSlot")
    private void freakyfriday$setBodyEquipment(EquipmentSlot slot, ItemStack stack, Operation<Void> original) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) {
            body.setItemSlot(slot, stack);
            return;
        }
        original.call(slot, stack);
    }

    @WrapMethod(method = "isUsingItem")
    private boolean freakyfriday$bodyIsUsingItem(Operation<Boolean> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.isUsingItem() : original.call();
    }

    @WrapMethod(method = "getUsedItemHand")
    private InteractionHand freakyfriday$bodyUsedItemHand(Operation<InteractionHand> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getUsedItemHand() : original.call();
    }

    @WrapMethod(method = "getUseItem")
    private ItemStack freakyfriday$bodyUseItem(Operation<ItemStack> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getUseItem() : original.call();
    }

    @WrapMethod(method = "getUseItemRemainingTicks")
    private int freakyfriday$bodyUseItemRemainingTicks(Operation<Integer> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getUseItemRemainingTicks() : original.call();
    }

    @WrapMethod(method = "getTicksUsingItem()I")
    private int freakyfriday$bodyTicksUsingItem(Operation<Integer> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getTicksUsingItem() : original.call();
    }

    @WrapMethod(method = "getTicksUsingItem(F)F")
    private float freakyfriday$bodyTicksUsingItem(float partialTick, Operation<Float> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getTicksUsingItem(partialTick) : original.call(partialTick);
    }

    @WrapMethod(method = "startUsingItem")
    private void freakyfriday$startBodyUsingItem(InteractionHand hand, Operation<Void> original) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) {
            body.startUsingItem(hand);
            return;
        }
        original.call(hand);
    }

    @WrapMethod(method = "stopUsingItem")
    private void freakyfriday$stopBodyUsingItem(Operation<Void> original) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) {
            body.stopUsingItem();
            return;
        }
        original.call();
    }

    @WrapMethod(method = "releaseUsingItem")
    private void freakyfriday$releaseBodyUsingItem(Operation<Void> original) {
        LivingEntity body = freakyfriday$body();
        if (body != (LivingEntity) (Object) this) {
            body.releaseUsingItem();
            return;
        }
        original.call();
    }

    @WrapMethod(method = "isBlocking")
    private boolean freakyfriday$bodyIsBlocking(Operation<Boolean> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.isBlocking() : original.call();
    }

    @WrapMethod(method = "getItemBlockingWith")
    private ItemStack freakyfriday$bodyItemBlockingWith(Operation<ItemStack> original) {
        LivingEntity body = freakyfriday$body();
        return body != (LivingEntity) (Object) this ? body.getItemBlockingWith() : original.call();
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
