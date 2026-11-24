package dev.freakyfriday.mind;

import java.util.List;
import java.util.UUID;

public final class NativeMindMirrorStateSelfTest {
    private NativeMindMirrorStateSelfTest() {}
    private static int checks;
    private static void check(boolean ok,String message) {
        checks++; if(!ok) throw new AssertionError(message);
    }
    private static void rejects(Runnable action,String message) {
        try { action.run(); } catch(RuntimeException expected) { checks++; return; }
        throw new AssertionError(message);
    }
    private static NativeMindSenseFrame frame(UUID body,UUID mind,long tick,
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
    private static NativeMindSenseFrame.BlockSense block(int x,String id) {
        return new NativeMindSenseFrame.BlockSense(x,0,0,id,
            "minecraft:empty",true,false);
    }
    private static NativeMindMirrorDelta delta(UUID body,UUID mind,long from,long to,
            List<NativeMindSenseFrame.EntitySense> added,
            List<NativeMindSenseFrame.EntitySense> changed,List<UUID> removed,
            List<NativeMindSenseFrame.BlockSense> changedBlocks,
            List<NativeMindMirrorDelta.BlockPosition> removedBlocks) {
        return new NativeMindMirrorDelta(body,mind,"minecraft:overworld",from,to,
            new NativeMindSenseFrame.BodySense("minecraft:cow",0,0,0,
                0,0,0,0,0,10,10,true,true,false,false),
            added,changed,removed,changedBlocks,removedBlocks);
    }
    public static void main(String[] args) {
        UUID body=UUID.randomUUID(),mind=UUID.randomUUID(),a=UUID.randomUUID(),b=UUID.randomUUID();
        var first=frame(body,mind,20,List.of(entity(a,1),entity(b,2)),
            List.of(block(0,"minecraft:stone"),block(1,"minecraft:dirt")));
        var state=new NativeMindMirrorState();
        check(!state.initialized() && state.snapshot()==null,"starts uninitialized");
        var seed=state.apply(NativeMindMirrorDelta.between(null,first));
        check(seed.tick()==20 && seed.entities().size()==2 && seed.blocks().size()==2,
            "full seed committed");
        var second=frame(body,mind,21,List.of(entity(a,1.5)),
            List.of(block(0,"minecraft:water"),block(2,"minecraft:glass")));
        var normalDelta=NativeMindMirrorDelta.between(first,second);
        var updated=state.apply(normalDelta);
        check(updated.tick()==21 && updated.entities().size()==1 && !updated.entities().containsKey(b),
            "entity removal and movement committed");
        check(updated.blocks().size()==2
            && updated.blocks().get(new NativeMindMirrorDelta.BlockPosition(0,0,0)).blockType().equals("minecraft:water")
            && !updated.blocks().containsKey(new NativeMindMirrorDelta.BlockPosition(1,0,0))
            && updated.blocks().containsKey(new NativeMindMirrorDelta.BlockPosition(2,0,0)),
            "block update, removal and addition committed");

        var stable=state.snapshot();
        rejects(()->state.apply(normalDelta),"replayed delta rejected");
        check(state.snapshot()==stable,"replayed delta leaves published snapshot unchanged");
        rejects(()->state.apply(delta(body,mind,21,22,List.of(),List.of(),
            List.of(UUID.randomUUID()),List.of(),List.of())),
            "removing an entity absent from mirror rejected");
        check(state.snapshot()==stable,"invalid entity removal is atomic");
        rejects(()->state.apply(delta(body,mind,21,22,List.of(),List.of(),
            List.of(a),List.of(),List.of())),
            "removing an entity and also changing it rejected at delta construction");
        check(state.snapshot()==stable,"conflicting entity operations leave state unchanged");
        rejects(()->state.apply(delta(body,mind,21,22,List.of(),List.of(entity(b,3)),
            List.of(),List.of(),List.of())),
            "changing an entity absent from mirror rejected");
        check(state.snapshot()==stable,"invalid changed entity is atomic");
        rejects(()->state.apply(delta(body,mind,21,22,List.of(entity(a,9)),List.of(),
            List.of(),List.of(),List.of())),
            "adding existing entity rejected");
        check(state.snapshot()==stable,"duplicate existing entity is atomic");
        rejects(()->state.apply(delta(body,mind,21,22,List.of(),List.of(),List.of(),
            List.of(),List.of(new NativeMindMirrorDelta.BlockPosition(9,0,0)))),
            "removing absent block rejected");
        check(state.snapshot()==stable,"invalid block removal is atomic");

        var unknown=new NativeMindSenseFrame.BlockSense(4,0,0,"minecraft:air",
            "minecraft:empty",false,true,false,java.util.Map.of());
        var nowKnown=block(4,"minecraft:stone");
        var third=frame(body,mind,22,List.of(entity(a,2)),
            List.of(block(0,"minecraft:water"),block(2,"minecraft:glass"),unknown));
        var gap=NativeMindMirrorDelta.between(second,third);
        var other=new NativeMindMirrorState();
        rejects(()->other.apply(gap),"non-seed cannot initialize empty mirror");
        state.reset();
        check(!state.initialized(),"reset clears mirror");
        check(state.apply(NativeMindMirrorDelta.between(null,third)).tick()==22,
            "reset permits fresh complete seed");
        var fourth=frame(body,mind,23,List.of(entity(a,2)),
            List.of(block(0,"minecraft:water"),block(2,"minecraft:glass"),nowKnown));
        var visibilityDelta=NativeMindMirrorDelta.between(third,fourth);
        check(visibilityDelta.changedBlocks().equals(List.of(nowKnown)),
            "unknown-to-known chunk/block transition is retained as a state change");
        check(state.apply(visibilityDelta).blocks().get(
            new NativeMindMirrorDelta.BlockPosition(4,0,0)).known(),
            "unknown-to-known block state committed");

        System.out.println("PASS NativeMindMirrorStateSelfTest: "+checks
            +" assertions; atomic seed/delta commit, strict removals, identity reconciliation, "
            +"conflict rejection, rollback-on-invalid-update, unknown-to-known transition and reset");
    }
}
