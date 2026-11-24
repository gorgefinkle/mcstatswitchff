package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Deterministic body-local difference between consecutive sensory snapshots.
 *
 * This is a data contract for a persistent worker-world mirror. It does not
 * itself mutate a Minecraft ServerLevel or execute donor AI. A missing
 * entity/block is explicit so a reconciler cannot retain stale targets or
 * terrain indefinitely.
 */
public record NativeMindMirrorDelta(
        UUID bodyId, UUID mindId, String dimension, long fromTick, long toTick,
        NativeMindSenseFrame.BodySense body,
        List<NativeMindSenseFrame.EntitySense> addedEntities,
        List<NativeMindSenseFrame.EntitySense> changedEntities,
        List<UUID> removedEntities,
        List<NativeMindSenseFrame.BlockSense> changedBlocks,
        List<BlockPosition> removedBlocks) {

    /** Body-relative coordinates in the bounded sensory neighborhood. */
    public record BlockPosition(int dx, int dy, int dz) {
        public BlockPosition {
            if (Math.abs(dx) > NativeMindSenseFrame.MAX_BLOCK_OFFSET
                    || Math.abs(dy) > NativeMindSenseFrame.MAX_BLOCK_OFFSET
                    || Math.abs(dz) > NativeMindSenseFrame.MAX_BLOCK_OFFSET)
                throw new IllegalArgumentException("Mirror block position outside sensory bounds");
        }
    }

    public NativeMindMirrorDelta {
        Objects.requireNonNull(bodyId, "bodyId");
        Objects.requireNonNull(mindId, "mindId");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(body, "body");
        if (dimension.length() > NativeMindSenseFrame.MAX_IDENTIFIER_CHARS
                || !dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+"))
            throw new IllegalArgumentException("Invalid mirror dimension: " + dimension);
        addedEntities = List.copyOf(Objects.requireNonNull(addedEntities, "addedEntities"));
        changedEntities = List.copyOf(Objects.requireNonNull(changedEntities, "changedEntities"));
        removedEntities = List.copyOf(Objects.requireNonNull(removedEntities, "removedEntities"));
        changedBlocks = List.copyOf(Objects.requireNonNull(changedBlocks, "changedBlocks"));
        removedBlocks = List.copyOf(Objects.requireNonNull(removedBlocks, "removedBlocks"));
        if (fromTick < -1 || toTick < 0 || toTick <= fromTick)
            throw new IllegalArgumentException("Mirror delta ticks must advance");
        if (fromTick >= 0 && (fromTick == Long.MAX_VALUE || toTick != fromTick + 1))
            throw new IllegalArgumentException("Incremental mirror deltas must advance exactly one tick");

        // A seed is a complete replacement snapshot, never a partial patch.
        if (fromTick == -1 && (!changedEntities.isEmpty()
                || !removedEntities.isEmpty() || !removedBlocks.isEmpty()))
            throw new IllegalArgumentException("Initial mirror update must be a complete seed");

        Set<UUID> entityOperations = new HashSet<>();
        for (var entity : addedEntities) {
            Objects.requireNonNull(entity, "added entity");
            checkEntityIdentity(entity.id(), bodyId, entityOperations, "add");
        }
        for (var entity : changedEntities) {
            Objects.requireNonNull(entity, "changed entity");
            checkEntityIdentity(entity.id(), bodyId, entityOperations, "change");
        }
        for (UUID removed : removedEntities)
            checkEntityIdentity(Objects.requireNonNull(removed, "removed entity id"),
                bodyId, entityOperations, "remove");

        Set<BlockPosition> blockOperations = new HashSet<>();
        for (var block : changedBlocks) {
            Objects.requireNonNull(block, "changed block");
            BlockPosition position = position(block);
            if (!blockOperations.add(position))
                throw new IllegalArgumentException("Duplicate block update: " + position);
        }
        for (BlockPosition removed : removedBlocks) {
            Objects.requireNonNull(removed, "removed block position");
            if (!blockOperations.add(removed))
                throw new IllegalArgumentException("Block cannot be changed and removed in one delta: " + removed);
        }
    }

    private static void checkEntityIdentity(UUID id, UUID bodyId,
            Set<UUID> operations, String operation) {
        if (id.equals(bodyId))
            throw new IllegalArgumentException("Physical body cannot be a mirrored nearby entity");
        if (!operations.add(id))
            throw new IllegalArgumentException("Conflicting or duplicate entity " + operation + ": " + id);
    }

    /** First snapshot is a full mirror seed; subsequent snapshots must be adjacent. */
    public static NativeMindMirrorDelta between(
            NativeMindSenseFrame previous, NativeMindSenseFrame current) {
        Objects.requireNonNull(current, "current snapshot");
        if (previous == null) {
            return new NativeMindMirrorDelta(current.bodyId(), current.mindId(), current.dimension(),
                -1, current.tick(), current.body(), current.entities(), List.of(),
                List.of(), current.blocks(), List.of());
        }
        if (!previous.bodyId().equals(current.bodyId())
                || !previous.mindId().equals(current.mindId()))
            throw new IllegalArgumentException("Cannot diff different body/mind identities");
        if (!previous.dimension().equals(current.dimension()))
            throw new IllegalArgumentException("Dimension change requires mirror reset");
        if (previous.tick() == Long.MAX_VALUE || current.tick() != previous.tick() + 1)
            throw new IllegalArgumentException("Mirror snapshots must be consecutive ticks");

        Map<UUID, NativeMindSenseFrame.EntitySense> oldEntities = new HashMap<>();
        for (var entity : previous.entities()) oldEntities.put(entity.id(), entity);
        List<NativeMindSenseFrame.EntitySense> added = new ArrayList<>();
        List<NativeMindSenseFrame.EntitySense> changed = new ArrayList<>();
        for (var entity : current.entities()) {
            var old = oldEntities.remove(entity.id());
            if (old == null) added.add(entity);
            else if (!old.equals(entity)) changed.add(entity);
        }

        Map<BlockPosition, NativeMindSenseFrame.BlockSense> oldBlocks = new HashMap<>();
        for (var block : previous.blocks()) oldBlocks.put(position(block), block);
        List<NativeMindSenseFrame.BlockSense> changedBlocks = new ArrayList<>();
        for (var block : current.blocks()) {
            var old = oldBlocks.remove(position(block));
            if (!block.equals(old)) changedBlocks.add(block);
        }
        List<BlockPosition> removedBlocks = new ArrayList<>(oldBlocks.keySet());
        removedBlocks.sort((a,b) -> {
            int c=Integer.compare(a.dx(),b.dx());
            if(c==0)c=Integer.compare(a.dy(),b.dy());
            if(c==0)c=Integer.compare(a.dz(),b.dz());
            return c;
        });

        List<UUID> removed = new ArrayList<>(oldEntities.keySet());
        removed.sort(UUID::compareTo);
        return new NativeMindMirrorDelta(current.bodyId(), current.mindId(), current.dimension(),
            previous.tick(), current.tick(), current.body(), added, changed,
            removed, changedBlocks, removedBlocks);
    }

    private static BlockPosition position(NativeMindSenseFrame.BlockSense block) {
        return new BlockPosition(block.dx(), block.dy(), block.dz());
    }
}
