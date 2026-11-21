package dev.freakyfriday;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Common-side networking. The client does not decide category membership. */
public final class CategoryPickerNetworking {
    private CategoryPickerNetworking() {}

    public static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playToClient(CategoryPickerPayload.TYPE, CategoryPickerPayload.STREAM_CODEC);
    }

    public static void open(net.minecraft.server.level.ServerPlayer player,
                            String targetName,
                            java.util.Map<String, dev.freakyfriday.core.CategoryOffers.Offer> offers) {
        StringBuilder entries = new StringBuilder();
        for (var offer : offers.values()) {
            if (!entries.isEmpty()) entries.append('\n');
            // IDs and labels originate from server-owned CategoryDefinitions.
            entries.append(offer.categoryId()).append('|')
                .append(offer.label()).append('|')
                .append(offer.memberCount());
        }
        String sanitizedTarget = targetName.replace('\n', ' ').replace('\r', ' ');
        if (sanitizedTarget.length() > 64) sanitizedTarget = sanitizedTarget.substring(0, 64);
        PacketDistributor.sendToPlayer(player, new CategoryPickerPayload(sanitizedTarget, entries.toString()));
    }
}
