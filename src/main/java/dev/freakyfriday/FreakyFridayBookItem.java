package dev.freakyfriday;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public final class FreakyFridayBookItem extends Item {
    public FreakyFridayBookItem(Properties properties) { super(properties); }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            dev.freakyfriday.mind.MindSwapSessions.selectFromInteraction(serverPlayer, target);
        }
        return InteractionResult.SUCCESS;
    }
}
