package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindBodyContext;
import dev.freakyfriday.mind.MindHostRegistry;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.JumpControl;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.ai.sensing.Sensing;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Native controller AI stays on its original Mob; physical sensing, navigation
 * and controls come from the body currently assigned to that controller.
 */
@Mixin(Mob.class)
abstract class MobServerAiStepMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void freakyfriday$observeNativeAiHost(CallbackInfo callback) {
        MindHostRegistry.observeServerAiPhase((Mob) (Object) this);
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;sensing:Lnet/minecraft/world/entity/ai/sensing/Sensing;"))
    private Sensing freakyfriday$physicalSensing(Mob controller) {
        return ((Mob) MindBodyContext.bodyFor(controller)).getSensing();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;navigation:Lnet/minecraft/world/entity/ai/navigation/PathNavigation;"))
    private PathNavigation freakyfriday$physicalNavigation(Mob controller) {
        return ((Mob) MindBodyContext.bodyFor(controller)).getNavigation();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;moveControl:Lnet/minecraft/world/entity/ai/control/MoveControl;"))
    private MoveControl freakyfriday$physicalMoveControl(Mob controller) {
        return ((Mob) MindBodyContext.bodyFor(controller)).getMoveControl();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;lookControl:Lnet/minecraft/world/entity/ai/control/LookControl;"))
    private LookControl freakyfriday$physicalLookControl(Mob controller) {
        return ((Mob) MindBodyContext.bodyFor(controller)).getLookControl();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "FIELD", target = "Lnet/minecraft/world/entity/Mob;jumpControl:Lnet/minecraft/world/entity/ai/control/JumpControl;"))
    private JumpControl freakyfriday$physicalJumpControl(Mob controller) {
        return ((Mob) MindBodyContext.bodyFor(controller)).getJumpControl();
    }

    @Redirect(method = "serverAiStep",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;level()Lnet/minecraft/world/level/Level;"))
    private Level freakyfriday$physicalLevel(Entity controller) {
        return ((Entity) MindBodyContext.bodyFor(controller)).level();
    }
}
