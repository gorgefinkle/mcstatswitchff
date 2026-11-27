package dev.freakyfriday.mind;

import java.util.Objects;

/**
 * Re-entrant, thread-scoped mapping between an original controller host and
 * the physical body it currently drives.
 *
 * The context stores identity tokens rather than Minecraft-specific objects,
 * which lets the same mechanism serve NPC controllers and player controllers.
 * Callers are responsible for casting a resolved body to the expected type.
 *
 * All use is confined to a single logical server tick thread. Nested dispatches
 * are supported and must close in strict LIFO order.
 */
public final class MindBodyContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private MindBodyContext() {}

    /** Pushes a controller → physical-body mapping for the current call stack. */
    public static Scope bind(Object controller, Object physicalBody) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(physicalBody, "physicalBody");
        Frame frame = new Frame(controller, physicalBody, CURRENT.get(), Thread.currentThread());
        CURRENT.set(frame);
        return new Scope(frame);
    }

    /** Resolves the physical body associated with this controller, or the input itself. */
    public static Object bodyFor(Object controller) {
        Objects.requireNonNull(controller, "controller");
        for (Frame frame = CURRENT.get(); frame != null; frame = frame.parent) {
            if (frame.controller == controller) return frame.physicalBody;
        }
        return controller;
    }

    /** Resolves the active controller for a physical body, or the input itself. */
    public static Object controllerFor(Object physicalBody) {
        Objects.requireNonNull(physicalBody, "physicalBody");
        for (Frame frame = CURRENT.get(); frame != null; frame = frame.parent) {
            if (frame.physicalBody == physicalBody) return frame.controller;
        }
        return physicalBody;
    }

    /** True only while this object is the active native controller invocation. */
    public static boolean isExecutingController(Object controller) {
        Frame current = CURRENT.get();
        return current != null && current.controller == controller;
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    private record Frame(Object controller, Object physicalBody, Frame parent, Thread ownerThread) {}

    public static final class Scope implements AutoCloseable {
        private final Frame frame;
        private boolean closed;

        private Scope(Frame frame) {
            this.frame = frame;
        }

        @Override
        public void close() {
            if (closed) return;
            if (Thread.currentThread() != frame.ownerThread) {
                throw new IllegalStateException("Mind/body context must be closed on its opening server thread");
            }
            if (CURRENT.get() != frame) {
                throw new IllegalStateException("Mind/body contexts must be closed in LIFO order");
            }
            if (frame.parent == null) CURRENT.remove();
            else CURRENT.set(frame.parent);
            closed = true;
        }
    }
}
