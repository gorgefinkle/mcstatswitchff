package dev.freakyfriday.mind;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Physical actions are registered by CAPABILITY, not by pairs of mob types.
 * The body remains the actual original LivingEntity: health, native speed,
 * interactions, loot, appearance, collision and UUID never change.
 *
 * Modded creatures may register additional actions dynamically. Registering
 * a capability is NOT sufficient to sandbox that mod's AI decision code:
 * a future isolated AI backend must still intercept side effects.
 */
public final class MinecraftBodyPort implements BodyActionGate.BodyPort {
    public static final String MOVE_TO = "freakyfriday:move_to";
    /** Worker-world-independent native movement relative to THIS body. */
    public static final String MOVE_OFFSET = BodyActionGate.MOVE_OFFSET;
    public static final String LOOK_OFFSET = BodyActionGate.LOOK_OFFSET;
    public static final String LOOK_AT = "freakyfriday:look_at";
    public static final String JUMP = "freakyfriday:jump";
    public static final String ATTACK_ENTITY = BodyActionGate.ATTACK_ENTITY;
    public static final String DETONATE = "freakyfriday:detonate";

    private static final BodyCapabilityRegistry<LivingEntity> CAPABILITIES =
        new BodyCapabilityRegistry<>();

    static {
        CAPABILITIES.register(MOVE_TO, capability(Mob.class, (body, intent) -> {
            double[] destination = target(intent.arguments());
            double speed = parse(intent.arguments(), "speed", 1.0);
            // This is a path speed multiplier; the receiving body still has
            // its own movement speed, dimensions, physics and navigation.
            if (speed <= 0 || speed > 2.0)
                return BodyActionGate.Result.blocked("Invalid movement speed multiplier");
            if (!body.getNavigation().moveTo(destination[0], destination[1], destination[2], speed))
                return BodyActionGate.Result.blocked("Recipient body found no navigable path");
            return BodyActionGate.Result.executed();
        }));
        CAPABILITIES.register(LOOK_AT, capability(Mob.class, (body, intent) -> {
            double[] destination = target(intent.arguments());
            body.getLookControl().setLookAt(destination[0], destination[1], destination[2]);
            return BodyActionGate.Result.executed();
        }));
        // Generic native-UI actions. An isolated donor AI can navigate in
        // its OWN world, then transmit a relative direction without exposing
        // disposable-worker coordinates to the live recipient world.
        // The recipient uses its actual navigation and physical attributes.
        CAPABILITIES.register(MOVE_OFFSET, capability(Mob.class, (body, intent) -> {
            double dx=parse(intent.arguments(),"dx",Double.NaN);
            double dy=parse(intent.arguments(),"dy",Double.NaN);
            double dz=parse(intent.arguments(),"dz",Double.NaN);
            double speed=parse(intent.arguments(),"speed",1.0);
            if(speed<=0 || speed>2 || dx*dx+dy*dy+dz*dz>144)
                return BodyActionGate.Result.blocked("Invalid relative movement request");
            if(!body.getNavigation().moveTo(
                    body.getX()+dx,body.getY()+dy,body.getZ()+dz,speed))
                return BodyActionGate.Result.blocked("Recipient cannot navigate to requested offset");
            return BodyActionGate.Result.executed();
        }));
        CAPABILITIES.register(LOOK_OFFSET, capability(Mob.class, (body, intent) -> {
            double dx=parse(intent.arguments(),"dx",Double.NaN);
            double dy=parse(intent.arguments(),"dy",Double.NaN);
            double dz=parse(intent.arguments(),"dz",Double.NaN);
            if(dx*dx+dy*dy+dz*dz>144)
                return BodyActionGate.Result.blocked("Invalid relative look request");
            body.getLookControl().setLookAt(
                body.getX()+dx,body.getY()+dy,body.getZ()+dz);
            return BodyActionGate.Result.executed();
        }));
        CAPABILITIES.register(JUMP, capability(Mob.class, (body, intent) -> {
            body.getJumpControl().jump();
            return BodyActionGate.Result.executed();
        }));
        // A native donor attack is an intent against a mapped world entity.
        // The ORIGINAL recipient Mob performs it, so its real reach, damage,
        // cooldowns, equipment and physical attack capabilities remain in
        // charge. No donor attack is replayed against the wrong world UUID.
        CAPABILITIES.register(ATTACK_ENTITY, capability(Mob.class, (body, intent) -> {
            String raw=intent.arguments().get("targetId");
            if(raw==null)
                return BodyActionGate.Result.blocked("Missing mapped attack target");
            final UUID targetId;
            try { targetId=UUID.fromString(raw); }
            catch(IllegalArgumentException invalid) {
                return BodyActionGate.Result.blocked("Invalid mapped attack target UUID");
            }
            if(!(body.level() instanceof ServerLevel level))
                return BodyActionGate.Result.blocked("Attack requires a live server body");
            Entity target=level.getEntity(targetId);
            if(target==null || target==body || !target.isAlive())
                return BodyActionGate.Result.blocked("Mapped attack target is not alive in this world");
            if(body.distanceToSqr(target)>16.0)
                return BodyActionGate.Result.blocked("Mapped target is outside physical melee reach");
            // Some real bodies (Chicken is the regression case) do not
            // possess ATTACK_DAMAGE at all. That is a body capability limit,
            // not a reason to reject the mind exchange or crash the session:
            // execute the native swing animation without inventing damage.
            if(body.getAttribute(Attributes.ATTACK_DAMAGE)==null) {
                body.swing(InteractionHand.MAIN_HAND,
                    net.minecraft.world.item.component.SwingAnimation.DEFAULT);
                return new BodyActionGate.Result(BodyActionGate.Status.EXECUTED,
                    "Physical body has no attack-damage attribute; native swing executed without damage");
            }
            boolean hit=body.doHurtTarget(level,target);
            return new BodyActionGate.Result(BodyActionGate.Status.EXECUTED,
                hit ? "Physical body attack accepted" :
                    "Physical body executed attack intent; damage was not confirmed");
        }));
        // This is a physical mechanism on a CREEPER BODY, not a creeper-mind
        // donor-recipient pair. Modded explosion-capable bodies can register
        // additional implementation(s) for this same action ID.
        CAPABILITIES.register(DETONATE, capability(Creeper.class, (body, intent) -> {
            body.ignite();
            return BodyActionGate.Result.executed();
        }));
    }

