package dev.freakyfriday.mind;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Creates a bounded authenticated sensory fixture for the two-JVM worker test. */
public final class NativeMindSenseFixtureWriter {
    private NativeMindSenseFixtureWriter() {}

    private static String required(String key) {
        String value=System.getProperty("ff.fixture."+key);
        if(value==null || value.isBlank())
            throw new IllegalArgumentException("Missing -Dff.fixture."+key);
        return value;
    }

    public static void main(String[] args) throws Exception {
        UUID bodyId=UUID.fromString(required("body"));
        UUID mindId=UUID.fromString(required("mind"));
        UUID stimulusId=UUID.fromString(required("stimulusId"));
        long tick=Long.parseLong(required("tick"));
        String recipient=required("recipient");
        String stimulus=required("stimulus");
        double targetDx=Double.parseDouble(
            System.getProperty("ff.fixture.targetDx","8"));
        if(!Double.isFinite(targetDx) || targetDx==0.0 || Math.abs(targetDx)>8.0)
            throw new IllegalArgumentException("Fixture targetDx must be finite, nonzero and within [-8,8]");
        String keyHex=System.getenv("FF_WORKER_KEY_HEX");
        if(keyHex==null) throw new IllegalArgumentException("FF_WORKER_KEY_HEX missing");
        byte[] key=java.util.HexFormat.of().parseHex(keyHex);
        if(key.length!=NativeMindSenseWireCodec.KEY_BYTES)
            throw new IllegalArgumentException("Fixture HMAC key must be 256 bits");

        boolean chicken=recipient.equals("minecraft:chicken");
        boolean player=recipient.equals("minecraft:player");
        double bodyWidth=chicken?0.4:0.6;
        double bodyHeight=chicken?0.7:player?1.8:1.95;
        float bodyMaxHealth=chicken?4.0f:(recipient.equals("minecraft:piglin")?16.0f:20.0f);
        var body=new NativeMindSenseFrame.BodySense(recipient,0,0,0,
            0,0,0,0,0,bodyWidth,bodyHeight,bodyMaxHealth,bodyMaxHealth,
            true,true,false,false);

        double targetWidth=0.6,targetHeight=stimulus.equals("minecraft:player")?1.8:1.95;
        var target=new NativeMindSenseFrame.EntitySense(stimulusId,stimulus,
            targetDx,0,0,0,0,0,0,0,targetWidth,targetHeight,20,20,
            true,true,true);
        List<NativeMindSenseFrame.BlockSense> blocks=new ArrayList<>(17*9*17);
        for(int dx=-8;dx<=8;dx++) for(int dy=-4;dy<=4;dy++) for(int dz=-8;dz<=8;dz++) {
            boolean floor=dy==-1;
            blocks.add(new NativeMindSenseFrame.BlockSense(dx,dy,dz,
                floor?"minecraft:stone":"minecraft:air","minecraft:empty",
                floor,!floor,true,Map.of()));
        }
        var frame=new NativeMindSenseFrame(bodyId,mindId,tick,"minecraft:overworld",
            body,List.of(target),blocks);
        byte[] packet=NativeMindSenseWireCodec.encode(frame,key);
        if(packet.length>NativeMindSenseWireCodec.MAX_WIRE_BYTES)
            throw new IllegalStateException("Generated fixture exceeds wire bound");
        Path output=Path.of(required("output")).toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Files.write(output,packet);
        System.out.println("FF-SENSE fixture_written PASS bodyType="+recipient
            +" stimulus="+stimulus+" targetDx="+targetDx+" blockSamples="+blocks.size()
            +" authenticated=true bytes="+packet.length);
    }
}
