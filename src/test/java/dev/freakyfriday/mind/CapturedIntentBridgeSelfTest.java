package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Real production bridge, fake recipients. Not a modded native AI sandbox. */
public final class CapturedIntentBridgeSelfTest {
    static int checks;
    static void check(boolean yes, String reason) {
        checks++;
        if (!yes) throw new AssertionError(reason);
    }
    static UUID id(int value) { return new UUID(0, value); }

    static final class Body implements BodyActionGate.BodyPort {
        final UUID identity;
        final Set<String> actions;
        final List<String> received = new ArrayList<>();
        final String physicalType;
        Body(int id, String physicalType, String... actions) {
            this.identity = CapturedIntentBridgeSelfTest.id(id);
            this.physicalType = physicalType;
            this.actions = Set.of(actions);
        }
        @Override public UUID id() { return identity; }
        @Override public boolean supports(String action) { return actions.contains(action); }
        @Override public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
            if (intent.actionId().equals("test:throw"))
                throw new IllegalStateException("Injected physical dispatcher fault");
            received.add(intent.actionId());
            return BodyActionGate.Result.executed();
        }
    }
    static CapturedIntentBridge.Frame frame(int body, int mind, long tick,
                                            BodyActionGate.Intent... actions) {
        return new CapturedIntentBridge.Frame(id(body), id(mind), tick, List.of(actions));
    }

    public static void main(String[] args) throws Exception {
        var bridge = new CapturedIntentBridge();
        Body cow = new Body(1, "cow", "test:move", "test:throw");
        Body creeper = new Body(2, "creeper", "test:move", "test:detonate");
        var cowMind = new MindLedger.Mind(id(101), MindLedger.Kind.NPC, "test:cow");
        var creeperMind = new MindLedger.Mind(id(102), MindLedger.Kind.NPC, "test:creeper");
        bridge.register(cow, cowMind);
        bridge.register(creeper, creeperMind);

        var one = bridge.deliver(frame(1, 101, 1, new BodyActionGate.Intent("test:move")));
        check(one.status() == CapturedIntentBridge.Status.DISPATCHED, "baseline dispatch");
        check(cow.received.equals(List.of("test:move")), "original cow body applied action");
        check(creeper.received.isEmpty(), "different physical body unchanged");
        check(one.outcomes().get(0).status() == BodyActionGate.Status.EXECUTED,
            "original body capability executed");
        var duplicate = bridge.deliver(frame(1, 101, 1,
            new BodyActionGate.Intent("test:move")));
        check(duplicate.status() == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
            "same body and tick cannot replay");
        check(cow.received.size() == 1, "duplicate did not mutate original body");
        check(bridge.deliver(frame(1, 101, 0)).status()
            == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
            "stale tick blocked");
        check(bridge.deliver(frame(99, 101, 2)).status()
            == CapturedIntentBridge.Status.UNKNOWN_BODY, "unknown body blocked");

        bridge.exchange(cow.id(), creeper.id());
        check(bridge.controlling(cow.id()).equals(creeperMind), "full mind assigned to cow");
        check(bridge.controlling(creeper.id()).equals(cowMind), "other mind assigned to creeper");
        check(cow.physicalType.equals("cow") && creeper.physicalType.equals("creeper"),
            "species and physical traits remain original");

        var wrong = bridge.deliver(frame(1, 101, 2,
            new BodyActionGate.Intent("test:move")));
        check(wrong.status() == CapturedIntentBridge.Status.WRONG_CONTROLLER,
            "old owner cannot command cow after exchange");
        check(cow.received.size() == 1, "unauthorized frame has no effects");
        var swapped = bridge.deliver(frame(1, 102, 2,
            new BodyActionGate.Intent("test:move"),
            new BodyActionGate.Intent("test:detonate")));
        check(swapped.status() == CapturedIntentBridge.Status.DISPATCHED,
            "correct swapped mind admitted");
        check(swapped.outcomes().size() == 2, "both requests evaluated");
        check(swapped.outcomes().get(0).status() == BodyActionGate.Status.EXECUTED,
            "cow locomotion available to transferred mind");
        check(swapped.outcomes().get(1).status() == BodyActionGate.Status.UNAVAILABLE,
            "cow never inherits creeper detonation");
        check(cow.received.equals(List.of("test:move","test:move")),
            "only cow physical port performed locomotion");
        check(!cow.received.contains("test:detonate"),
            "unsupported anatomy never faked");

        var opposing = bridge.deliver(frame(2, 101, 2,
            new BodyActionGate.Intent("test:move")));
        check(opposing.status() == CapturedIntentBridge.Status.DISPATCHED,
            "opposite direction mind frame applies to original creeper body");
        check(creeper.received.equals(List.of("test:move")),
            "creeper body retains physical action port");
        bridge.exchange(cow.id(), creeper.id());
        check(bridge.controlling(cow.id()).equals(cowMind), "reverse exchange restored cow mind");
        check(bridge.controlling(creeper.id()).equals(creeperMind),
            "reverse exchange restored creeper mind");
        check(bridge.deliver(frame(1, 101, 2)).status()
                == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
            "rapid mind exchange cannot replay physical body on same tick");
        check(bridge.deliver(frame(1, 101, 3)).status()
                == CapturedIntentBridge.Status.DISPATCHED,
            "new tick after reversal is accepted");

        var incoming = new ArrayList<BodyActionGate.Intent>();
        incoming.add(new BodyActionGate.Intent("test:move"));
        var immutable = new CapturedIntentBridge.Frame(id(1), id(101), 4, incoming);
        incoming.add(new BodyActionGate.Intent("test:move"));
        check(immutable.actions().size() == 1,
            "input list mutations cannot change captured frame");
        try {
            immutable.actions().add(new BodyActionGate.Intent("test:move"));
            throw new AssertionError("frame collection was mutable");
        } catch (UnsupportedOperationException expected) { checks++; }
        var mutableMap = new java.util.HashMap<>(Map.of("speed","1"));
        var safeIntent = new BodyActionGate.Intent("test:move",mutableMap);
        mutableMap.put("speed","100");
        check(safeIntent.arguments().get("speed").equals("1"),
            "nested payload map immutable");
        check(bridge.deliver(immutable).status() ==
            CapturedIntentBridge.Status.DISPATCHED, "immutable frame executes correctly");

        try {
            bridge.register(new Body(3,"dupe"), cowMind);
            throw new AssertionError("duplicate mind admitted");
        } catch (IllegalStateException expected) { checks++; }
        check(bridge.deliver(frame(3,101,4)).status()
            == CapturedIntentBridge.Status.UNKNOWN_BODY, "rejected registration left no zombie port");
        try {
            new CapturedIntentBridge.Frame(id(1),id(101),5,
                java.util.Collections.nCopies(49,new BodyActionGate.Intent("test:move")));
            throw new AssertionError("unbounded action frame accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        try {
            new CapturedIntentBridge.Frame(id(1),id(101),5,
                List.of(new BodyActionGate.Intent("test:move",
                    Map.of("garbage","x".repeat(32769)))));
            throw new AssertionError("oversized payload accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        check(cow.received.size()==3, "invalid frames cannot dispatch");
        try {
            new CapturedIntentBridge.Frame(id(1),id(101),-1,List.of());
            throw new AssertionError("negative tick accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        try {
            bridge.exchange(cow.id(),id(999));
            throw new AssertionError("unknown exchange target accepted");
        } catch (IllegalArgumentException expected) { checks++; }
        check(bridge.controlling(cow.id()).equals(cowMind),
            "failed exchange must preserve ownership");

        var partial = bridge.deliver(frame(1,101,5,
            new BodyActionGate.Intent("test:move"),
            new BodyActionGate.Intent("test:throw"),
            new BodyActionGate.Intent("test:move")));
        check(partial.status() == CapturedIntentBridge.Status.PARTIAL_DISPATCH_FAILURE,
            "physical dispatch errors identified as partial execution");
        check(partial.outcomes().size()==1 && cow.received.size()==4,
            "partial dispatch must retain only actually completed prior effects");
        check(bridge.deliver(frame(1,101,5,
            new BodyActionGate.Intent("test:move"))).status()
            == CapturedIntentBridge.Status.DUPLICATE_OR_STALE_TICK,
            "failed physical frame may not re-run same tick");
        check(cow.received.size()==4,
            "failed physical frame never replays completed action");

        System.out.println("PASS CapturedIntentBridgeSelfTest: "+checks+
            " assertions; no donor callback invoked, bounded immutable frames,"+
            " swap admission, unsupported anatomy, fail-closed duplicate/partial dispatch");
    }
}
