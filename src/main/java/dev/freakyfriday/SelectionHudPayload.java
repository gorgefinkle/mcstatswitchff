package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-owned first-body selection notice; never accepts client entity IDs. */
public record SelectionHudPayload(boolean active, String bodyName, int remainingTicks)
    implements CustomPacketPayload {
    public static final Type<SelectionHudPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID,"selection_hud"));
    public static final StreamCodec<RegistryFriendlyByteBuf,SelectionHudPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public SelectionHudPayload decode(RegistryFriendlyByteBuf b) {
                boolean active = b.readBoolean();
                return active
                    ? new SelectionHudPayload(true,b.readUtf(48),b.readVarInt())
                    : new SelectionHudPayload(false,"",0);
            }
            @Override public void encode(RegistryFriendlyByteBuf b,SelectionHudPayload p) {
                b.writeBoolean(p.active());
                if(p.active()) {
                    b.writeUtf(p.bodyName(),48);
                    b.writeVarInt(p.remainingTicks());
                }
            }
        };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
