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
    public static final int MAX_BLOCK_PROPERTIES = 32;
    public static final int MAX_PROPERTY_NAME_CHARS = 64;
    public static final int MAX_PROPERTY_VALUE_CHARS = 128;
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
            float yaw, float pitch, double width, double height,
            float health, float maxHealth,
            boolean alive, boolean onGround, boolean inWater, boolean inLava) {
        public BodySense {
            identifier(entityType, "body entity type");
            finite(x,y,z,velocityX,velocityY,velocityZ,yaw,pitch,width,height,health,maxHealth);
            dimensions(width,height);
            if (x != 0.0 || y != 0.0 || z != 0.0)
                throw new IllegalArgumentException("Body position must use the local origin (0,0,0)");
            if (maxHealth < 0 || health < 0 || health > maxHealth + 0.001f)
                throw new IllegalArgumentException("Invalid body health");
        }

        /** Compatibility constructor for pre-geometry body observations. */
        public BodySense(String entityType, double x, double y, double z,
                double velocityX, double velocityY, double velocityZ,
                float yaw, float pitch, float health, float maxHealth,
                boolean alive, boolean onGround, boolean inWater, boolean inLava) {
            this(entityType,x,y,z,velocityX,velocityY,velocityZ,yaw,pitch,
                0.0,0.0,health,maxHealth,alive,onGround,inWater,inLava);
        }
    }

    /** Nearby live-world entity identity is preserved for target/action mapping. */
    public record EntitySense(UUID id, String entityType,
            double dx, double dy, double dz, double velocityX, double velocityY,
            double velocityZ, float yaw, float pitch, double width, double height,
            float health, float maxHealth,
            boolean alive, boolean visible, boolean onGround) {
        public EntitySense {
            Objects.requireNonNull(id, "entity id");
            identifier(entityType, "entity type");
            finite(dx,dy,dz,velocityX,velocityY,velocityZ,yaw,pitch,width,height,health,maxHealth);
            dimensions(width,height);
            if (dx*dx + dy*dy + dz*dz > MAX_ENTITY_RANGE*MAX_ENTITY_RANGE)
                throw new IllegalArgumentException("Nearby entity exceeds observation radius");
            if (maxHealth < 0 || health < 0 || health > maxHealth + 0.001f)
                throw new IllegalArgumentException("Invalid nearby entity health");
        }

        /** Compatibility constructor for pre-geometry nearby-entity observations. */
        public EntitySense(UUID id, String entityType,
                double dx, double dy, double dz, double velocityX, double velocityY,
                double velocityZ, float health, float maxHealth,
                boolean alive, boolean visible, boolean onGround) {
            this(id,entityType,dx,dy,dz,velocityX,velocityY,velocityZ,
                0.0f,0.0f,0.0,0.0,health,maxHealth,alive,visible,onGround);
        }
    }

    /** Sparse recipient-world block sample relative to the occupied body. */
    public record BlockSense(int dx, int dy, int dz, String blockType,
            String fluidType, boolean sturdyTop, boolean collisionEmpty,
            boolean known, java.util.Map<String, String> properties) {
        public BlockSense {
            if (Math.abs(dx)>MAX_BLOCK_OFFSET || Math.abs(dy)>MAX_BLOCK_OFFSET || Math.abs(dz)>MAX_BLOCK_OFFSET)
                throw new IllegalArgumentException("Block sample outside bounded neighborhood");
            identifier(blockType, "block type");
            identifier(fluidType, "fluid type");
            Objects.requireNonNull(properties, "block properties");
            if (!known && !properties.isEmpty())
                throw new IllegalArgumentException("Unknown block samples cannot carry properties");
            if (properties.size() > MAX_BLOCK_PROPERTIES)
                throw new IllegalArgumentException("Block property limit exceeded");
            java.util.Map<String, String> copy = new java.util.TreeMap<>();
            for (var entry : properties.entrySet()) {
                String name = Objects.requireNonNull(entry.getKey(), "block property name");
                String value = Objects.requireNonNull(entry.getValue(), "block property value");
                if (name.length() > MAX_PROPERTY_NAME_CHARS
                        || !name.matches("[a-z_]+"))
                    throw new IllegalArgumentException("Invalid block property name: " + name);
                if (value.isEmpty() || value.length() > MAX_PROPERTY_VALUE_CHARS
                        || !value.matches("[a-z0-9_.-]+"))
                    throw new IllegalArgumentException("Invalid block property value: " + value);
                if (copy.put(name, value) != null)
                    throw new IllegalArgumentException("Duplicate block property: " + name);
            }
            properties = java.util.Map.copyOf(copy);
        }

        /** Compatibility constructor for blocks with properties and known state. */
        public BlockSense(int dx, int dy, int dz, String blockType,
                String fluidType, boolean sturdyTop, boolean collisionEmpty,
                java.util.Map<String,String> properties) {
            this(dx,dy,dz,blockType,fluidType,sturdyTop,collisionEmpty,true,properties);
        }

        /** Compatibility constructor for blocks with no serialized state properties. */
        public BlockSense(int dx, int dy, int dz, String blockType,
                String fluidType, boolean sturdyTop, boolean collisionEmpty) {
            this(dx, dy, dz, blockType, fluidType, sturdyTop, collisionEmpty,
                true, java.util.Map.of());
        }
    }

    private static void dimensions(double width,double height) {
        if (width < 0.0 || height < 0.0 || width > 16.0 || height > 16.0)
            throw new IllegalArgumentException("Invalid physical body dimensions");
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
