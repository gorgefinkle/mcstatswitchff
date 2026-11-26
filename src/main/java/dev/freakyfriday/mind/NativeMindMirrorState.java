package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Validated state accumulator for a persistent native-worker mirror.
 *
 * Deltas are checked and applied to private copies before a new immutable
 * snapshot is published. This class intentionally does not mutate Minecraft
 * worlds or execute donor AI.
 */
public final class NativeMindMirrorState {
    public record Snapshot(UUID bodyId, UUID mindId, String dimension, long tick,
            NativeMindSenseFrame.BodySense body,
            Map<UUID, NativeMindSenseFrame.EntitySense> entities,
            Map<NativeMindMirrorDelta.BlockPosition, NativeMindSenseFrame.BlockSense> blocks) {
        public Snapshot {
            Objects.requireNonNull(bodyId);
            Objects.requireNonNull(mindId);
            Objects.requireNonNull(dimension);
            Objects.requireNonNull(body);
            Objects.requireNonNull(entities,"entities");
            Objects.requireNonNull(blocks,"blocks");
            if(tick<0) throw new IllegalArgumentException("Negative mirror snapshot tick");
            if(!dimension.matches("[a-z0-9_.-]+:[a-z0-9_./-]+")
                    || dimension.length()>NativeMindSenseFrame.MAX_IDENTIFIER_CHARS)
                throw new IllegalArgumentException("Invalid mirror snapshot dimension");
            Map<UUID,NativeMindSenseFrame.EntitySense> entityCopy=Map.copyOf(entities);
            for(var entry:entityCopy.entrySet()) {
                if(!entry.getKey().equals(entry.getValue().id())
                        || entry.getKey().equals(bodyId))
                    throw new IllegalArgumentException("Mirror entity map key/identity mismatch");
            }
            Map<NativeMindMirrorDelta.BlockPosition,NativeMindSenseFrame.BlockSense> blockCopy=
                Map.copyOf(blocks);
            for(var entry:blockCopy.entrySet()) {
                var block=entry.getValue();
                if(entry.getKey().dx()!=block.dx()
                        || entry.getKey().dy()!=block.dy()
                        || entry.getKey().dz()!=block.dz())
                    throw new IllegalArgumentException("Mirror block map key/position mismatch");
            }
            entities=entityCopy;
            blocks=blockCopy;
        }
    }

    private Snapshot current;

    public synchronized boolean initialized() { return current != null; }
    public synchronized Snapshot snapshot() { return current; }

    /** First delta must be a complete seed; every later delta must be adjacent. */
    public synchronized Snapshot apply(NativeMindMirrorDelta delta) {
        Objects.requireNonNull(delta);
        if (current == null) {
            if (delta.fromTick() != -1)
                throw new IllegalStateException("First mirror update must be a full seed");
            Map<UUID, NativeMindSenseFrame.EntitySense> entities = new HashMap<>();
            for (var entity : delta.addedEntities()) {
                if (entities.put(entity.id(), entity) != null)
                    throw new IllegalArgumentException("Duplicate entity in mirror seed");
            }
            Map<NativeMindMirrorDelta.BlockPosition, NativeMindSenseFrame.BlockSense> blocks =
                new HashMap<>();
            for (var block : delta.changedBlocks()) {
                if (blocks.put(position(block), block) != null)
                    throw new IllegalArgumentException("Duplicate block in mirror seed");
            }
            current = new Snapshot(delta.bodyId(), delta.mindId(),
                delta.dimension(), delta.toTick(), delta.body(), entities, blocks);
            return current;
        }

        if (!current.bodyId().equals(delta.bodyId())
                || !current.mindId().equals(delta.mindId()))
            throw new IllegalArgumentException("Mirror identity changed");
        if (!current.dimension().equals(delta.dimension()))
            throw new IllegalArgumentException("Dimension changed without mirror reset");
        if (current.tick() == Long.MAX_VALUE
                || delta.fromTick() != current.tick()
                || delta.toTick() != current.tick() + 1)
            throw new IllegalArgumentException("Mirror delta is stale, skipped or out of order");

        Map<UUID, NativeMindSenseFrame.EntitySense> entities =
            new HashMap<>(current.entities());
        for (UUID removed : delta.removedEntities()) {
            if (entities.remove(removed) == null)
                throw new IllegalArgumentException("Removed entity is absent from mirror: " + removed);
        }
        for (var entity : delta.addedEntities()) {
            if (entities.putIfAbsent(entity.id(), entity) != null)
                throw new IllegalArgumentException("Added entity already exists in mirror: " + entity.id());
        }
        for (var entity : delta.changedEntities()) {
            if (!entities.containsKey(entity.id()))
                throw new IllegalArgumentException("Changed entity is absent from mirror: " + entity.id());
            entities.put(entity.id(), entity);
        }

        Map<NativeMindMirrorDelta.BlockPosition, NativeMindSenseFrame.BlockSense> blocks =
            new HashMap<>(current.blocks());
        for (var removed : delta.removedBlocks()) {
            if (blocks.remove(removed) == null)
                throw new IllegalArgumentException("Removed block is absent from mirror: " + removed);
        }
        for (var block : delta.changedBlocks()) blocks.put(position(block), block);

        // Publish only after every validation and mutation succeeds.
        current = new Snapshot(delta.bodyId(), delta.mindId(), delta.dimension(),
            delta.toTick(), delta.body(), entities, blocks);
        return current;
    }

    public synchronized void reset() { current = null; }

    private static NativeMindMirrorDelta.BlockPosition position(
            NativeMindSenseFrame.BlockSense block) {
        return new NativeMindMirrorDelta.BlockPosition(block.dx(), block.dy(), block.dz());
    }
}
