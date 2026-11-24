package dev.freakyfriday.mind;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Bounded, immutable sensory snapshot for a native mind hosted away from its
 * physical body. Coordinates are relative to the recipient body, never the
 * donor's old position. This is the input contract for a continuous mirrored
 * world; it is data, not an AI decision or success flag.
 */
public record NativeMindSenseFrame(
        UUID bodyId, UUID mindId, long tick, String dimension,
        BodySense body, List<EntitySense> entities, List<BlockSense> blocks) {
    public static final int MAX_ENTITIES = 128;
    public static final int MAX_BLOCKS = 4096;
    public static final int MAX_IDENTIFIER_CHARS = 256;
    public static final double MAX_ENTITY_RANGE = 32.0;
    public static final int MAX_BLOCK_OFFSET = 16;

    public NativeMindSenseFrame {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(mindId, "mindId");
        if (tick < 0) throw new IllegalArgumentException("Negative observation tick");
        identifier(dimension, "dimension");
        Objects.requireNonNull(body, "body");
        entities = List.copyOf(Objects.requireNonNull(entities, "entities"));
        blocks = List.copyOf(Objects.requireNonNull(blocks, "blocks"));
        if (entities.size() > MAX_ENTITIES) throw new IllegalArgumentException("Entity observation limit exceeded");
        if (blocks.size() > MAX_BLOCKS) throw new IllegalArgumentException("Block observation limit exceeded");
        Set<UUID> seenEntities = new HashSet<>();
        for (EntitySense entity : entities) {
            Objects.requireNonNull(entity, "entity sense");
            if (entity.id().equals(bodyId)) throw new IllegalArgumentException("Body must not appear in nearby entities");
            if (!seenEntities.add(entity.id())) throw new IllegalArgumentException("Duplicate nearby entity identity");
        }
        Set<String> seenBlocks = new HashSet<>();
        for (BlockSense block : blocks) {
            Objects.requireNonNull(block, "block sense");
            if (!seenBlocks.add(block.dx()+":"+block.dy()+":"+block.dz()))
                throw new IllegalArgumentException("Duplicate relative block position");
        }
    }

    /** Body origin is always local (0,0,0); absolute world coordinates never cross the worker boundary. */
    public record BodySense(String entityType, double x, double y, double z,
            double velocityX, double velocityY, double velocityZ,
            float yaw, float pitch, float health, float maxHealth,
            boolean alive, boolean onGround, boolean inWater, boolean inLava) {
        public BodySense {
            identifier(entityType, "body entity type");
            finite(x,y,z,velocityX,velocityY,velocityZ,yaw,pitch,health,maxHealth);
            if (x != 0.0 || y != 0.0 || z != 0.0)
                throw new IllegalArgumentException("Body position must use the local origin (0,0,0)");
            if (maxHealth < 0 || health < 0 || health > maxHealth + 0.001f)
                throw new IllegalArgumentException("Invalid body health");
        }
    }

    /** Nearby live-world entity identity is preserved for target/action mapping. */
    public record EntitySense(UUID id, String entityType,
            double dx, double dy, double dz, double velocityX, double velocityY,
            double velocityZ, float health, float maxHealth,
            boolean alive, boolean visible, boolean onGround) {
        public EntitySense {
            Objects.requireNonNull(id, "entity id");
            identifier(entityType, "entity type");
            finite(dx,dy,dz,velocityX,velocityY,velocityZ,health,maxHealth);
            if (dx*dx + dy*dy + dz*dz > MAX_ENTITY_RANGE*MAX_ENTITY_RANGE)
                throw new IllegalArgumentException("Nearby entity exceeds observation radius");
            if (maxHealth < 0 || health < 0 || health > maxHealth + 0.001f)
                throw new IllegalArgumentException("Invalid nearby entity health");
        }
    }

    /** Sparse recipient-world block sample relative to the occupied body. */
    public record BlockSense(int dx, int dy, int dz, String blockType,
            String fluidType, boolean sturdyTop, boolean collisionEmpty) {
        public BlockSense {
            if (Math.abs(dx)>MAX_BLOCK_OFFSET || Math.abs(dy)>MAX_BLOCK_OFFSET || Math.abs(dz)>MAX_BLOCK_OFFSET)
                throw new IllegalArgumentException("Block sample outside bounded neighborhood");
            identifier(blockType, "block type");
            identifier(fluidType, "fluid type");
        }
    }

    private static void identifier(String value, String label) {
        Objects.requireNonNull(value, label);
        if (value.length()>MAX_IDENTIFIER_CHARS || !value.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid "+label+": "+value);
    }

    private static void finite(double... values) {
        for (double value : values) if (!Double.isFinite(value))
            throw new IllegalArgumentException("Non-finite sensory value");
    }
}
