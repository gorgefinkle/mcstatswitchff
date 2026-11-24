package dev.freakyfriday.mind;

import java.util.List;
import java.util.UUID;

public final class NativeMindSenseWireCodecSelfTest {
    public static void main(String[] args) {
        byte[] key=new byte[NativeMindSenseWireCodec.KEY_BYTES];
        java.util.Arrays.fill(key,(byte)0x5a);
        UUID body=UUID.randomUUID(),mind=UUID.randomUUID(),target=UUID.randomUUID();
        var self=new NativeMindSenseFrame.BodySense("minecraft:chicken",
            0,0,0,0.1,0,0,90,0,0.7,0.7,4,4,true,true,false,false);
        var entity=new NativeMindSenseFrame.EntitySense(target,"minecraft:zombie",
            2,0,-1,0,0,0,35,0,0.6,1.8,20,20,true,true,true);
        var block=new NativeMindSenseFrame.BlockSense(0,-1,0,
            "minecraft:oak_log","minecraft:empty",true,false,true,
            java.util.Map.of("axis","y","waterlogged","false"));
        var frame=new NativeMindSenseFrame(body,mind,123,"minecraft:overworld",
            self,List.of(entity),List.of(block,
                new NativeMindSenseFrame.BlockSense(1,-1,0,"minecraft:air",
                    "minecraft:empty",false,true,false,java.util.Map.of())));
        byte[] wire=NativeMindSenseWireCodec.encode(frame,key);
        var decoded=NativeMindSenseWireCodec.decode(wire,key,body,mind,123);
        assert decoded.equals(frame);
        int rejected=0;
        byte[] tampered=wire.clone(); tampered[12]^=1;
        try { NativeMindSenseWireCodec.decode(tampered,key,body,mind,123); }
        catch(IllegalArgumentException expected) { rejected++; }
        try { NativeMindSenseWireCodec.decode(wire,key,UUID.randomUUID(),mind,123); }
        catch(IllegalArgumentException expected) { rejected++; }
        try { NativeMindSenseWireCodec.decode(wire,key,body,mind,124); }
        catch(IllegalArgumentException expected) { rejected++; }
        try { NativeMindSenseWireCodec.decode(wire,new byte[8],body,mind,123); }
        catch(IllegalArgumentException expected) { rejected++; }
        assert rejected==4;
        System.out.println("PASS NativeMindSenseWireCodecSelfTest: 5 assertions; HMAC, identity, replay and block-state fidelity checks");
    }
}
