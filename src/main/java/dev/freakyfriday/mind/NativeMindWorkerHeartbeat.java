package dev.freakyfriday.mind;

import java.util.Objects;

/**
 * Fails a persistent native worker session when authenticated recipient
 * snapshots stop arriving. This protects the disposable worker from being
 * left alive and making decisions from stale recipient surroundings.
 *
 * Tick values are supplied by the worker server and recipient sensory stream;
 * this class never reads a clock or performs IO itself.
 */
public final class NativeMindWorkerHeartbeat {
    public static final int DEFAULT_MAX_SILENCE_TICKS = 10;

    private final int maxSilenceTicks;
    private long lastWorkerTick;
    private long lastSenseTick;
    private boolean failed;

    public NativeMindWorkerHeartbeat(long workerTick,long initialSenseTick,
            int maxSilenceTicks) {
        if(workerTick<0 || initialSenseTick<0
                || maxSilenceTicks<1 || maxSilenceTicks>200)
            throw new IllegalArgumentException("Invalid worker heartbeat policy");
        this.lastWorkerTick=workerTick;
        this.lastSenseTick=initialSenseTick;
        this.maxSilenceTicks=maxSilenceTicks;
    }

    public synchronized long lastWorkerTick() { return lastWorkerTick; }
    public synchronized long lastSenseTick() { return lastSenseTick; }
    public synchronized boolean failed() { return failed; }

    /** Admit exactly one next sensory tick within the worker's silence budget. */
    public synchronized void received(long workerTick,long sensoryTick) {
        requireActive();
        if(workerTick<0 || sensoryTick<0)
            fail("Negative worker or sensory tick");
        if(workerTick<=lastWorkerTick)
            fail("Worker tick did not advance between sensory frames");
        if(workerTick-lastWorkerTick>maxSilenceTicks)
            fail("Recipient sensory heartbeat exceeded silence deadline");
        if(lastSenseTick==Long.MAX_VALUE || sensoryTick!=lastSenseTick+1)
            fail("Recipient sensory tick was skipped, replayed or reordered");
        lastWorkerTick=workerTick;
        lastSenseTick=sensoryTick;
    }

    /** Called each worker server tick; true means the session is now terminal. */
    public synchronized boolean expire(long workerTick) {
        if(failed) return true;
        if(workerTick<lastWorkerTick) {
            failed=true;
            return true;
        }
        if(workerTick-lastWorkerTick>maxSilenceTicks) {
            failed=true;
            return true;
        }
        return false;
    }

    private void requireActive() {
        if(failed) throw new IllegalStateException("Native worker heartbeat is faulted");
    }

    private void fail(String message) {
        failed=true;
        throw new IllegalArgumentException(message);
    }
}