    private final LivingEntity body;

    public MinecraftBodyPort(LivingEntity body) {
        this.body = Objects.requireNonNull(body, "body");
    }

    /** Third-party extensions register BODY action implementations, never mob pairs. */
    public static void registerCapability(String actionId,
            BodyCapabilityRegistry.Executor<LivingEntity> executor) {
        CAPABILITIES.register(actionId, executor);
    }

    private interface TypedAction<T extends LivingEntity> {
        BodyActionGate.Result perform(T body, BodyActionGate.Intent intent);
    }

    private static <T extends LivingEntity> BodyCapabilityRegistry.Executor<LivingEntity>
            capability(Class<T> bodyClass, TypedAction<T> implementation) {
        return new BodyCapabilityRegistry.Executor<>() {
            @Override public boolean supports(LivingEntity candidate) {
                return bodyClass.isInstance(candidate);
            }
            @Override public BodyActionGate.Result perform(LivingEntity candidate,
                                                           BodyActionGate.Intent intent) {
                return implementation.perform(bodyClass.cast(candidate), intent);
            }
        };
    }

    @Override public UUID id() { return body.getUUID(); }

    @Override
    public boolean supports(String actionId) {
        return CAPABILITIES.supports(body, actionId);
    }

    @Override
    public BodyActionGate.Result perform(BodyActionGate.Intent intent) {
        try {
            return CAPABILITIES.execute(body, intent);
        } catch (IllegalArgumentException invalid) {
            return BodyActionGate.Result.blocked(
                "Invalid body action parameters: " + invalid.getMessage());
        }
    }

    private static double[] target(Map<String, String> params) {
        return new double[] {
            parse(params, "x", Double.NaN),
            parse(params, "y", Double.NaN),
            parse(params, "z", Double.NaN)
        };
    }

    private static double parse(Map<String, String> params, String key, double fallback) {
        final double number;
        try {
            String raw = params.get(key);
            number = raw == null ? fallback : Double.parseDouble(raw);
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Not a number: " + key, failure);
        }
        if (!Double.isFinite(number))
            throw new IllegalArgumentException("Nonfinite coordinate or speed: " + key);
        return number;
    }
}
