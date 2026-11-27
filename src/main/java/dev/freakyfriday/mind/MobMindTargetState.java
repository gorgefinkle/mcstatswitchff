package dev.freakyfriday.mind;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;

/**
 * The current combat target belongs to the acting mind, not to the body that
 * happened to host it before exchange. Move it with the goal and target
 * selectors. The receiving Mob still applies its own setTarget validation,
 * so species/body restrictions remain authoritative.
 */
public final class MobMindTargetState {
    private MobMindTargetState() {}

    public record Snapshot(LivingEntity firstTarget, LivingEntity secondTarget) {}

    public static Snapshot capture(Mob first, Mob second) {
        return new Snapshot(first.getTargetUnchecked(), second.getTargetUnchecked());
    }

    public static boolean matches(Mob first, Mob second, Snapshot snapshot) {
        return first.getTargetUnchecked() == snapshot.firstTarget()
            && second.getTargetUnchecked() == snapshot.secondTarget();
    }

    public static void exchange(Mob first, Mob second, Snapshot snapshot) {
        assign(first, snapshot.secondTarget());
        assign(second, snapshot.firstTarget());
    }

    public static void restore(Mob first, Mob second, Snapshot snapshot) {
        assign(first, snapshot.firstTarget());
        assign(second, snapshot.secondTarget());
    }

    private static void assign(Mob body, LivingEntity target) {
        // Clear first because species overrides may intentionally refuse one
        // target class; a rejected incoming target must not leave the body's
        // previous physical target attached to the new mind.
        body.setTarget(null);
        if (target != null) body.setTarget(target);
    }
}
