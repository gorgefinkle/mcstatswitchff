package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import dev.freakyfriday.mind.MindHostRegistry;
import dev.freakyfriday.mind.PlayerPossessionState;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Preserve native controller AI services when the physical body is a Player.
 * For a player-driven NPC body, suppress only the Mob decision-selector phase;
 * the body still receives its normal LivingEntity physical tick.
 */
@Mixin(Mob.class)
abstract class MobServerAiStepMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$observeOrSuppressNativeAi(CallbackInfo callback) {
        Mob mob = (Mob) (Object) this;
        if (PlayerPossessionState.isPlayerControlledBody(mob)
                || MindBodyContext.isExecutingPhysicalBodyAiStep(mob)) {
            callback.cancel();
            return;
        }
        MindHostRegistry.observeServerAiPhase(mob);
    }

    private Mob freakyfriday$serviceOwner(Mob controller) {
        Object physicalBody = MindBodyContext.bodyFor(controller);
        // ServerPlayer has no Mob navigation/sensing/control services. Keep
        // the original controller's owner-bound services; common Entity
        // accessors and move() are redirected to the Player body.
        return physicalBody instanceof Mob bodyMob ? bodyMob : controller;
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;sensing:Lnet/minecraft/world/entity/ai/sensing/Sensing;"))
    private Sensing freakyfriday$physicalSensing(Mob controller) {
        return freakyfriday$serviceOwner(controller).getSensing();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;navigation:Lnet/minecraft/world/entity/ai/navigation/PathNavigation;"))
    private PathNavigation freakyfriday$physicalNavigation(Mob controller) {
        return freakyfriday$serviceOwner(controller).getNavigation();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;moveControl:Lnet/minecraft/world/entity/ai/control/MoveControl;"))
    private MoveControl freakyfriday$physicalMoveControl(Mob controller) {
        return freakyfriday$serviceOwner(controller).getMoveControl();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;lookControl:Lnet/minecraft/world/entity/ai/control/LookControl;"))
    private LookControl freakyfriday$physicalLookControl(Mob controller) {
        return freakyfriday$serviceOwner(controller).getLookControl();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;jumpControl:Lnet/minecraft/world/entity/ai/control/JumpControl;"))
    private JumpControl freakyfriday$physicalJumpControl(Mob controller) {
        return freakyfriday$serviceOwner(controller).getJumpControl();
    }
}
