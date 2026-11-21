package dev.freakyfriday.mind;

/** Classfile candidates are an audit aid, never an execution permission. */
public final class NativeAiEffectSurveySelfTest {
    private static int checks;
    private static void check(boolean valid, String reason) {
        checks++;
        if (!valid) throw new AssertionError(reason);
    }

    private static final class FakeWorld {
        void setBlock(int x, int y, int z) {}
        void addFreshEntity(Object object) {}
    }

    private static final class MutatingGoal {
        void tick(FakeWorld world) {
            world.setBlock(1, 2, 3);
            world.addFreshEntity(new Object());
        }
    }

    private static final class NoWorldCalls {
        int decide(int x) { return x * 2; }
    }

    private static final class LambdaGoal {
        final Runnable arbitrary;
        LambdaGoal(FakeWorld world) { arbitrary = () -> world.setBlock(0, 0, 0); }
    }

    public static void main(String[] args) {
        var world = NativeAiEffectSurvey.inspect(MutatingGoal.class);
        check(world.bytesAvailable(), "classfile resource could be loaded");
        check(world.candidates().stream().anyMatch(x ->
                x.operation().equals("setBlock")),
            "world writes classified as potential side effects");
        check(world.candidates().stream().anyMatch(x ->
                x.operation().equals("addFreshEntity")),
            "entity spawn recognized without per-mob rules");
        check(world.limitation().contains("not proven executed effects"),
            "report does not overclaim reachable world mutations");

        var simple = NativeAiEffectSurvey.inspect(NoWorldCalls.class);
        check(simple.bytesAvailable(), "simple classfile inspectable");
        check(simple.candidates().isEmpty(), "simple arithmetic has no effect candidates");
        check(simple.limitation().contains("NOT resolved"),
            "a clean report must not certify side-effect isolation");

        var lambda = NativeAiEffectSurvey.inspect(LambdaGoal.class);
        check(lambda.candidates().stream().anyMatch(x ->
                x.operation().equals("invokedynamic/constantdynamic")),
            "dynamic lambdas explicitly flagged as opaque");
        check(lambda.candidates().stream().anyMatch(x ->
                x.operation().equals("setBlock")),
            "lambda's method reference still seen when available");

        System.out.println("PASS NativeAiEffectSurveySelfTest: " + checks
            + " checks; candidate detection, dynamic calls, truthful limitations");
    }
}
