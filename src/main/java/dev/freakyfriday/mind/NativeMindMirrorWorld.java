package dev.freakyfriday.mind;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

/**
 * Applies a bounded recipient sensory snapshot to a disposable worker
 * ServerLevel. This mirrors nearby Mob targets and known block states only;
 * it does not reproduce POIs, chunk biomes, inventories, capabilities or
 * arbitrary modded world queries, and it never authorizes public swapping.
 *
 * All methods must run on the owning Minecraft server thread. Created target
 * mobs have NoAI set so they provide sensory targets without competing with
 * the donor mind being simulated in the worker arena.
 */
public final class NativeMindMirrorWorld implements AutoCloseable {
    public record Report(int addedEntities,int updatedEntities,int removedEntities,
                         int changedBlocks,int restoredBlocks) {}

    private final Map<UUID,Entity> entities=new HashMap<>();
    private final Map<UUID,Integer> fakePlayerGenerations=new HashMap<>();
    private final Map<BlockPos,BlockState> originalBlocks=new HashMap<>();
    private final Set<BlockPos> observedBlocks=new HashSet<>();
    private final Map<BlockPos,BlockState> appliedBlocks=new HashMap<>();
    private UUID bodyId;
    private UUID mindId;
    private String dimension;
    private long lastTick=-1;
    private ServerLevel ownerLevel;

    /** Return a specifically managed mirrored entity without relying on level-wide UUID lookup timing. */
    public Entity managedEntity(UUID originalId) {
        Objects.requireNonNull(originalId,"original entity UUID");
        Entity entity=entities.get(originalId);
        if(entity==null || entity.isRemoved() || !originalId.equals(entity.getUUID()))
            return null;
        return entity;
    }

