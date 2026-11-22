package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server correction and input-acknowledgement for client-side prediction. */
public record PossessionStatePayload(int entityId,long acknowledgedSequence,
    double x,double y,double z,float yaw,float pitch,double velocityY)
    implements CustomPacketPayload {
    public static final Type<PossessionStatePayload> TYPE=
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID,"possession_state"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PossessionStatePayload> STREAM_CODEC=
        new StreamCodec<>() {
            @Override public PossessionStatePayload decode(RegistryFriendlyByteBuf b){
                return new PossessionStatePayload(b.readVarInt(),b.readVarLong(),
                    b.readDouble(),b.readDouble(),b.readDouble(),
                    b.readFloat(),b.readFloat(),b.readDouble());
            }
            @Override public void encode(RegistryFriendlyByteBuf b,PossessionStatePayload p){
                b.writeVarInt(p.entityId());b.writeVarLong(p.acknowledgedSequence());
                b.writeDouble(p.x());b.writeDouble(p.y());b.writeDouble(p.z());
                b.writeFloat(p.yaw());b.writeFloat(p.pitch());b.writeDouble(p.velocityY());
            }
        };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
