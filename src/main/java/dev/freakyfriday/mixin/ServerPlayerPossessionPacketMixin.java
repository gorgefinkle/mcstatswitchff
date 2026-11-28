package dev.freakyfriday.mixin;

import dev.freakyfriday.mind.PlayerInputBridge;
import dev.freakyfriday.mind.PlayerPossessionState;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.player.Input;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Split account packet authentication from controls sent to the possessed NPC. */
@Mixin(ServerGamePacketListenerImpl.class)
abstract class ServerPlayerPossessionPacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handlePlayerInput", at = @At("TAIL"))
    private void freakyfriday$captureNpcInput(ServerboundPlayerInputPacket packet, CallbackInfo callback) {
        if (!PlayerPossessionState.isActive(this.player)) return;
        PlayerInputBridge.capture(this.player, packet.input());
        // The account's physical body must not also consume the same WASD input.
        this.player.setLastClientInput(Input.EMPTY);
        this.player.setShiftKeyDown(false);
    }

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void freakyfriday$routeLookOnly(ServerboundMovePlayerPacket packet, CallbackInfo callback) {
        if (!PlayerPossessionState.isActive(this.player)) return;

        // Preserve normal thread affinity before taking over the packet. Client
        // position coordinates are deliberately ignored; they describe the
        // camera target, not the player's separate physical body.
        PacketUtils.ensureRunningOnSameThread(packet,
            (ServerGamePacketListenerImpl) (Object) this, this.player.level());

        if (packet.hasRotation()) {
            float yaw = packet.getYRot(this.player.getYRot());
            float pitch = packet.getXRot(this.player.getXRot());
            if (Float.isFinite(yaw) && Float.isFinite(pitch)) {
                PlayerInputBridge.captureLook(this.player, yaw, pitch);
            }
        }
        this.player.resetLastActionTime();
        callback.cancel();
    }
}
