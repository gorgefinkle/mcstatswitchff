package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindHostRegistry;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Passive 26.2 observation point for every server-side Mob AI implementation.
 * The actual Goals/Brain remain untouched and continue running on their host.
 */
@Mixin(Mob.class)
abstract class MobServerAiStepMixin {
    @Inject(method = "serverAiStep", at = @At("HEAD"))
    private void freakyfriday$observeNativeAiHost(CallbackInfo callback) {
        MindHostRegistry.observeServerAiPhase((Mob) (Object) this);
    }
}
