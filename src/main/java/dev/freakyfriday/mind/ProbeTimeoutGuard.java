package dev.freakyfriday.mind;

/** Pure Java watchdog threshold shared by detached and physical probes. */
public final class ProbeTimeoutGuard {
    private ProbeTimeoutGuard() {}

    public static boolean expired(long currentServerTick, long lastProgressTick,
                                  long allowedIdleTicks) {
        if (allowedIdleTicks <= 0)
            throw new IllegalArgumentException("Idle threshold must be positive");
        if (currentServerTick < lastProgressTick)
            throw new IllegalArgumentException("Server tick clock moved backwards");
        return currentServerTick - lastProgressTick >= allowedIdleTicks;
    }
}
