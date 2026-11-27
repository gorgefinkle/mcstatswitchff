package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;

/** Executable regression checks for nested controller/body context handling. */
public final class MindBodyContextSelfTest {
    private MindBodyContextSelfTest() {}
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        ServerLevel level = new ServerLevel(net.minecraft.resources.Identifier.withDefaultNamespace("overworld"));
        Mob a = new Mob(level);
        Mob b = new Mob(level);
        Mob c = new Mob(level);

        check(!MindBodyContext.isBound(), "context starts empty");
        check(MindBodyContext.bodyFor(a) == a, "unbound controller resolves to itself");
        check(MindBodyContext.controllerFor(a) == a, "unbound body resolves to itself");

        try (MindBodyContext.Scope ignored = MindBodyContext.bind(a, b)) {
            check(MindBodyContext.isBound(), "outer context is active");
            check(MindBodyContext.bodyFor(a) == b, "controller A sees physical body B");
            check(MindBodyContext.controllerFor(b) == a, "body B resolves to controller A");
            check(MindBodyContext.isExecutingController(a), "controller A is active");
            check(!MindBodyContext.isExecutingController(b), "physical body B is not mistaken for controller");
            check(MindBodyContext.bodyFor(c) == c, "unrelated mob is unaffected");

            try (MindBodyContext.Scope nested = MindBodyContext.bind(c, a)) {
                check(MindBodyContext.bodyFor(c) == a, "nested controller C sees body A");
                check(MindBodyContext.controllerFor(a) == c, "nested body A resolves to controller C");
                check(MindBodyContext.isExecutingController(c), "nested controller is active");
                check(!MindBodyContext.isExecutingController(a), "outer controller is not the active inner invocation");
            }

            check(MindBodyContext.bodyFor(a) == b, "closing nested scope restores outer binding");
            check(MindBodyContext.controllerFor(b) == a, "closing nested scope restores inverse lookup");
        }

        check(!MindBodyContext.isBound(), "closing outer scope clears thread-local context");
        check(MindBodyContext.bodyFor(a) == a, "outer close restores default body resolution");

        // Exception unwinding must never leak a mapping into a later entity tick.
        try {
            try (MindBodyContext.Scope ignored = MindBodyContext.bind(a, b)) {
                throw new IllegalStateException("fixture");
            }
        } catch (IllegalStateException expected) {
            check(expected.getMessage().equals("fixture"), "fixture exception propagated");
        }
        check(!MindBodyContext.isBound(), "try/finally unwinding clears context after exception");

        MindBodyContext.Scope outer = MindBodyContext.bind(a, b);
        MindBodyContext.Scope inner = MindBodyContext.bind(c, a);
        try {
            outer.close();
            throw new AssertionError("out-of-order close must fail");
        } catch (IllegalStateException expected) {
            check(expected.getMessage().contains("LIFO"), "out-of-order close is detected");
        }
        inner.close();
        outer.close();
        check(!MindBodyContext.isBound(), "valid LIFO cleanup restores empty context");

        System.out.println("PASS MindBodyContextSelfTest: " + checks
            + " checks; nested mapping, reverse lookup, exception cleanup and LIFO validation");
    }
}
