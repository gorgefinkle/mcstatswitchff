package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.PlayerPossessionState;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Keep camera view and physical player body independent during possession.
 * Vanilla setCamera teleports the player to the camera, and ServerPlayer.tick
 * continuously follows it; both relocations are suppressed while possessed.
 */
@Mixin(ServerPlayer.class)
abstract class ServerPlayerPossessionCameraMixin {
    @Redirect(
        method = "setCamera",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDLjava/util/Set;FFZ)Z")
    )
    private boolean freakyfriday$skipCameraTeleport(ServerPlayer player, ServerLevel level,
            double x, double y, double z, Set<?> relatives,
            float yRot, float xRot, boolean resetCamera) {
        if (PlayerPossessionState.isActive(player)) return true;
        @SuppressWarnings("unchecked")
        Set<Relative> typedRelatives = (Set<Relative>) relatives;
        return player.teleportTo(level, x, y, z, typedRelatives, yRot, xRot, resetCamera);
    }

    @Redirect(
        method = "tick",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;absSnapTo(DDDFF)V")
    )
    private void freakyfriday$skipCameraFollow(Entity physicalPlayer,
            double x, double y, double z, float yRot, float xRot) {
        if (physicalPlayer instanceof ServerPlayer player && PlayerPossessionState.isActive(player)) return;
        physicalPlayer.absSnapTo(x, y, z, yRot, xRot);
    }
}