    public Report reconcile(ServerLevel level, Entity anchor,
            NativeMindMirrorState.Snapshot snapshot) {
        try {
            return reconcileInternal(level,anchor,snapshot);
        } catch(RuntimeException failure) {
            // Never leave a half-applied recipient snapshot in the worker.
            // Roll back owned entities and blocks before propagating failure.
            if(ownerLevel==null || ownerLevel==level) {
                try { close(level); }
                catch(RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            }
            throw failure;
        }
    }

    private Report reconcileInternal(ServerLevel level, Entity anchor,
            NativeMindMirrorState.Snapshot snapshot) {
        Objects.requireNonNull(level,"worker level");
        Objects.requireNonNull(anchor,"mirror anchor");
        Objects.requireNonNull(snapshot,"mirror snapshot");
        requireServerThread(level);
        if(anchor.level()!=level || anchor.isRemoved())
            throw new IllegalArgumentException("Mirror anchor must be a live entity in the worker level");
        // The worker's disposable simulation dimension is deliberately
        // independent of the recipient's source dimension. The source
        // dimension remains part of the mirror identity/delta contract.
        if(ownerLevel!=null && ownerLevel!=level)
            throw new IllegalStateException("A mirror instance cannot span ServerLevels");
        if(bodyId==null) {
            bodyId=snapshot.bodyId();
            mindId=snapshot.mindId();
            dimension=snapshot.dimension();
            ownerLevel=level;
        } else {
            if(!bodyId.equals(snapshot.bodyId()) || !mindId.equals(snapshot.mindId())
                    || !dimension.equals(snapshot.dimension()))
                throw new IllegalArgumentException("Mirror identity or dimension changed without reset");
            if(lastTick==Long.MAX_VALUE || snapshot.tick()!=lastTick+1)
                throw new IllegalArgumentException("Worker world mirror requires exact adjacent ticks");
        }
        if(!snapshot.body().alive())
            throw new IllegalStateException("Cannot mirror a dead recipient body");

        int added=0,updated=0,removed=0,changed=0,restored=0;
        Set<UUID> desiredEntities=new HashSet<>();
        for(var sense:snapshot.entities().values()) {
            UUID id=sense.id();
            if(id.equals(snapshot.bodyId()) || id.equals(anchor.getUUID()))
                throw new IllegalArgumentException("Recipient body/worker anchor cannot be a nearby target");
            desiredEntities.add(id);
            Entity entity=entities.get(id);
            if(entity==null) {
                if(level.getEntity(id)!=null)
                    throw new IllegalStateException("Worker world already contains unowned mirror UUID "+id);
                entity=createMirrorEntity(level,sense);
                entity.setUUID(id);
                position(entity,anchor,sense);
                entity.setDeltaMovement(new Vec3(sense.velocityX(),sense.velocityY(),sense.velocityZ()));
                entity.setYRot(sense.yaw());
                entity.setXRot(sense.pitch());
                if(entity instanceof LivingEntity living) living.setYHeadRot(sense.yaw());
                setHealth(entity,sense.health());
                if(entity instanceof Mob mob) mob.setNoAi(true);
                if(entity instanceof ServerPlayer player) {
                    level.addNewPlayer(player);
                } else if(!level.addFreshEntity(entity)) {
                    throw new IllegalStateException("Could not register mirrored recipient entity "+id);
                }
                entities.put(id,entity);
                added++;
            } else {
                if(entity.isRemoved() || !entity.getUUID().equals(id)
                        || !BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString()
                            .equals(sense.entityType()))
                    throw new IllegalStateException("Previously mirrored entity identity/type changed: "+id);
                position(entity,anchor,sense);
                entity.setDeltaMovement(new Vec3(sense.velocityX(),sense.velocityY(),sense.velocityZ()));
                entity.setYRot(sense.yaw());
                entity.setXRot(sense.pitch());
                if(entity instanceof LivingEntity living) {
                    living.setYHeadRot(sense.yaw());
                    setHealth(entity,sense.health());
                }
                updated++;
            }
        }
        for(UUID id:Set.copyOf(entities.keySet())) {
            if(!desiredEntities.contains(id)) {
                Entity stale=entities.remove(id);
                if(stale instanceof ServerPlayer player)
                    level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
                else if(stale!=null) stale.discard();
                if(stale!=null) removed++;
            }
        }

        Set<BlockPos> currentObserved=new HashSet<>();
        for(var block:snapshot.blocks().values()) {
            BlockPos pos=anchor.blockPosition().offset(block.dx(),block.dy(),block.dz());
            currentObserved.add(pos);
            if(!block.known()) continue;
            if(pos.getY()<level.getMinY() || pos.getY()>=level.getMaxY())
                throw new IllegalArgumentException("Known mirrored block is outside worker build height: "+pos);
            if(!level.hasChunkAt(pos))
                throw new IllegalStateException("Refusing to force-load mirrored recipient terrain: "+pos);
            BlockState state=resolveBlock(block);
            String fluid=BuiltInRegistries.FLUID.getKey(state.getFluidState().getType()).toString();
            if(!fluid.equals(block.fluidType()))
                throw new IllegalArgumentException("Mirrored block fluid-state mismatch at "+pos);
            if(originalBlocks.putIfAbsent(pos,level.getBlockState(pos))==null) {
                // The original value is retained until this coordinate leaves the observed window.
            }
            BlockState previous=appliedBlocks.get(pos);
            if(previous==null || !previous.equals(state)) {
                // The worker arena often already contains the same air/stone.
                // Preserve the ownership ledger without firing needless
                // neighbor-update hooks for an unchanged physical state.
                if(!level.getBlockState(pos).equals(state)) {
                    level.setBlockAndUpdate(pos,state);
                    changed++;
                }
                appliedBlocks.put(pos,state);
            }
        }

        // State IDs/properties alone do not guarantee the worker's actual
        // collision topology matches the recipient when shape depends on
        // adjacent blocks. Verify the two collision facts captured by the
        // sensory contract only after the full local terrain is applied.
        for(var block:snapshot.blocks().values()) {
            if(!block.known()) continue;
            BlockPos pos=anchor.blockPosition().offset(block.dx(),block.dy(),block.dz());
            BlockState actual=level.getBlockState(pos);
            boolean sturdy=actual.isFaceSturdy(level,pos,net.minecraft.core.Direction.UP);
            boolean empty=actual.getCollisionShape(level,pos).isEmpty();
            if(sturdy!=block.sturdyTop() || empty!=block.collisionEmpty())
                throw new IllegalStateException("Mirrored terrain collision facts differ at "+pos
                    +" sturdyTop="+sturdy+"/"+block.sturdyTop()
                    +" collisionEmpty="+empty+"/"+block.collisionEmpty());
        }

        // Restore coordinates that left the bounded neighborhood. Unknown
        // samples stay observed and retain their last known worker-world state.
        for(BlockPos pos:Set.copyOf(originalBlocks.keySet())) {
            if(!currentObserved.contains(pos)) {
                if(level.hasChunkAt(pos)) {
                    BlockState original=originalBlocks.remove(pos);
                    appliedBlocks.remove(pos);
                    level.setBlockAndUpdate(pos,original);
                    restored++;
                }
            }
        }
        observedBlocks.clear();
        observedBlocks.addAll(currentObserved);
        lastTick=snapshot.tick();
        return new Report(added,updated,removed,changed,restored);
    }

    private Entity createMirrorEntity(ServerLevel level,
            NativeMindSenseFrame.EntitySense sense) {
        if("minecraft:player".equals(sense.entityType())) {
            int generation=fakePlayerGenerations.merge(sense.id(),1,Integer::sum);
            String compact=sense.id().toString().replace("-","");
            String profileName="FFM"+compact.substring(0,7)
                +Integer.toUnsignedString(generation,36);
            if(profileName.length()>16) profileName=profileName.substring(0,16);
            var player=FakePlayerFactory.get(level,new GameProfile(sense.id(),profileName));
            if(!player.getUUID().equals(sense.id()))
                throw new IllegalStateException("Fake player factory did not preserve recipient player UUID");
            if(Math.abs(player.getBbWidth()-sense.width())>0.01
                    || Math.abs(player.getBbHeight()-sense.height())>0.01)
                throw new IllegalStateException("Recipient player geometry cannot be represented by worker FakePlayer");
            if(Math.abs(player.getMaxHealth()-sense.maxHealth())>0.05)
                throw new IllegalStateException("Recipient player health attributes differ in worker FakePlayer");
            return player;
        }
        Identifier id=Identifier.tryParse(sense.entityType());
        if(id==null || !BuiltInRegistries.ENTITY_TYPE.containsKey(id))
            throw new IllegalStateException("Worker does not have recipient entity type "+sense.entityType());
        EntityType<?> type=BuiltInRegistries.ENTITY_TYPE.getValue(id);
        Entity entity=type.create(level,EntitySpawnReason.COMMAND);
        if(!(entity instanceof LivingEntity living))
            throw new IllegalStateException("Recipient sensory target is not a mirrorable LivingEntity: "
                +sense.entityType());
        if(Math.abs(entity.getBbWidth()-sense.width())>0.01
                || Math.abs(entity.getBbHeight()-sense.height())>0.01)
            throw new IllegalStateException("Recipient target geometry cannot be represented by worker type "
                +sense.entityType());
        if(Math.abs(living.getMaxHealth()-sense.maxHealth())>0.05)
            throw new IllegalStateException("Recipient target health attributes differ in worker type "
                +sense.entityType());
        return living;
    }

    private static void position(Entity entity,Entity anchor,
            NativeMindSenseFrame.EntitySense sense) {
        entity.setPos(anchor.getX()+sense.dx(),anchor.getY()+sense.dy(),anchor.getZ()+sense.dz());
    }

    private static void setHealth(Entity entity,float health) {
        if(entity instanceof LivingEntity living) {
            if(health>living.getMaxHealth()+0.001f)
                throw new IllegalStateException("Mirrored health exceeds worker entity max health");
            living.setHealth(health);
        }
    }

    private static BlockState resolveBlock(NativeMindSenseFrame.BlockSense block) {
        Identifier id=Identifier.tryParse(block.blockType());
        if(id==null || !BuiltInRegistries.BLOCK.containsKey(id))
            throw new IllegalStateException("Worker does not have recipient block "+block.blockType());
        BlockState state=BuiltInRegistries.BLOCK.getValue(id).defaultBlockState();
        for(var entry:block.properties().entrySet()) {
            Property<?> property=state.getBlock().getStateDefinition().getProperty(entry.getKey());
            if(property==null)
                throw new IllegalArgumentException("Block property is unavailable: "+entry.getKey());
            state=withProperty(state,property,entry.getValue());
        }
        if(state.getProperties().size()!=block.properties().size())
            throw new IllegalArgumentException("Recipient block state is incomplete for "+block.blockType());
        return state;
    }

    private static <T extends Comparable<T>> BlockState withProperty(
            BlockState state,Property<T> property,String value) {
        var parsed=property.getValue(value);
        if(parsed.isEmpty())
            throw new IllegalArgumentException("Invalid property value "+property.getName()+"="+value);
        return state.setValue(property,parsed.get());
    }

    private static void requireServerThread(ServerLevel level) {
        if(!level.getServer().isSameThread())
            throw new IllegalStateException("Mirror reconciliation must run on the Minecraft server thread");
    }

    public void close(ServerLevel level) {
        Objects.requireNonNull(level);
        requireServerThread(level);
        if(ownerLevel!=null && ownerLevel!=level)
            throw new IllegalArgumentException("Cannot close mirror from a different ServerLevel");
        for(Entity entity:entities.values()) if(!entity.isRemoved()) {
            if(entity instanceof ServerPlayer player)
                level.removePlayerImmediately(player,Entity.RemovalReason.DISCARDED);
            else entity.discard();
        }
        entities.clear();
        fakePlayerGenerations.clear();
        for(var entry:originalBlocks.entrySet()) {
            if(!level.hasChunkAt(entry.getKey()))
                throw new IllegalStateException("Cannot safely restore an unloaded mirror block without force-loading: "+entry.getKey());
            level.setBlockAndUpdate(entry.getKey(),entry.getValue());
        }
        originalBlocks.clear();
        appliedBlocks.clear();
        observedBlocks.clear();
        bodyId=null; mindId=null; dimension=null; lastTick=-1; ownerLevel=null;
    }

    @Override public void close() {
        if(ownerLevel!=null) close(ownerLevel);
    }
}
