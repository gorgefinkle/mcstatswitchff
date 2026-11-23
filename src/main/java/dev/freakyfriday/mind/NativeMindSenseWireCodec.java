package dev.freakyfriday.mind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Authenticated, bounded binary transport for recipient-world sensory frames.
 * HMAC is verified before any attacker-controlled lengths/counts are parsed.
 * This codec is transport infrastructure; a live streaming supervisor still
 * has to connect it to a native AI host.
 */
public final class NativeMindSenseWireCodec {
    private NativeMindSenseWireCodec() {}
    public static final int KEY_BYTES=32;
    public static final int MAX_WIRE_BYTES=262144;
    private static final int SIGNATURE_BYTES=32;
    private static final int MAGIC=0x46464d53; // FFMS
    private static final int VERSION=1;
    private static final int MAX_STRING_BYTES=NativeMindSenseFrame.MAX_IDENTIFIER_CHARS;

    public static byte[] encode(NativeMindSenseFrame frame, byte[] key) {
        keyCheck(key);
        try {
            ByteArrayOutputStream memory=new ByteArrayOutputStream();
            DataOutputStream out=new DataOutputStream(memory);
            out.writeInt(MAGIC);
            out.writeByte(VERSION);
            writeUuid(out,frame.bodyId());
            writeUuid(out,frame.mindId());
            out.writeLong(frame.tick());
            writeString(out,frame.dimension());
            var body=frame.body();
            writeString(out,body.entityType());
            out.writeDouble(body.x()); out.writeDouble(body.y()); out.writeDouble(body.z());
            out.writeDouble(body.velocityX()); out.writeDouble(body.velocityY()); out.writeDouble(body.velocityZ());
            out.writeFloat(body.yaw()); out.writeFloat(body.pitch());
            out.writeFloat(body.health()); out.writeFloat(body.maxHealth());
            out.writeBoolean(body.alive()); out.writeBoolean(body.onGround());
            out.writeBoolean(body.inWater()); out.writeBoolean(body.inLava());
            out.writeShort(frame.entities().size());
            for(var entity:frame.entities()) {
                writeUuid(out,entity.id()); writeString(out,entity.entityType());
                out.writeDouble(entity.dx()); out.writeDouble(entity.dy()); out.writeDouble(entity.dz());
                out.writeDouble(entity.velocityX()); out.writeDouble(entity.velocityY()); out.writeDouble(entity.velocityZ());
                out.writeFloat(entity.health()); out.writeFloat(entity.maxHealth());
                out.writeBoolean(entity.alive()); out.writeBoolean(entity.visible()); out.writeBoolean(entity.onGround());
            }
            out.writeShort(frame.blocks().size());
            for(var block:frame.blocks()) {
                out.writeByte(block.dx()); out.writeByte(block.dy()); out.writeByte(block.dz());
                writeString(out,block.blockType()); writeString(out,block.fluidType());
                out.writeBoolean(block.sturdyTop()); out.writeBoolean(block.collisionEmpty());
            }
            out.flush();
            byte[] payload=memory.toByteArray();
            if(payload.length+SIGNATURE_BYTES>MAX_WIRE_BYTES)
                throw new IllegalArgumentException("Sensory frame exceeds wire byte limit");
            byte[] signature=hmac(payload,key);
            byte[] result=java.util.Arrays.copyOf(payload,payload.length+SIGNATURE_BYTES);
            System.arraycopy(signature,0,result,payload.length,SIGNATURE_BYTES);
            return result;
        } catch(IOException impossible) {
            throw new IllegalStateException("Unexpected in-memory sensory writer failure",impossible);
        }
    }

