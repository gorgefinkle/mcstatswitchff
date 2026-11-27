package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.MindHostRegistry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Minecraft 26.2 server entity-tick dispatch.
 *
 * The physical body still owns its world tick, but a swapped Mob's original
 * concrete controller host supplies the virtual tick implementation.
 */
@Mixin(ServerLevel.class)
abstract class ServerLevelMobTickDispatchMixin {
    @Redirect(
        method = "tickNonPassenger",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V")
    )
    private void freakyfriday$dispatchNativeControllerTick(Entity physicalEntity) {
        if (physicalEntity instanceof Mob mob) {
            MindHostRegistry.tickAssignedController(mob);
        } else {
            physicalEntity.tick();
        }
    }
}
