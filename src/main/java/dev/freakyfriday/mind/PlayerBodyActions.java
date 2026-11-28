package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;

/** Physical-item actions for a player who is currently driving an NPC body. */
public final class PlayerBodyActions {
    private PlayerBodyActions() {}

    /**
     * Vanilla DROP_ITEM/DROP_ALL_ITEMS operate on the selected main-hand stack,
     * not every equipment slot. All mutations and item spawning belong to the
     * NPC body; the authenticated ServerPlayer inventory is left untouched.
     *
     * @return the number of items actually dropped
     */
    public static int dropMainHand(Mob body, boolean dropEntireStack) {
        if (body == null || !(body.level() instanceof ServerLevel level)) return 0;
        ItemStack held = body.getItemBySlot(EquipmentSlot.MAINHAND);
        if (held == null || held.isEmpty()) return 0;

        int droppedCount = dropEntireStack ? held.getCount() : 1;
        ItemStack dropped;
        if (dropEntireStack) {
            dropped = held.copy();
            body.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        } else {
            dropped = held.split(1);
            body.setItemSlot(EquipmentSlot.MAINHAND,
                held.isEmpty() ? ItemStack.EMPTY : held);
        }

        if (dropped.isEmpty()) return 0;
        body.spawnAtLocation(level, dropped);
        return droppedCount;
    }

    /**
     * Execute the body species' own native attack method. This deliberately
     * avoids ServerPlayer.attack(): modded Mob overrides of doHurtTarget are
     * honored automatically, and unsupported attacks simply have no effect.
     */
    public static boolean attack(Mob body, net.minecraft.world.entity.Entity target) {
        if (body == null || target == null || !body.isAlive() || body.isRemoved()
                || target == body || target.isRemoved() || body.level() != target.level()
                || !(body.level() instanceof ServerLevel level)
                || !(target instanceof net.minecraft.world.entity.LivingEntity livingTarget)) {
            return false;
        }
        if (!livingTarget.isAlive() || !body.canAttack(livingTarget)
                || !body.isWithinMeleeAttackRange(livingTarget)
                || !body.hasLineOfSight(livingTarget)) {
            return false;
        }

        boolean hit = body.doHurtTarget(level, target);
        if (hit) body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        return hit;
    }
}
