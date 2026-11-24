package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Validated state accumulator for a persistent native-worker mirror.
 *
 * A delta is committed atomically only after its identity and sequence have
 * been checked. Consumers can read a stable immutable snapshot to reconcile
 * a worker ServerLevel on its owning server thread. This class intentionally
 * does not mutate Minecraft worlds or execute donor AI.
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
            entities = Map.copyOf(entities);
            blocks = Map.copyOf(blocks);
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
        if (delta.fromTick() != current.tick() || delta.toTick() != current.tick() + 1)
            throw new IllegalArgumentException("Mirror delta is stale, skipped or out of order");

        Map<UUID, NativeMindSenseFrame.EntitySense> entities =
            new HashMap<>(current.entities());
        for (UUID removed : delta.removedEntities()) entities.remove(removed);
        for (var entity : delta.addedEntities()) {
            if (entities.putIfAbsent(entity.id(), entity) != null)
                throw new IllegalArgumentException("Added entity already exists in mirror");
        }
        for (var entity : delta.changedEntities()) {
            if (!entities.containsKey(entity.id()))
                throw new IllegalArgumentException("Changed entity is absent from mirror");
            entities.put(entity.id(), entity);
        }

        Map<NativeMindMirrorDelta.BlockPosition, NativeMindSenseFrame.BlockSense> blocks =
            new HashMap<>(current.blocks());
        for (var removed : delta.removedBlocks()) blocks.remove(removed);
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
