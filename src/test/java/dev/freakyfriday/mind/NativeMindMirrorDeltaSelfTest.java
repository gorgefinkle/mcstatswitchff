package dev.freakyfriday.mind;

import java.util.List;
import java.util.UUID;

public final class NativeMindMirrorDeltaSelfTest {
    private NativeMindMirrorDeltaSelfTest() {}
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        try { action.run(); } catch (RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static NativeMindSenseFrame frame(UUID body, UUID mind, long tick,
            List<NativeMindSenseFrame.EntitySense> entities,
            List<NativeMindSenseFrame.BlockSense> blocks) {
        return new NativeMindSenseFrame(body,mind,tick,"minecraft:overworld",
            new NativeMindSenseFrame.BodySense("minecraft:cow",0,0,0,
                0,0,0,0,0,10,10,true,true,false,false),entities,blocks);
    }
    private static NativeMindSenseFrame.EntitySense entity(UUID id,double x) {
        return new NativeMindSenseFrame.EntitySense(id,"minecraft:player",
            x,0,0,0,0,0,20,20,true,true,true);
    }
    private static NativeMindSenseFrame.BlockSense block(int x,String type) {
        return new NativeMindSenseFrame.BlockSense(x,0,0,type,
            "minecraft:empty",true,false);
    }
    public static void main(String[] args) {
        UUID body=UUID.randomUUID(), mind=UUID.randomUUID();
        UUID retained=UUID.randomUUID(), removed=UUID.randomUUID(), added=UUID.randomUUID();
        var b0=block(0,"minecraft:stone");
        var b1=block(1,"minecraft:dirt");
        var seed=frame(body,mind,10,List.of(entity(retained,1),entity(removed,2)),
            List.of(b0,b1));
        var initial=NativeMindMirrorDelta.between(null,seed);
        check(initial.fromTick()==-1 && initial.toTick()==10,"initial full seed tick range");
        check(initial.addedEntities().size()==2 && initial.changedEntities().isEmpty(),
            "initial seed adds every observed entity");
        check(initial.changedBlocks().size()==2 && initial.removedBlocks().isEmpty(),
            "initial seed installs every observed block");

        var b0Changed=block(0,"minecraft:water");
        var b2=block(2,"minecraft:glass");
        var next=frame(body,mind,11,
            List.of(entity(retained,1.5),entity(added,3)),
            List.of(b0Changed,b2));
        var delta=NativeMindMirrorDelta.between(seed,next);
        check(delta.fromTick()==10 && delta.toTick()==11,"consecutive tick identity");
        check(delta.addedEntities().equals(List.of(entity(added,3))),"new entity detected");
        check(delta.changedEntities().equals(List.of(entity(retained,1.5))),"moving entity updated");
        check(delta.removedEntities().equals(List.of(removed)),"vanished entity removed");
        check(delta.changedBlocks().equals(List.of(b0Changed,b2)),"changed and new blocks emitted");
        check(delta.removedBlocks().equals(List.of(new NativeMindMirrorDelta.BlockPosition(1,0,0))),
            "stale block removal emitted");

        rejects(()->NativeMindMirrorDelta.between(seed,
            frame(UUID.randomUUID(),mind,11,List.of(),List.of())),
            "body identity change rejected");
        rejects(()->NativeMindMirrorDelta.between(seed,
            new NativeMindSenseFrame(body,mind,11,"minecraft:the_nether",
                next.body(),next.entities(),next.blocks())),
            "dimension transition requires mirror reset");
        rejects(()->NativeMindMirrorDelta.between(seed,
            frame(body,mind,12,List.of(),List.of())),
            "tick gap rejected instead of applying incomplete delta");
        rejects(()->new NativeMindMirrorDelta(body,mind,"minecraft:overworld",
            -1,11,seed.body(),List.of(),List.of(entity(retained,1)),List.of(),
            List.of(),List.of()),"partial initial seed rejected");
        rejects(()->new NativeMindMirrorDelta(body,mind,"minecraft:overworld",
            10,11,seed.body(),List.of(entity(added,1),entity(added,2)),List.of(),
            List.of(),List.of(),List.of()),"duplicate entity operation rejected");
        rejects(()->new NativeMindMirrorDelta(body,mind,"minecraft:overworld",
            10,11,seed.body(),List.of(entity(added,1)),List.of(),List.of(added),
            List.of(),List.of()),"add and remove same entity rejected");
        rejects(()->new NativeMindMirrorDelta.BlockPosition(17,0,0),
            "out-of-bounds block position rejected");
        rejects(()->new NativeMindMirrorDelta(body,mind,"minecraft:overworld",
            10,11,seed.body(),List.of(),List.of(),List.of(),
            List.of(block(0,"minecraft:stone"),block(0,"minecraft:dirt")),List.of()),
            "duplicate block operation rejected");

        System.out.println("PASS NativeMindMirrorDeltaSelfTest: "+checks
            +" assertions; complete seed semantics, deterministic entity/block diffs, "
            +"identity, dimension, operation conflicts and bounds");
    }
}
