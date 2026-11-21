package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Contract tests, NOT synthetic evidence that native Minecraft AI is isolated. */
public final class MindHostEngineSelfTest {
    private static int assertions;
    private static void check(boolean truth, String why) {
        assertions++;
        if (!truth) throw new AssertionError(why);
    }
    private static UUID id(long value) { return new UUID(0L, value); }

    /** These mock bodies keep their traits even when controllers trade places. */
    private static final class Body implements BodyActionGate.BodyPort {
        final UUID identity;
        final String type, loot, physicalInteraction;
        final double speed, maxHealth;
        final Set<String> supported;
        final List<String> executed = new ArrayList<>();
        Body(long id, String type, double speed, double maxHealth,
             String loot, String physicalInteraction, String... supported) {
            this.identity = MindHostEngineSelfTest.id(id);
            this.type = type;
            this.speed = speed;
            this.maxHealth = maxHealth;
            this.loot = loot;
            this.physicalInteraction = physicalInteraction;
            this.supported = Set.of(supported);
        }
        @Override public UUID id() { return identity; }
        @Override public boolean supports(String action) { return supported.contains(action); }
        @Override public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
            if ("test:blocked".equals(intent.actionId()))
                return BodyActionGate.Result.blocked("Physical state or cooldown");
            if ("test:bad_action".equals(intent.actionId()))
                throw new IllegalStateException("Synthetic physical failure");
            executed.add(intent.actionId());
            return BodyActionGate.Result.executed();
        }
    }

    private static MindHostEngine.IsolatedMindSource source(long mindId, String... actions) {
        return new MindHostEngine.IsolatedMindSource() {
            @Override public UUID mindId() { return id(mindId); }
            @Override public void decide(MindHostEngine.Observation context,
                                         MindHostEngine.IntentSink output) {
                for (String action : actions)
                    output.request(new BodyActionGate.Intent(action));
            }
        };
    }

    public static void main(String[] args) throws Exception {
        var engine = new MindHostEngine();
        Body cow = new Body(1, "cow", 0.2, 10, "beef/leather", "milking",
            "test:walk", "test:blocked", "test:bad_action");
        Body creeper = new Body(2, "creeper", 0.25, 20, "gunpowder", "fuse",
            "test:walk", "test:detonate", "test:blocked");
        var cowMind = new MindLedger.Mind(id(101), MindLedger.Kind.NPC, "test:cow");
        var creeperMind = new MindLedger.Mind(id(102), MindLedger.Kind.NPC, "test:creeper");
        engine.register(cow, cowMind, source(101, "test:walk"));
        engine.register(creeper, creeperMind, source(102, "test:walk", "test:detonate"));

        var before = engine.tick(new MindHostEngine.Observation(cow.id(), 1, Map.of()));
        check(before.status() == MindHostEngine.TickStatus.DISPATCHED, "original AI frame");
        check(before.mind().equals(cowMind.identity()), "cow mind initially controls cow");
        engine.exchange(cow.id(), creeper.id());
        check(engine.controlling(cow.id()).equals(creeperMind), "entire creeper mind assigned to cow");
        check(engine.controlling(creeper.id()).equals(cowMind), "entire cow mind assigned to creeper");
        check(cow.type.equals("cow") && cow.speed == 0.2 && cow.maxHealth == 10
            && cow.loot.equals("beef/leather") && cow.physicalInteraction.equals("milking"),
            "appearance, physical attributes, drops and milking stay with cow body");

        var cowFrame = engine.tick(new MindHostEngine.Observation(cow.id(), 2, Map.of("light","12")));
        check(cowFrame.mind().equals(creeperMind.identity()), "incoming controller ran for cow");
        check(cowFrame.intents().size() == 2 && cowFrame.outcomes().size() == 2,
            "all actions captured before dispatch");
        check(cowFrame.outcomes().get(0).status() == BodyActionGate.Status.EXECUTED,
            "cow body executes walking through its own locomotion");
        check(cowFrame.outcomes().get(1).status() == BodyActionGate.Status.UNAVAILABLE,
            "creeper desire to explode does not give cow explosive anatomy");
        check(cow.executed.equals(List.of("test:walk", "test:walk")),
            "only recipient cow executes recipient actions");

        var creeperFrame = engine.tick(new MindHostEngine.Observation(creeper.id(), 2, Map.of()));
        check(creeperFrame.mind().equals(cowMind.identity()) && creeperFrame.intents().size() == 1,
            "cow mind drives creeper body");
        check(creeper.executed.equals(List.of("test:walk")) && creeper.maxHealth == 20
            && creeper.loot.equals("gunpowder"), "creeper mechanics and loot remain intact");
        var duplicate = engine.tick(new MindHostEngine.Observation(cow.id(), 2, Map.of()));
        check(duplicate.status() == MindHostEngine.TickStatus.DUPLICATE_TICK
            && cow.executed.size() == 2, "at most one body evaluation per game tick");

        engine.exchange(cow.id(), creeper.id());
        check(engine.controlling(cow.id()).equals(cowMind)
            && engine.controlling(creeper.id()).equals(creeperMind), "reciprocal reverse restores minds");
        var restored = engine.tick(new MindHostEngine.Observation(creeper.id(), 3, Map.of()));
        check(restored.outcomes().get(1).status() == BodyActionGate.Status.EXECUTED,
            "creeper fuse can be requested again once creeper mind is back in its body");
        check(creeper.executed.contains("test:detonate"), "fuse request goes to creeper, not cow");

        try {
            engine.exchange(cow.id(), id(999));
            throw new AssertionError("unregistered body exchange");
        } catch (IllegalArgumentException expected) { assertions++; }
        check(engine.controlling(cow.id()).equals(cowMind), "rejected swap is all-or-nothing");
        try {
            engine.register(new Body(7, "broken", 1, 1, "", "", "test:walk"), cowMind,
                source(101, "test:walk"));
            throw new AssertionError("duplicate mind registered");
        } catch (IllegalStateException expected) { assertions++; }

        var faulted = new MindHostEngine();
        Body body = new Body(11, "probe", 1, 1, "", "", "test:walk", "test:bad_action");
        var identity = new MindLedger.Mind(id(201), MindLedger.Kind.NPC, "test:fault");
        faulted.register(body, identity, new MindHostEngine.IsolatedMindSource() {
            public UUID mindId() { return identity.identity(); }
            public void decide(MindHostEngine.Observation data, MindHostEngine.IntentSink sink) {
                sink.request(new BodyActionGate.Intent("test:walk"));
                throw new IllegalStateException("Native mind capture interrupted");
            }
        });
        var rejection = faulted.tick(new MindHostEngine.Observation(body.id(), 1, Map.of()));
        check(rejection.status() == MindHostEngine.TickStatus.CAPTURE_FAILED && body.executed.isEmpty(),
            "capture failure discards all uncommitted actions; no partial recipient mutation");
        var retry = faulted.tick(new MindHostEngine.Observation(body.id(), 1, Map.of()));
        check(retry.status() == MindHostEngine.TickStatus.DUPLICATE_TICK,
            "crashed mind cannot reexecute the same tick");

        var bounded = new MindHostEngine();
        Body boundedBody = new Body(12, "probe", 1, 1, "", "", "test:walk");
        var boundedMind = new MindLedger.Mind(id(202), MindLedger.Kind.NPC, "test:overflow");
        bounded.register(boundedBody, boundedMind, new MindHostEngine.IsolatedMindSource() {
            public UUID mindId() { return boundedMind.identity(); }
            public void decide(MindHostEngine.Observation data, MindHostEngine.IntentSink sink) {
                for (int n = 0; n <= MindHostEngine.MAX_ACTIONS_PER_FRAME; n++)
                    sink.request(new BodyActionGate.Intent("test:walk"));
            }
        });
        var overflow = bounded.tick(new MindHostEngine.Observation(boundedBody.id(), 1, Map.of()));
        check(overflow.status() == MindHostEngine.TickStatus.CAPTURE_FAILED
            && boundedBody.executed.isEmpty(), "runaway decision loop rejected before physical effects");

        var physicalFault = new MindHostEngine();
        Body fbody = new Body(14, "probe", 1, 1, "", "", "test:walk", "test:bad_action");
        var fmind = new MindLedger.Mind(id(204), MindLedger.Kind.NPC, "test:partial");
        physicalFault.register(fbody, fmind,
            source(204, "test:walk", "test:bad_action", "test:walk"));
        var partial = physicalFault.tick(new MindHostEngine.Observation(fbody.id(), 1, Map.of()));
        check(partial.status() == MindHostEngine.TickStatus.ACTION_FAILED
            && partial.outcomes().size() == 1 && fbody.executed.size() == 1,
            "physical dispatch failures are explicitly marked PARTIAL, never rolled back dishonestly");
        System.out.println("PASS MindHostEngineSelfTest: " + assertions
            + " assertions; cross-body control, whole swap, isolation boundary, fail-closed capture");
    }
}
