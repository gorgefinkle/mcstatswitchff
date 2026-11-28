package dev.freakyfriday.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import dev.freakyfriday.mind.MindBodyContext;
import dev.freakyfriday.mind.MindHostRegistry;
import dev.freakyfriday.mind.PlayerPossessionState;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Runs the original NPC controller tick while ServerPlayer's physical body
 * executes its ordinary Player tick. The context is always popped in finally.
 */
@Mixin(Player.class)
abstract class PlayerPossessionTickMixin {
    @WrapMethod(method = "tick")
    private void freakyfriday$runNpcMindForPlayerBody(Operation<Void> original) {
        Player physicalPlayer = (Player) (Object) this;
        if (!(physicalPlayer instanceof ServerPlayer serverPlayer)) {
            original.call();
            return;
        }

        PlayerPossessionState.validateOrEnd(serverPlayer);
        Mob controller = PlayerPossessionState.controllerForPlayerBody(serverPlayer);
        if (controller == null) {
            original.call();
            return;
        }

        MindHostRegistry.prepareControllerTick(controller, serverPlayer);
        try (MindBodyContext.Scope ignored = MindBodyContext.bind(controller, serverPlayer)) {
            // Preserve the controller's native concrete tick and private task
            // state. MobTickBodyBridgeMixin suppresses only its superclass tick.
            controller.tick();
            // Vanilla Player.tick still owns account-body lifecycle. Its
            // LivingEntity.aiStep is bridged to the NPC controller below.
            original.call();
        }
    }
}
