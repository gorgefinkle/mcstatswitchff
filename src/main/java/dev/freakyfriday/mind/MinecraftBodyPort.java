package dev.freakyfriday.mind;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

/**
 * The body side of a real Minecraft entity. Native entity identity, type,
 * attributes, geometry, loot and vanilla physical interactions stay untouched.
 *
 * This adapter provides the first small set of physical action dispatchers.
 * The AI runtime must translate mind decisions into these intents; this class
 * does NOT itself transplant goals, brains or player inputs.
 *
 * More physical abilities can be registered through further body-action ports.
 * These rules describe abilities of the recipient BODY, never allowed pairs
 * of donor/recipient mob types.
 */
public final class MinecraftBodyPort implements BodyActionGate.BodyPort {
    public static final String MOVE_TO = "freakyfriday:move_to";
    public static final String LOOK_AT = "freakyfriday:look_at";
    public static final String JUMP = "freakyfriday:jump";
    public static final String DETONATE = "freakyfriday:detonate";

    private final LivingEntity body;

    public MinecraftBodyPort(LivingEntity body) {
        this.body = Objects.requireNonNull(body, "body");
    }

    @Override
    public UUID id() {
        return body.getUUID();
    }

    @Override
    public boolean supports(String actionId) {
        // Only Mob has these native controls, and only a creeper's physical
        // body has the actual creeper fuse. Every other species may still
        // have a mind that WANTS to explode; the request is unavailable.
        return switch (actionId) {
            case MOVE_TO, LOOK_AT, JUMP -> body instanceof Mob;
            case DETONATE -> body instanceof Creeper;
            default -> false;
        };
    }

    @Override
    public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
        Objects.requireNonNull(intent, "intent");
        if (!supports(intent.actionId()))
            return new BodyActionGate.Result(BodyActionGate.Status.UNAVAILABLE,
                "Body cannot execute " + intent.actionId());
        try {
            switch (intent.actionId()) {
                case MOVE_TO -> {
                    Mob mob = (Mob) body;
                    double[] target = target(intent.arguments());
                    double speed = parse(intent.arguments(), "speed", 1.0);
                    // Navigation speed is an instruction multiplier, not a
                    // copied donor movement attribute. Actual physical speed
                    // remains the receiving mob's own native attribute.
                    if (speed <= 0 || speed > 2.0)
                        return BodyActionGate.Result.blocked("Invalid movement speed multiplier");
                    if (!mob.getNavigation().moveTo(target[0], target[1], target[2], speed))
                        return BodyActionGate.Result.blocked("Body has no navigable path");
                    return BodyActionGate.Result.executed();
                }
                case LOOK_AT -> {
                    Mob mob = (Mob) body;
                    double[] target = target(intent.arguments());
                    mob.getLookControl().setLookAt(target[0], target[1], target[2]);
                    return BodyActionGate.Result.executed();
                }
                case JUMP -> {
                    ((Mob) body).getJumpControl().jump();
                    return BodyActionGate.Result.executed();
                }
                case DETONATE -> {
                    // This is the creeper BODY's own fuse mechanism. A cow
                    // with a creeper mind is NOT granted this mechanism.
                    ((Creeper) body).ignite();
                    return BodyActionGate.Result.executed();
                }
                default -> {
                    return new BodyActionGate.Result(BodyActionGate.Status.UNAVAILABLE,
                        "Unknown physical body action");
                }
            }
        } catch (IllegalArgumentException invalid) {
            return BodyActionGate.Result.blocked("Invalid body action parameters: " + invalid.getMessage());
        }
    }

    private static double[] target(Map<String, String> params) {
        return new double[]{
            parse(params, "x", Double.NaN),
            parse(params, "y", Double.NaN),
            parse(params, "z", Double.NaN)
        };
    }

    private static double parse(Map<String, String> params, String key, double fallback) {
        double value;
        try {
            String raw = params.get(key);
            value = raw == null ? fallback : Double.parseDouble(raw);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Not a number: " + key, failure);
        }
        if (!Double.isFinite(value))
            throw new IllegalArgumentException("Nonfinite coordinate or speed: " + key);
        return value;
    }
}
