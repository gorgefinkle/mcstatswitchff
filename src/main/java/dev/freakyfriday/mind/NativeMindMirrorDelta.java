package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Deterministic, body-local difference between consecutive sensory snapshots.
 *
 * This is the incremental input contract for a persistent worker-world mirror.
 * It contains data only: applying it to a Minecraft ServerLevel is deliberately
 * a separate server-thread operation. A missing entity/block is explicit so a
 * mirror cannot accidentally retain stale targets or stale terrain forever.
 */
public record NativeMindMirrorDelta(
        UUID bodyId, UUID mindId, long fromTick, long toTick,
        NativeMindSenseFrame.BodySense body,
        List<NativeMindSenseFrame.EntitySense> addedEntities,
        List<NativeMindSenseFrame.EntitySense> changedEntities,
        List<UUID> removedEntities,
        List<NativeMindSenseFrame.BlockSense> changedBlocks,
        List<BlockPosition> removedBlocks) {

    public record BlockPosition(int dx, int dy, int dz) {}

    public NativeMindMirrorDelta {
        Objects.requireNonNull(bodyId);
        Objects.requireNonNull(mindId);
        Objects.requireNonNull(body);
        addedEntities = List.copyOf(addedEntities);
        changedEntities = List.copyOf(changedEntities);
        removedEntities = List.copyOf(removedEntities);
        changedBlocks = List.copyOf(changedBlocks);
        removedBlocks = List.copyOf(removedBlocks);
        if (fromTick < -1 || toTick < 0 || toTick <= fromTick)
            throw new IllegalArgumentException("Mirror delta ticks must advance");
    }

    /** First snapshot is a full mirror seed; subsequent snapshots must be adjacent. */
    public static NativeMindMirrorDelta between(
            NativeMindSenseFrame previous, NativeMindSenseFrame current) {
        Objects.requireNonNull(current);
        if (previous == null) {
            return new NativeMindMirrorDelta(current.bodyId(), current.mindId(),
                -1, current.tick(), current.body(), current.entities(), List.of(),
                List.of(), current.blocks(), List.of());
        }
        if (!previous.bodyId().equals(current.bodyId())
                || !previous.mindId().equals(current.mindId()))
            throw new IllegalArgumentException("Cannot diff different body/mind identities");
        if (!previous.dimension().equals(current.dimension()))
            throw new IllegalArgumentException("Dimension change requires mirror reset");
        if (current.tick() != previous.tick() + 1)
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
        for (var block : previous.blocks())
            oldBlocks.put(position(block), block);
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
        return new NativeMindMirrorDelta(current.bodyId(), current.mindId(),
            previous.tick(), current.tick(), current.body(), added, changed,
            removed, changedBlocks, removedBlocks);
    }

    private static BlockPosition position(NativeMindSenseFrame.BlockSense block) {
        return new BlockPosition(block.dx(), block.dy(), block.dz());
    }
}
