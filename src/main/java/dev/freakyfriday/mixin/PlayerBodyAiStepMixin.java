package dev.freakyfriday.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.freakyfriday.mind.MindBodyContext;
import dev.freakyfriday.mind.PlayerPossessionState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Preserve Player-only aiStep work without running a second physical travel step. */
@Mixin(Player.class)
abstract class PlayerBodyAiStepMixin {
    @WrapOperation(
        method = "aiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;aiStep()V")
    )
    private void freakyfriday$skipDuplicateBaseAiStep(Player physicalPlayer, Operation<Void> original) {
        if (physicalPlayer instanceof ServerPlayer serverPlayer
                && PlayerPossessionState.isActive(serverPlayer)
                && MindBodyContext.isBound()) {
            return;
        }
        original.call(physicalPlayer);
    }
}
