package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
     * is suppressed for this body; the authenticated player's digital controls
     * drive the body's regular LivingEntity travel/collision pipeline.
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
        body.setShiftKeyDown(input.shift());
        body.setSprinting(input.sprint());

        // 26.2 Mob.serverAiStep normally lets MoveControl set this speed.
        // That AI phase is intentionally suppressed while a player owns the
        // body, so reproduce only its generic movement-speed contribution.
        // Read after sprint is applied so the native sprint attribute modifier
        // is included. Fall back safely for unusual modded Mob attribute sets.
        var movementSpeedAttribute = body.getAttribute(Attributes.MOVEMENT_SPEED);
        float movementSpeed = movementSpeedAttribute == null
            ? body.getSpeed()
            : (float) body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (!Float.isFinite(movementSpeed) || movementSpeed <= 0.0f) {
            movementSpeed = 0.1f;
        }

        // Mob.setSpeed(speed) also writes forward input (zza = speed) in 26.2.
        // Set speed first, then overwrite both axes with player intent.
        body.setSpeed(movementSpeed);
        float forward = axis(input.forward(), input.backward());
        float strafe = axis(input.left(), input.right());
        float magnitude = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (magnitude > 1.0f) {
            forward /= magnitude;
            strafe /= magnitude;
        }
        body.setZza(forward);
        body.setXxa(strafe);

        if (input.jump()) body.getJumpControl().jump();
        body.getJumpControl().tick();

        // This Mob is also the native controller host whose concrete tick
        // already runs on the Player body. Invoke only its LivingEntity
        // physical tick here so its Mob/subclass tick state is not advanced a
        // second time. Mob.aiStep still runs; MobServerAiStepMixin suppresses
        // its native scheduler while preserving physical body effects/travel.
        ((LivingEntityTickInvoker) (Object) body).freakyfriday$invokeLivingEntityTick();
    }

    private static float axis(boolean positive, boolean negative) {
        return (positive ? 1.0f : 0.0f) - (negative ? 1.0f : 0.0f);
    }

    /** Normalized, world-independent digital intent for tests and diagnostics. */
    public static Vec3 movementIntent(ServerPlayer player) {
        Controls controls = CONTROLS.get(player.getUUID());
        if (controls == null) return Vec3.ZERO;
        Input input = controls.input();
        float forward = axis(input.forward(), input.backward());
        float strafe = axis(input.left(), input.right());
        float magnitude = (float) Math.sqrt(forward * forward + strafe * strafe);
        if (magnitude > 1.0f) {
            forward /= magnitude;
            strafe /= magnitude;
        }
        return new Vec3(strafe, 0.0, forward);
    }
}
