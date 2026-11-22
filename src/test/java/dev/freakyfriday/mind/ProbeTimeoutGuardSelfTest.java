package dev.freakyfriday.mind;

/** Run directly in CI, no JUnit discovery required. */
public final class ProbeTimeoutGuardSelfTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void rejects(Runnable call, String message) {
        checks++;
        try { call.run(); throw new AssertionError(message); }
        catch (IllegalArgumentException expected) {}
    }

    public static void main(String[] args) {
        check(!ProbeTimeoutGuard.expired(0, 0, 120), "new probe alive");
        check(!ProbeTimeoutGuard.expired(119, 0, 120), "just before threshold");
        check(ProbeTimeoutGuard.expired(120, 0, 120), "expire at exact threshold");
        check(ProbeTimeoutGuard.expired(121, 0, 120), "expire after threshold");
        check(!ProbeTimeoutGuard.expired(161, 42, 120),
            "recipient progress resets watchdog even with old session");
        check(ProbeTimeoutGuard.expired(162, 42, 120),
            "recipient idle for 120 ticks is collectible");
        check(!ProbeTimeoutGuard.expired(1_000_000, 999_981, 120),
            "long-running server not affected by absolute uptime");
        rejects(() -> ProbeTimeoutGuard.expired(1, 2, 120), "backward server tick");
        rejects(() -> ProbeTimeoutGuard.expired(1, 1, 0), "zero threshold");
        rejects(() -> ProbeTimeoutGuard.expired(1, 1, -1), "negative threshold");
        System.out.println("PASS ProbeTimeoutGuardSelfTest: " + checks
            + " assertions; deterministic stalled-session lifecycle gate");
    }
}
