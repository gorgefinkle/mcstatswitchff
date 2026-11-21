package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-to-client snapshot of selectable categories for the selected first entity. */
public record CategoryPickerPayload(String targetName, String entries) implements CustomPacketPayload {
    public static final Type<CategoryPickerPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID, "open_category_picker"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CategoryPickerPayload> STREAM_CODEC =
        StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, CategoryPickerPayload::targetName,
            ByteBufCodecs.STRING_UTF8, CategoryPickerPayload::entries,
            CategoryPickerPayload::new
        );
    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
