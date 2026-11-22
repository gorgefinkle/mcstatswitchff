package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** An intent only; the server validates the real occupied body's attack. */
public record PossessionAttackPayload(int targetEntityId,long sequence)
    implements CustomPacketPayload {
    public static final Type<PossessionAttackPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID,"possession_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PossessionAttackPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public PossessionAttackPayload decode(RegistryFriendlyByteBuf b){
                return new PossessionAttackPayload(b.readVarInt(),b.readVarLong());
            }
            @Override public void encode(RegistryFriendlyByteBuf b,PossessionAttackPayload p){
                b.writeVarInt(p.targetEntityId());b.writeVarLong(p.sequence());
            }
        };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
