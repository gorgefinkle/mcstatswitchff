package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.HitResult;

/**
 * Captures real recipient-world senses for a remote/native mind.
 *
 * This deliberately reads the occupied body's current surroundings rather
 * than the donor's old world/coordinates. It is a capture seam only: a
 * continuous worker transport and complete side-effect interception still
 * have to consume it before this can be called a working mind swap.
 */
public final class MinecraftMindSenseCapture {
    private MinecraftMindSenseCapture() {}

    private static final int HORIZONTAL_BLOCK_RADIUS = 8;
    private static final int VERTICAL_BLOCK_RADIUS = 4;

    public static NativeMindSenseFrame capture(
            LivingEntity body, UUID mindId, long serverTick) {
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(mindId, "mindId");
        if (!(body.level() instanceof ServerLevel level))
            throw new IllegalArgumentException("Mind senses require a server-side body");

        var velocity = body.getDeltaMovement();
        String bodyType = BuiltInRegistries.ENTITY_TYPE.getKey(body.getType()).toString();
        var self = new NativeMindSenseFrame.BodySense(
            // The worker receives a body-local frame, never absolute live-world
            // coordinates. Its mirror origin is always (0,0,0).
            bodyType, 0.0, 0.0, 0.0,
            velocity.x, velocity.y, velocity.z,
            body.getYRot(), body.getXRot(),
            body.getHealth(), body.getMaxHealth(),
            body.isAlive(), body.onGround(), body.isInWater(), body.isInLava());

        List<Entity> nearby = new ArrayList<>(level.getEntities(
            body, body.getBoundingBox().inflate(
                NativeMindSenseFrame.MAX_ENTITY_RANGE), Entity::isAlive));
        nearby.sort(Comparator.comparingDouble(body::distanceToSqr));
        List<NativeMindSenseFrame.EntitySense> entities = new ArrayList<>();
        for (Entity entity : nearby) {
            if (entities.size() == NativeMindSenseFrame.MAX_ENTITIES) break;
            var motion = entity.getDeltaMovement();
            float health = entity instanceof LivingEntity living ? living.getHealth() : 0;
            float maxHealth = entity instanceof LivingEntity living ? living.getMaxHealth() : 0;
            // Use the actual recipient world's block ray for every LivingEntity.
            // A ServerPlayer body has no Mob.Sensing object; marking every target
            // invisible there would starve a mob mind after a reciprocal swap.
            boolean visible = level.clip(new ClipContext(
                body.getEyePosition(), entity.getEyePosition(),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, body))
                .getType() == HitResult.Type.MISS;
            entities.add(new NativeMindSenseFrame.EntitySense(
                entity.getUUID(),
                BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString(),
                entity.getX()-body.getX(), entity.getY()-body.getY(), entity.getZ()-body.getZ(),
                motion.x, motion.y, motion.z, health, maxHealth,
                entity.isAlive(), visible, entity.onGround()));
        }

        BlockPos origin = body.blockPosition();
        List<NativeMindSenseFrame.BlockSense> blocks = new ArrayList<>();
        for (int dx=-HORIZONTAL_BLOCK_RADIUS; dx<=HORIZONTAL_BLOCK_RADIUS; dx++) {
            for (int dy=-VERTICAL_BLOCK_RADIUS; dy<=VERTICAL_BLOCK_RADIUS; dy++) {
                for (int dz=-HORIZONTAL_BLOCK_RADIUS; dz<=HORIZONTAL_BLOCK_RADIUS; dz++) {
                    BlockPos pos = origin.offset(dx,dy,dz);
                    // Never force-load a neighboring chunk just to observe it.
                    // Missing samples remain unknown to the remote mind.
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    String blockType = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    String fluidType = BuiltInRegistries.FLUID.getKey(
                        state.getFluidState().getType()).toString();
                    blocks.add(new NativeMindSenseFrame.BlockSense(
                        dx,dy,dz,blockType,fluidType,
                        state.isFaceSturdy(level,pos,Direction.UP),
                        state.getCollisionShape(level,pos).isEmpty()));
                }
            }
        }
        return new NativeMindSenseFrame(
            body.getUUID(), mindId, serverTick,
            level.dimension().identifier().toString(), self, entities, blocks);
    }
}
