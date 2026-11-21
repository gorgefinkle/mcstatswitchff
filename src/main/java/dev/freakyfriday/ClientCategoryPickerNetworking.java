package dev.freakyfriday;

import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client-only packet handler; no client classes are loaded on dedicated servers. */
@EventBusSubscriber(modid = FreakyFriday.MODID, value = Dist.CLIENT)
public final class ClientCategoryPickerNetworking {
    private ClientCategoryPickerNetworking() {}

    @SubscribeEvent
    public static void register(RegisterClientPayloadHandlersEvent event) {
        event.register(CategoryPickerPayload.TYPE, ClientCategoryPickerNetworking::handle);
    }

    private static void handle(CategoryPickerPayload payload, IPayloadContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player != null && client.player.connection != null) {
            client.setScreen(new CategoryPickerScreen(payload.targetName(), payload.entries()));
        }
    }
}