    public static NativeMindSenseFrame decode(byte[] wire, byte[] key,
            UUID expectedBody, UUID expectedMind, long expectedTick) {
        keyCheck(key);
        if(wire==null || wire.length<=SIGNATURE_BYTES || wire.length>MAX_WIRE_BYTES)
            throw new IllegalArgumentException("Invalid sensory wire size");
        if(expectedBody==null || expectedMind==null || expectedTick<0)
            throw new IllegalArgumentException("Invalid expected sensory identity/tick");
        int payloadLength=wire.length-SIGNATURE_BYTES;
        byte[] payload=java.util.Arrays.copyOf(wire,payloadLength);
        byte[] supplied=java.util.Arrays.copyOfRange(wire,payloadLength,wire.length);
        if(!MessageDigest.isEqual(hmac(payload,key),supplied))
            throw new IllegalArgumentException("Unauthenticated sensory frame");
        try(DataInputStream in=new DataInputStream(new ByteArrayInputStream(payload))) {
            if(in.readInt()!=MAGIC || in.readUnsignedByte()!=VERSION)
                throw new IllegalArgumentException("Unknown sensory frame version");
            UUID bodyId=readUuid(in), mindId=readUuid(in);
            long tick=in.readLong();
            if(!expectedBody.equals(bodyId) || !expectedMind.equals(mindId) || tick!=expectedTick)
                throw new IllegalArgumentException("Sensory frame identity or tick mismatch");
            String dimension=readString(in);
            String entityType=readString(in);
            double x=in.readDouble(),y=in.readDouble(),z=in.readDouble();
            double vx=in.readDouble(),vy=in.readDouble(),vz=in.readDouble();
            float yaw=in.readFloat(),pitch=in.readFloat(),health=in.readFloat(),maxHealth=in.readFloat();
            var body=new NativeMindSenseFrame.BodySense(entityType,x,y,z,vx,vy,vz,yaw,pitch,
                health,maxHealth,in.readBoolean(),in.readBoolean(),in.readBoolean(),in.readBoolean());
            int entityCount=in.readUnsignedShort();
            if(entityCount>NativeMindSenseFrame.MAX_ENTITIES)
                throw new IllegalArgumentException("Entity count exceeds sensory bound");
            List<NativeMindSenseFrame.EntitySense> entities=new ArrayList<>(entityCount);
            for(int i=0;i<entityCount;i++) {
                UUID id=readUuid(in); String type=readString(in);
                double dx=in.readDouble(),dy=in.readDouble(),dz=in.readDouble();
                double evx=in.readDouble(),evy=in.readDouble(),evz=in.readDouble();
                float eh=in.readFloat(),em=in.readFloat();
                entities.add(new NativeMindSenseFrame.EntitySense(id,type,dx,dy,dz,evx,evy,evz,
                    eh,em,in.readBoolean(),in.readBoolean(),in.readBoolean()));
            }
            int blockCount=in.readUnsignedShort();
            if(blockCount>NativeMindSenseFrame.MAX_BLOCKS)
                throw new IllegalArgumentException("Block count exceeds sensory bound");
            List<NativeMindSenseFrame.BlockSense> blocks=new ArrayList<>(blockCount);
            for(int i=0;i<blockCount;i++) {
                int dx=in.readByte(),dy=in.readByte(),dz=in.readByte();
                String block=readString(in),fluid=readString(in);
                blocks.add(new NativeMindSenseFrame.BlockSense(dx,dy,dz,block,fluid,
                    in.readBoolean(),in.readBoolean()));
            }
            if(in.available()!=0) throw new IllegalArgumentException("Trailing sensory frame bytes");
            return new NativeMindSenseFrame(bodyId,mindId,tick,dimension,body,entities,blocks);
        } catch(IOException malformed) {
            throw new IllegalArgumentException("Truncated sensory frame",malformed);
        }
    }

    private static void writeUuid(DataOutputStream out,UUID id)throws IOException {
        out.writeLong(id.getMostSignificantBits()); out.writeLong(id.getLeastSignificantBits());
    }
    private static UUID readUuid(DataInputStream in)throws IOException {
        return new UUID(in.readLong(),in.readLong());
    }
    private static void writeString(DataOutputStream out,String value)throws IOException {
        byte[] bytes=value.getBytes(StandardCharsets.UTF_8);
        if(bytes.length>MAX_STRING_BYTES) throw new IllegalArgumentException("Sensory string too long");
        out.writeShort(bytes.length); out.write(bytes);
    }
    private static String readString(DataInputStream in)throws IOException {
        int length=in.readUnsignedShort();
        if(length>MAX_STRING_BYTES) throw new IllegalArgumentException("Sensory string too long");
        byte[] bytes=in.readNBytes(length);
        if(bytes.length!=length) throw new IllegalArgumentException("Truncated sensory string");
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString();
        } catch(CharacterCodingException badUtf8) {
            throw new IllegalArgumentException("Malformed sensory UTF-8",badUtf8);
        }
    }
    private static void keyCheck(byte[] key) {
        if(key==null || key.length!=KEY_BYTES)
            throw new IllegalArgumentException("Expected a 256-bit sensory session key");
    }
    private static byte[] hmac(byte[] bytes,byte[] key) {
        try {
            Mac mac=Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key,"HmacSHA256"));
            return mac.doFinal(bytes);
        } catch(java.security.GeneralSecurityException unavailable) {
            throw new IllegalStateException("JDK HMAC-SHA256 unavailable",unavailable);
        }
    }
}
