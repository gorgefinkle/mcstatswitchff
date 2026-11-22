package dev.freakyfriday.mind;

import dev.freakyfriday.SelectionHudPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Sends one first-body selection plus a definitive clear on expiry/cancel/swap. */
public final class SelectionHudSync {
    private SelectionHudSync() {}
    public static void show(ServerPlayer player, String name, int remainingTicks) {
        if(Boolean.getBoolean("freakyfriday.thirdPartyFixture")) return;
        String safeName = name.length()>48?name.substring(0,48):name;
        PacketDistributor.sendToPlayer(player,
            new SelectionHudPayload(true,safeName,Math.max(1,Math.min(160,remainingTicks))));
    }
    public static void clear(ServerPlayer player) {
        if(Boolean.getBoolean("freakyfriday.thirdPartyFixture") || player.isRemoved()) return;
        PacketDistributor.sendToPlayer(player,new SelectionHudPayload(false,"",0));
    }
    /** Expire server-owned pending selections without requiring another click. */
    @SubscribeEvent
    public static void onTick(ServerTickEvent.Post event) {
        for(ServerPlayer player:event.getServer().getPlayerList().getPlayers())
            MindSwapSessions.expirePending(player);
    }
}
