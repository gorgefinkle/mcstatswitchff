package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Executable ownership-map regression tests; no Minecraft stubs or species profiles. */
public final class MindBodyBindingsSelfTest {
    private MindBodyBindingsSelfTest() {}
    private static int checks;

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID c = UUID.fromString("00000000-0000-0000-0000-000000000003");

        MindBodyBindings.ensureOriginalBinding(a);
        MindBodyBindings.ensureOriginalBinding(b);
        MindBodyBindings.ensureOriginalBinding(c);
        check(MindBodyBindings.controllerForBody(a).equals(a), "A initially controls A");
        check(MindBodyBindings.controllerForBody(b).equals(b), "B initially controls B");
        check(MindBodyBindings.bodyForController(c).equals(c), "C initially occupies C");

        var first = MindBodyBindings.exchangeBodies(a, b);
        check(first.firstController().equals(a) && first.secondController().equals(b),
            "exchange reports the prior controller assignments");
        check(MindBodyBindings.controllerForBody(a).equals(b), "B controller maps to A body");
        check(MindBodyBindings.controllerForBody(b).equals(a), "A controller maps to B body");
        check(MindBodyBindings.bodyForController(a).equals(b), "inverse lookup resolves A controller to B body");
        check(MindBodyBindings.bodyForController(b).equals(a), "inverse lookup resolves B controller to A body");
        check(MindBodyBindings.controllerForBody(c).equals(c), "unrelated C assignment remains untouched");

        // Reversing the same pair restores both original mappings.
        MindBodyBindings.exchangeBodies(a, b);
        check(MindBodyBindings.controllerForBody(a).equals(a), "reverse restores A");
        check(MindBodyBindings.controllerForBody(b).equals(b), "reverse restores B");

        // Repeated exchanges preserve bijection and always keep the same two mind IDs.
        for (int i = 0; i < 500; i++) {
            MindBodyBindings.exchangeBodies(a, b);
            var snapshot = MindBodyBindings.snapshot();
            check(snapshot.controllerByBody().size() == 3, "pair exchange retains all body entries");
            check(snapshot.bodyByController().size() == 3, "pair exchange retains all controller entries");
            check(snapshot.controllerByBody().values().stream().distinct().count() == 3,
                "each controller remains uniquely assigned");
        }
        check(MindBodyBindings.controllerForBody(a).equals(a), "500 exchanges finish at original assignment");
        check(MindBodyBindings.controllerForBody(b).equals(b), "500 exchanges preserve reverse assignment");

        // Ensure registration after an exchange cannot silently reset it.
        MindBodyBindings.exchangeBodies(a, b);
        MindBodyBindings.ensureOriginalBinding(a);
        check(MindBodyBindings.controllerForBody(a).equals(b), "re-observation never resets an exchanged controller");
        MindBodyBindings.exchangeBodies(a, b);

        // Snapshot maps are immutable and detached from future changes.
        var before = MindBodyBindings.snapshot();
        MindBodyBindings.exchangeBodies(a, b);
        check(before.controllerByBody().get(a).equals(a), "snapshot remains immutable historical state");
        check(MindBodyBindings.controllerForBody(a).equals(b), "live mapping changes independently of snapshot");

        try {
            MindBodyBindings.exchangeBodies(a, a);
            throw new AssertionError("self exchange must fail");
        } catch (IllegalArgumentException expected) {
            check(expected.getMessage().contains("itself"), "self exchange rejected precisely");
        }

        System.out.println("PASS MindBodyBindingsSelfTest: " + checks
            + " checks; species-agnostic pairing, reverse, bijection, idempotent observation");
    }
}
