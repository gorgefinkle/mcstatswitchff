package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;

/** Routes authenticated client controls to the NPC physical body. */
public final class PlayerInputBridge {
    private static final Map<UUID, Controls> CONTROLS = new HashMap<>();

    private PlayerInputBridge() {}

    private record Controls(Input input, float yaw, float pitch) {}

    public static void begin(ServerPlayer player, Mob body) {
        CONTROLS.put(player.getUUID(), new Controls(Input.EMPTY, body.getYRot(), body.getXRot()));
    }

    public static void capture(ServerPlayer player, Input input) {
        Controls old = CONTROLS.get(player.getUUID());
        if (old == null) return;
        CONTROLS.put(player.getUUID(), new Controls(input, old.yaw(), old.pitch()));
    }

    public static void captureLook(ServerPlayer player, float yaw, float pitch) {
        Controls old = CONTROLS.get(player.getUUID());
        if (old == null) return;
        CONTROLS.put(player.getUUID(), new Controls(old.input(), yaw, pitch));
    }

    public static void clear(UUID playerId) {
        CONTROLS.remove(playerId);
    }

    /**
     * Called in the entity's normal server tick. The native Mob decision phase
     * is suppressed for this body; the authenticated player's seven digital
     * controls become the body's ordinary LivingEntity movement input.
     */
    public static void tickControlledMob(Mob body) {
        ServerPlayer player = PlayerPossessionState.playerForNpcBody(body);
        if (player == null) {
            body.tick();
            return;
        }

        Controls controls = CONTROLS.getOrDefault(player.getUUID(),
            new Controls(Input.EMPTY, body.getYRot(), body.getXRot()));
        Input input = controls.input();

        body.setYRot(controls.yaw());
        body.setXRot(controls.pitch());
        body.setZza(axis(input.forward(), input.backward()));
        body.setXxa(axis(input.left(), input.right()));
        body.setShiftKeyDown(input.shift());
        body.setSprinting(input.sprint());

        if (input.jump()) body.getJumpControl().jump();
        body.getJumpControl().tick();

        // Run the physical Mob's full concrete tick chain. Its subclass/body
        // lifecycle is preserved, while MobServerAiStepMixin suppresses only
        // the native decision scheduler because player input owns this body.
        body.tick();
    }

    private static float axis(boolean positive, boolean negative) {
        return (positive ? 1.0f : 0.0f) - (negative ? 1.0f : 0.0f);
    }

    /** Pure view of the most recent controls for tests and diagnostics. */
    public static Vec3 movementIntent(ServerPlayer player) {
        Controls controls = CONTROLS.get(player.getUUID());
        if (controls == null) return Vec3.ZERO;
        Input input = controls.input();
        return new Vec3(axis(input.left(), input.right()), 0.0, axis(input.forward(), input.backward()));
    }
}
