package dev.freakyfriday.mind;

import java.util.Objects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.server.level.ServerPlayer;

/**
 * Selects a physical action adapter from the real body's runtime class.
 * This is a body-type dispatch only: it never examines the incoming mind,
 * donor species, or source/recipient pair.
 */
public final class MinecraftBodyPortFactory {
    private MinecraftBodyPortFactory() {}

    public static BodyActionGate.BodyPort create(LivingEntity body) {
        Objects.requireNonNull(body, "physical body");
        if (body instanceof ServerPlayer player) {
            return new MinecraftPlayerBodyPort(player);
        }
        if (body instanceof Mob mob) {
            return new MinecraftBodyPort(mob);
        }
        throw new IllegalArgumentException(
            "No native physical action adapter is registered for "
                + body.getClass().getName());
    }
}
