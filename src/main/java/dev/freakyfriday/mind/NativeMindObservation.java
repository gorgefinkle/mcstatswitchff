package dev.freakyfriday.mind;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Species-independent observation of an ACTUAL native AI decision.
 *
 * A separate NeoForge host runs the donor's own Goal, Brain and mob-class
 * logic in its disposable world. When native navigation has demonstrably
 * moved the donor, this projects its observed direction into coordinates
 * RELATIVE to a different physical body. It does not synthesize any donor
 * goal, target class or species-specific decision recipe.
 *
 * This is deliberately only the navigation/looking part of a future complete
 * native-action capture engine. Damage, projectiles, blocks, inventory,
 * effects and arbitrary modded side effects still require interception.
 */
public final class NativeMindObservation {
    private NativeMindObservation() {}

    public static final double MIN_NATIVE_TRAVEL=0.35;
    public static final double DESIRED_OFFSET=3.5;

    public record NativeNavigation(double dx, double dy, double dz,
                                   double observedDistance) {
        public NativeNavigation {
            if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)
                    || !Double.isFinite(observedDistance) || observedDistance<0)
                throw new IllegalArgumentException("Invalid native navigation observation");
            if (dx*dx+dy*dy+dz*dz>144)
                throw new IllegalArgumentException("Native movement offset outside bound");
        }
    }

    /**
     * Returns null until the DONOR actually moved under native AI.
     * A navigation goal existing in metadata, but never moving, is NOT a
     * demonstrated decision and must not produce a fake success frame.
     */
    public static NativeNavigation observe(double initialX,double initialY,double initialZ,
                                           double actualX,double actualY,double actualZ,
                                           boolean nativeNavigationSeen) {
        if (!nativeNavigationSeen) return null;
        double dx=actualX-initialX;
        double dz=actualZ-initialZ;
        double distance=Math.hypot(dx,dz);
        if (!Double.isFinite(distance) || distance<MIN_NATIVE_TRAVEL)
            return null;
        double scale=DESIRED_OFFSET/distance;
        return new NativeNavigation(dx*scale,0,dz*scale,distance);
    }

    /** Never embed disposable worker coordinates in receiver-body movement. */
    public static CapturedIntentBridge.Frame frame(UUID recipientBody,UUID donorMind,
                                                     long serverTick,NativeNavigation movement) {
        var offset=Map.of(
            "dx",Double.toString(movement.dx()),
            "dy",Double.toString(movement.dy()),
            "dz",Double.toString(movement.dz()));
        return new CapturedIntentBridge.Frame(recipientBody,donorMind,serverTick,
            List.of(
                new BodyActionGate.Intent(MinecraftBodyPort.LOOK_OFFSET,offset),
                new BodyActionGate.Intent(MinecraftBodyPort.MOVE_OFFSET,
                    Map.of("dx",offset.get("dx"),"dy",offset.get("dy"),
                           "dz",offset.get("dz"),"speed","0.65"))
            ));
    }
}
