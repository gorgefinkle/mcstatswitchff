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
    private static boolean wasHolding;
    private static String lastHintKey;

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SELECT_SELF);
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) {
            wasHolding = false;
            lastHintKey = null;
            return;
        }
        boolean holding = mc.player.getMainHandItem().is(FreakyFriday.BOOK.get())
            || mc.player.getOffhandItem().is(FreakyFriday.BOOK.get());
        while (SELECT_SELF.consumeClick()) {
            if (holding && mc.gui.screen() == null)
                mc.player.connection.sendCommand("freakyfriday self");
        }
        // Show once per equip, and again if the user remaps the shortcut.
        // Do not periodically overwrite other mods' action-bar messages.
        String shownKey = SELECT_SELF.getTranslatedKeyMessage().getString();
        if (holding && mc.gui.screen() == null && (!wasHolding || !shownKey.equals(lastHintKey))) {
            mc.gui.hud.setOverlayMessage(Component.literal("Freaky Friday: right-click mob or "
                + shownKey + " (Ctrl+S selects your body; /freakyfriday cancel returns)"), true);
            lastHintKey = shownKey;
        }
        if (!holding) lastHintKey = null;
        wasHolding = holding;
    }
}
