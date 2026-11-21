package dev.freakyfriday.mind;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** No donor-species pairing or hardcoded list is needed for modded actions. */
public final class BodyCapabilityRegistrySelfTest {
    private static int checks;
    private static void check(boolean ok, String why) {
        checks++;
        if (!ok) throw new AssertionError(why);
    }
    private record Body(String label, Set<String> abilities) {}
    private static final class Port implements BodyActionGate.BodyPort {
        private final UUID id;
        private final Body body;
        private final BodyCapabilityRegistry<Body> registry;
        Port(int n, Body body, BodyCapabilityRegistry<Body> registry) {
            id = new UUID(0L, n);
            this.body = body;
            this.registry = registry;
        }
        @Override public UUID id() { return id; }
        @Override public boolean supports(String action) {
            return registry.supports(body, action);
        }
        @Override public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
            return registry.execute(body, intent);
        }
    }

    public static void main(String[] args) {
        var registry = new BodyCapabilityRegistry<Body>();
        registry.register("freakyfriday:move", new BodyCapabilityRegistry.Executor<>() {
            @Override public boolean supports(Body b) { return b.abilities.contains("legs"); }
            @Override public BodyActionGate.Result perform(Body b, BodyActionGate.Intent i) {
                return BodyActionGate.Result.executed();
            }
        });
        registry.register("modded:teleport", new BodyCapabilityRegistry.Executor<>() {
            @Override public boolean supports(Body b) { return b.abilities.contains("teleporter"); }
            @Override public BodyActionGate.Result perform(Body b, BodyActionGate.Intent i) {
                if (i.arguments().containsKey("cooldown"))
                    return BodyActionGate.Result.blocked("Body ability cooling down");
                return BodyActionGate.Result.executed();
            }
        });
        registry.register("modded:teleport", new BodyCapabilityRegistry.Executor<>() {
            @Override public boolean supports(Body b) { return b.abilities.contains("blink"); }
            @Override public BodyActionGate.Result perform(Body b, BodyActionGate.Intent i) {
                return BodyActionGate.Result.executed();
            }
        });

        var cow = new Port(1, new Body("cow", Set.of("legs")), registry);
        var modded = new Port(2, new Body("modded", Set.of("legs", "blink")), registry);
        var mindIntent = new BodyActionGate.Intent("modded:teleport");
        check(registry.registeredActions().get("modded:teleport") == 2,
            "many implementations can supply a single capability without species pair rules");
        check(BodyActionGate.dispatch(cow, mindIntent).status() == BodyActionGate.Status.UNAVAILABLE,
            "a modded mind cannot grant a cow an absent teleport mechanism");
        check(BodyActionGate.dispatch(modded, mindIntent).status() == BodyActionGate.Status.EXECUTED,
            "unknown modded body can supply an action dynamically");
        check(BodyActionGate.dispatch(cow,
            new BodyActionGate.Intent("freakyfriday:move")).status() == BodyActionGate.Status.EXECUTED,
            "shared physical operations require no species-specific adapter");
        check(registry.execute(new Body("other", Set.of()), mindIntent).status()
            == BodyActionGate.Status.UNAVAILABLE, "registry fails closed when no provider applies");

        var mismatched = new BodyCapabilityRegistry<Body>();
        mismatched.register("test:bad", new BodyCapabilityRegistry.Executor<>() {
            public boolean supports(Body b) { return true; }
            public BodyActionGate.Result perform(Body b, BodyActionGate.Intent i) {
                return new BodyActionGate.Result(BodyActionGate.Status.UNAVAILABLE, "bad provider");
            }
        });
        try {
            mismatched.execute(new Body("x", Set.of()), new BodyActionGate.Intent("test:bad"));
            throw new AssertionError("invalid capability result");
        } catch (IllegalStateException expected) { checks++; }
        try {
            registry.register("bad id", new BodyCapabilityRegistry.Executor<>() {
                public boolean supports(Body b) { return true; }
                public BodyActionGate.Result perform(Body b, BodyActionGate.Intent i) {
                    return BodyActionGate.Result.executed();
                }
            });
            throw new AssertionError("invalid registry action ID");
        } catch (IllegalArgumentException expected) { checks++; }
        check(!registry.registeredActions().containsKey("bad id"),
            "invalid registration never mutates registry");

        System.out.println("PASS BodyCapabilityRegistrySelfTest: " + checks
            + " assertions; generic extension, missing anatomy and multiple mod providers");
    }
}
