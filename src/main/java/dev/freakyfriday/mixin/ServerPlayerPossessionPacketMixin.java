package dev.freakyfriday.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.freakyfriday.mind.MindBodyContext;
import dev.freakyfriday.mind.PlayerInputBridge;
import dev.freakyfriday.mind.PlayerPossessionState;
import dev.freakyfriday.mind.PlayerBodyActions;
import net.minecraft.network.protocol.PacketUtils;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerLevel;
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
    @Shadow private abstract boolean hasClientLoaded();

    private void freakyfriday$runActionAtNpcBody(Object packet, Operation<Void> original) {
        Mob physicalNpcBody = PlayerPossessionState.npcBodyForPlayer(this.player);
        if (physicalNpcBody == null) {
            original.call(packet);
            return;
        }
        try (MindBodyContext.Scope ignored = MindBodyContext.bind(this.player, physicalNpcBody)) {
            original.call(packet);
        }
    }



    // Keep account identity and inventory on ServerPlayer, but perform native
    // packet validation and positional work from the NPC physical body.
    @WrapMethod(method = "handleAttack")
    private void freakyfriday$attackAtNpcBody(ServerboundAttackPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        freakyfriday$runActionAtNpcBody(packet, original);
    }

    @WrapMethod(method = "handleInteract")
    private void freakyfriday$interactAtNpcBody(ServerboundInteractPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        freakyfriday$runActionAtNpcBody(packet, original);
    }

    @WrapMethod(method = "handlePlayerAction")
    private void freakyfriday$playerActionAtNpcBody(ServerboundPlayerActionPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        Mob body = PlayerPossessionState.npcBodyForPlayer(this.player);
        if (body == null) return;
        PacketUtils.ensureRunningOnSameThread(packet, (ServerGamePacketListenerImpl) (Object) this, this.player.level());
        if (!this.hasClientLoaded()) return;

        ServerboundPlayerActionPacket.Action action = packet.getAction();
        if (action == ServerboundPlayerActionPacket.Action.DROP_ITEM
                || action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS) {
            this.player.resetLastActionTime();
            PlayerBodyActions.dropMainHand(body,
                action == ServerboundPlayerActionPacket.Action.DROP_ALL_ITEMS);
            return;
        }

        try (MindBodyContext.Scope ignored = MindBodyContext.bind(this.player, body)) {
            original.call(packet);
        }
    }

    @WrapMethod(method = "handleUseItemOn")
    private void freakyfriday$useItemOnAtNpcBody(ServerboundUseItemOnPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        freakyfriday$runActionAtNpcBody(packet, original);
    }

    @WrapMethod(method = "handleUseItem")
    private void freakyfriday$useItemAtNpcBody(ServerboundUseItemPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        freakyfriday$runActionAtNpcBody(packet, original);
    }

    @WrapMethod(method = "handlePlayerCommand")
    private void freakyfriday$playerCommandAtNpcBody(ServerboundPlayerCommandPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        freakyfriday$runActionAtNpcBody(packet, original);
    }

    @WrapMethod(method = "handleAnimate")
    private void freakyfriday$swingAtNpcBody(ServerboundSwingPacket packet, Operation<Void> original) {
        if (!PlayerPossessionState.isActive(this.player)) { original.call(packet); return; }
        freakyfriday$runActionAtNpcBody(packet, original);
    }

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
