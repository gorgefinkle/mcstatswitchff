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
 * setCamera still stores/sends the NPC camera entity, but its teleport is
 * suppressed. Only ServerPlayer.tick's follow query is overridden, so other
 * callers of getCamera retain the actual camera identity.
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
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/level/ServerPlayer;getCamera()Lnet/minecraft/world/entity/Entity;")
    )
    private Entity freakyfriday$keepPhysicalAccountBody(ServerPlayer player) {
        if (PlayerPossessionState.isActive(player)) return player;
        return player.getCamera();
    }
}
