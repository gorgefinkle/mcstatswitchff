package dev.freakyfriday.mind;

import java.util.Objects;
import java.util.UUID;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Small read-only body baseline captured BEFORE invoking any modded host
 * factory or setPos. It detects only observable donor/recipient changes;
 * neither a successful comparison nor an unregistered host proves that
 * third-party constructors did not mutate blocks, other mobs, or global state.
 */
public record ProbeBodySnapshot(
    UUID id, EntityType<?> type, Level level, Vec3 position,
    float health, float maxHealth, int goalCount, int targetGoalCount
) {
    public static ProbeBodySnapshot take(Mob mob) {
        Objects.requireNonNull(mob, "mob");
        return new ProbeBodySnapshot(
            mob.getUUID(), mob.getType(), mob.level(), mob.position(),
            mob.getHealth(), mob.getMaxHealth(),
            mob.goalSelector.getAvailableGoals().size(),
            mob.targetSelector.getAvailableGoals().size());
    }

    public String changes(Mob mob) {
        ProbeBodySnapshot after = take(mob);
        StringBuilder changes = new StringBuilder();
        append(changes, "uuid", id, after.id);
        append(changes, "type", type, after.type);
        if (level != after.level) append(changes, "level", level, after.level);
        append(changes, "position", position, after.position);
        if (Float.compare(health, after.health) != 0)
            append(changes, "health", health, after.health);
        if (Float.compare(maxHealth, after.maxHealth) != 0)
            append(changes, "maxHealth", maxHealth, after.maxHealth);
        if (goalCount != after.goalCount)
            append(changes, "goalCount", goalCount, after.goalCount);
        if (targetGoalCount != after.targetGoalCount)
            append(changes, "targetGoalCount", targetGoalCount, after.targetGoalCount);
        return changes.toString();
    }

    private static void append(StringBuilder output, String field, Object before, Object after) {
        if (Objects.equals(before, after)) return;
        if (!output.isEmpty()) output.append("; ");
        output.append(field).append('=').append(before).append(" -> ").append(after);
    }

    public boolean unchanged(Mob mob) { return changes(mob).isEmpty(); }
}
