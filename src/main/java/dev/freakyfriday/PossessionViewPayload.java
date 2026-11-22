package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server-owned camera target; -1 inactive. */
public record PossessionViewPayload(int entityId,boolean active)
    implements CustomPacketPayload {
    public static final Type<PossessionViewPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID,"possession_camera"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PossessionViewPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public PossessionViewPayload decode(RegistryFriendlyByteBuf b) {
                return new PossessionViewPayload(b.readVarInt(),b.readBoolean());
            }
            @Override public void encode(RegistryFriendlyByteBuf b,PossessionViewPayload p) {
                b.writeVarInt(p.entityId());b.writeBoolean(p.active());
            }
        };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
