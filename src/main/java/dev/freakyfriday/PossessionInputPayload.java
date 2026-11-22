package dev.freakyfriday;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Serverbound authenticated-connection movement sample (NO client target ID). */
public record PossessionInputPayload(float forward,float strafe,boolean jump,
                                     float yaw,float pitch,long sequence)
    implements CustomPacketPayload {
    public static final Type<PossessionInputPayload> TYPE =
        new Type<>(Identifier.fromNamespaceAndPath(FreakyFriday.MODID,"possession_input"));
    public static final StreamCodec<RegistryFriendlyByteBuf,PossessionInputPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override public PossessionInputPayload decode(RegistryFriendlyByteBuf b) {
                return new PossessionInputPayload(
                    b.readFloat(),b.readFloat(),b.readBoolean(),
                    b.readFloat(),b.readFloat(),b.readVarLong());
            }
            @Override public void encode(RegistryFriendlyByteBuf b,PossessionInputPayload p) {
                b.writeFloat(p.forward());
                b.writeFloat(p.strafe());
                b.writeBoolean(p.jump());
                b.writeFloat(p.yaw());
                b.writeFloat(p.pitch());
                b.writeVarLong(p.sequence());
            }
        };
    @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
}
