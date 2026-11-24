package dev.freakyfriday.mind;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Native physical-action adapter for an ORIGINAL ServerPlayer body.
 *
 * This is deliberately separate from MinecraftBodyPort: a player is not a
 * Mob, has no Mob navigation or GoalSelector, and must never be disguised as
 * one. Actions execute against the real connected ServerPlayer and retain
 * player-owned attributes, equipment, attack cooldowns, collision and world
 * authority. The adapter does not itself supply a mind or schedule a worker.
 */
public final class MinecraftPlayerBodyPort implements BodyActionGate.BodyPort {
    private final ServerPlayer body;

    public MinecraftPlayerBodyPort(ServerPlayer body) {
        this.body = Objects.requireNonNull(body, "player body");
    }

    @Override
    public UUID id() {
        return body.getUUID();
    }

    @Override
    public boolean supports(String actionId) {
        return BodyActionGate.MOVE_OFFSET.equals(actionId)
            || BodyActionGate.LOOK_OFFSET.equals(actionId)
            || BodyActionGate.ATTACK_ENTITY.equals(actionId);
    }

    @Override
    public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (body.isRemoved() || !body.isAlive()
                || !(body.level() instanceof ServerLevel level)) {
            return BodyActionGate.Result.blocked("Player body is not alive in a server level");
        }
        try {
            return switch (intent.actionId()) {
                case BodyActionGate.MOVE_OFFSET -> move(intent);
                case BodyActionGate.LOOK_OFFSET -> look(intent);
                case BodyActionGate.ATTACK_ENTITY -> attack(level, intent);
                default -> new BodyActionGate.Result(
                    BodyActionGate.Status.UNAVAILABLE,
                    "Player body does not provide " + intent.actionId());
            };
        } catch (IllegalArgumentException invalid) {
            return BodyActionGate.Result.blocked(
                "Invalid player-body action: " + invalid.getMessage());
        }
    }

    /**
     * Move one body-native step toward the mind's relative destination.
     * ServerPlayer.move performs the real player's collision resolution;
     * speed comes from the player's own movement attribute, not the donor.
     * A fresh intent is expected each server tick, just as a native controller
     * continually updates its movement command.
     */
    private BodyActionGate.Result move(BodyActionGate.Intent intent) {
        double dx = number(intent, "dx");
        double dz = number(intent, "dz");
        double multiplier = optionalNumber(intent, "speed", 1.0);
        if (multiplier <= 0.0 || multiplier > 2.0
                || dx * dx + dz * dz > 144.0) {
            return BodyActionGate.Result.blocked("Movement request outside player-body bounds");
        }
        double distance = Math.hypot(dx, dz);
        if (distance < 0.05) return BodyActionGate.Result.executed();

        double nativeSpeed = body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        if (!Double.isFinite(nativeSpeed) || nativeSpeed <= 0.0) {
            return BodyActionGate.Result.blocked("Player body has no usable movement speed");
        }
        double step = Math.min(distance, nativeSpeed * 20.0 * multiplier);
        Vec3 displacement = new Vec3(dx / distance * step, 0.0, dz / distance * step);
        body.move(MoverType.SELF, displacement);
        return BodyActionGate.Result.executed();
    }

    /** Aim the actual player's view; no camera/entity replacement is involved. */
    private BodyActionGate.Result look(BodyActionGate.Intent intent) {
        double dx = number(intent, "dx");
        double dy = number(intent, "dy");
        double dz = number(intent, "dz");
        if (dx * dx + dy * dy + dz * dz < 1.0e-8
                || dx * dx + dy * dy + dz * dz > 144.0) {
            return BodyActionGate.Result.blocked("Look vector outside player-body bounds");
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, horizontal));
        body.setYRot(yaw);
        body.setYHeadRot(yaw);
        body.setXRot(Math.max(-90.0f, Math.min(90.0f, pitch)));
        return BodyActionGate.Result.executed();
    }

    /** Use the real player's attack pipeline, including held item and cooldown. */
    private BodyActionGate.Result attack(ServerLevel level, BodyActionGate.Intent intent) {
        String raw = intent.arguments().get("targetId");
        if (raw == null) return BodyActionGate.Result.blocked("Missing mapped attack target");
        final UUID targetId;
        try {
            targetId = UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return BodyActionGate.Result.blocked("Invalid mapped attack target UUID");
        }
        Entity target = level.getEntity(targetId);
        if (target == null || target == body || !target.isAlive()) {
            return BodyActionGate.Result.blocked("Mapped attack target is not alive in this world");
        }
        if (body.distanceToSqr(target) > 16.0) {
            return BodyActionGate.Result.blocked("Mapped target is outside player melee reach");
        }
        float healthBefore=target instanceof net.minecraft.world.entity.LivingEntity living
            ? living.getHealth() : Float.NaN;
        float attackStrength=body.getAttackStrengthScale(0.5f);
        double attackDamage=body.getAttributeValue(
            net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        boolean targetInvulnerable=target.isInvulnerableTo(
            body.damageSources().playerAttack(body));
        body.attack(target);
        float healthAfter=target instanceof net.minecraft.world.entity.LivingEntity living
            ? living.getHealth() : Float.NaN;
        if(Boolean.getBoolean("freakyfriday.thirdPartyFixture")) {
            System.out.println("FF-PLAYER-BODY-ATTACK"
                +" body="+body.getUUID()
                +" target="+target.getUUID()
                +" attackStrength="+attackStrength
                +" attackDamage="+attackDamage
                +" targetInvulnerable="+targetInvulnerable
                +" creative="+body.isCreative()
                +" spectator="+body.isSpectator()
                +" mainHand="+body.getMainHandItem()
                +" distanceSq="+body.distanceToSqr(target)
                +" healthBefore="+healthBefore
                +" healthAfter="+healthAfter);
        }
        return BodyActionGate.Result.executed();
    }

    private static double number(BodyActionGate.Intent intent, String key) {
        String raw = intent.arguments().get(key);
        if (raw == null) throw new IllegalArgumentException("Missing " + key);
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + key);
        return value;
    }

    private static double optionalNumber(BodyActionGate.Intent intent,
                                         String key, double fallback) {
        String raw = intent.arguments().get(key);
        if (raw == null) return fallback;
        double value = Double.parseDouble(raw);
        if (!Double.isFinite(value)) throw new IllegalArgumentException("Non-finite " + key);
        return value;
    }
}
