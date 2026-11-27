package dev.freakyfriday.mind;

import java.util.Objects;
import net.minecraft.world.entity.Mob;

/**
 * Scoped mapping used while a native Mob AI controller is executing for a
 * different physical Mob body.
 *
 * Controller-owned native state stays on the controller Mob. Shared physical
 * state/control services can query the current body through this context.
 * This type is deliberately inert until the Mob AI dispatcher is enabled.
 *
 * All use is confined to one logical server tick thread. Nested dispatches are
 * supported and must always close in strict LIFO order.
 */
public final class MindBodyContext {
    private static final ThreadLocal<Frame> CURRENT = new ThreadLocal<>();

    private MindBodyContext() {}

    /** Pushes a controller → physical-body mapping for the current call stack. */
    public static Scope bind(Mob controller, Mob physicalBody) {
        Objects.requireNonNull(controller, "controller");
        Objects.requireNonNull(physicalBody, "physicalBody");
        Frame frame = new Frame(controller, physicalBody, CURRENT.get(), Thread.currentThread());
        CURRENT.set(frame);
        return new Scope(frame);
    }

    /** Resolve the physical body associated with this controller in the active nested context. */
    public static Mob bodyFor(Mob controller) {
        Objects.requireNonNull(controller, "controller");
        for (Frame frame = CURRENT.get(); frame != null; frame = frame.parent) {
            if (frame.controller == controller) return frame.physicalBody;
        }
        return controller;
    }

    /** Resolve the active controller for a physical body in the current nested context. */
    public static Mob controllerFor(Mob physicalBody) {
        Objects.requireNonNull(physicalBody, "physicalBody");
        for (Frame frame = CURRENT.get(); frame != null; frame = frame.parent) {
            if (frame.physicalBody == physicalBody) return frame.controller;
        }
        return physicalBody;
    }

    /** True only while this Mob's own native serverAiStep is being invoked as controller. */
    public static boolean isExecutingController(Mob mob) {
        Frame current = CURRENT.get();
        return current != null && current.controller == mob;
    }

    public static boolean isBound() {
        return CURRENT.get() != null;
    }

    private record Frame(Mob controller, Mob physicalBody, Frame parent, Thread ownerThread) {}

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
