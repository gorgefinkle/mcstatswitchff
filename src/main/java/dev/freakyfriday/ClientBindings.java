package dev.freakyfriday;

import dev.freakyfriday.compat.ClientKeyFactory;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * A configurable Ctrl+S self-select shortcut. The engine maps the shortcut
 * to a server-validated command and never accepts client-selected entity IDs.
 * Version-specific keyboard constants live in ClientKeyFactory.
 */
@EventBusSubscriber(modid = FreakyFriday.MODID, value = Dist.CLIENT)
public final class ClientBindings {
    private ClientBindings() {}
    private static final KeyMapping SELECT_SELF = ClientKeyFactory.create();
    private static int hintTimer;
    private static boolean wasHolding;

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SELECT_SELF);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            wasHolding = false;
            hintTimer = 0;
            return;
        }
        boolean holding = mc.player.getMainHandItem().is(FreakyFriday.BOOK.get())
            || mc.player.getOffhandItem().is(FreakyFriday.BOOK.get());
        while (SELECT_SELF.consumeClick()) {
            if (holding && mc.gui.screen() == null)
                mc.player.connection.sendCommand("freakyfriday self");
        }
        if (holding && mc.gui.screen() == null && (!wasHolding || ++hintTimer >= 200)) {
            hintTimer = 0;
            mc.gui.hud.setOverlayMessage(Component.literal("Freaky Friday: right-click mob or "
                + SELECT_SELF.getTranslatedKeyMessage().getString() + " to select yourself"), true);
        }
        if (!holding) hintTimer = 0;
        wasHolding = holding;
    }
}
