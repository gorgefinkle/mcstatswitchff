package dev.freakyfriday;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Route book selection ahead of ordinary mob-specific right-click actions.
 * Villager trading, animal feeding, and other mob interactions can consume the
 * click before Item#interactLivingEntity is reached. The book is a selector,
 * not an instruction to use the target's vanilla right-click action.
 *
 * The event fires on both physical sides. State changes ONLY on the logical
 * server; both sides consume the input to prevent a duplicate client action.
 */
public final class BookInteractions {
    private BookInteractions() {}

    @SubscribeEvent
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof LivingEntity target)) return;
        Player player = event.getEntity();
        boolean holdingBook = player.getMainHandItem().is(FreakyFriday.BOOK.get())
            || player.getOffhandItem().is(FreakyFriday.BOOK.get());
        if (!holdingBook) return;

        if (player instanceof ServerPlayer serverPlayer) {
            SwapSessions.selectFromInteraction(serverPlayer, target);
        }
        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }
}
