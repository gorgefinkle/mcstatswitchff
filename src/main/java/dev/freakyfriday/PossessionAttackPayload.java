package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Candidate-only attack intent. The authenticated server resolves the actor. */
public record PossessionAttackPayload(int lookedAtEntityId,long sequence)
    implements CustomPacketPayload {
    public static final Type<PossessionAttackPayload> TYPE=
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID,"possession_attack"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PossessionAttackPayload> STREAM_CODEC=
        new StreamCodec<>() {
            @Override public PossessionAttackPayload decode(RegistryFriendlyByteBuf b){
                return new PossessionAttackPayload(b.readVarInt(),b.readVarLong());
            }
            @Override public void encode(RegistryFriendlyByteBuf b,PossessionAttackPayload p){
                b.writeVarInt(p.lookedAtEntityId());b.writeVarLong(p.sequence());
            }
        };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
