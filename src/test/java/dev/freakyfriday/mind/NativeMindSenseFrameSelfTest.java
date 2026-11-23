package dev.freakyfriday.mind;

import java.util.List;
import java.util.UUID;

public final class NativeMindSenseFrameSelfTest {
    public static void main(String[] args) {
        UUID body=UUID.randomUUID(), mind=UUID.randomUUID(), target=UUID.randomUUID();
        var self=new NativeMindSenseFrame.BodySense("minecraft:chicken",
            10,64,-3,0.1,0,0,90,0,4,4,true,true,false,false);
        var entity=new NativeMindSenseFrame.EntitySense(target,"minecraft:zombie",
            2,0,-1,0,0,0,20,20,true,true,true);
        var block=new NativeMindSenseFrame.BlockSense(0,-1,0,
            "minecraft:grass_block","minecraft:empty",true,false);
        var frame=new NativeMindSenseFrame(body,mind,123,"minecraft:overworld",
            self,List.of(entity),List.of(block));
        assert frame.bodyId().equals(body);
        assert frame.entities().get(0).id().equals(target);
        assert frame.blocks().get(0).blockType().equals("minecraft:grass_block");
        int rejected=0;
        try { new NativeMindSenseFrame(body,mind,123,"minecraft:overworld",
            self,List.of(entity,entity),List.of()); }
        catch(IllegalArgumentException expected) { rejected++; }
        try { new NativeMindSenseFrame(body,mind,123,"minecraft:overworld",
            self,List.of(new NativeMindSenseFrame.EntitySense(target,"minecraft:zombie",
            33,0,0,0,0,0,20,20,true,true,true)),List.of()); }
        catch(IllegalArgumentException expected) { rejected++; }
        try { new NativeMindSenseFrame(body,mind,123,"minecraft:overworld",
            self,List.of(),List.of(new NativeMindSenseFrame.BlockSense(17,0,0,
            "minecraft:stone","minecraft:empty",true,false))); }
        catch(IllegalArgumentException expected) { rejected++; }
        try { new NativeMindSenseFrame(body,mind,123,"minecraft:overworld",
            new NativeMindSenseFrame.BodySense("minecraft:chicken",Double.NaN,
            64,-3,0,0,0,0,0,4,4,true,true,false,false),List.of(),List.of()); }
        catch(IllegalArgumentException expected) { rejected++; }
        assert rejected==4;
        System.out.println("PASS NativeMindSenseFrameSelfTest: 8 assertions; recipient-relative bounded sensory contract");
    }
}
