package dev.freakyfriday.mind;

import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Real-server coverage for the recipient-world sensory boundary and codec. */
public final class NativeMindSenseGameTests {
    private NativeMindSenseGameTests() {}

    public static final DeferredRegister<Consumer<GameTestHelper>> FUNCTIONS =
        DeferredRegister.create(BuiltInRegistries.TEST_FUNCTION,"freakyfriday");

    public static final DeferredHolder<Consumer<GameTestHelper>,Consumer<GameTestHelper>>
        RECIPIENT_SNAPSHOT = FUNCTIONS.register(
            "recipient_sensory_snapshot",()->NativeMindSenseGameTests::verifyCapture);

    private static void verifyCapture(GameTestHelper helper) {
        var level=helper.getLevel();
        Mob body=EntityTypes.CHICKEN.create(level,EntitySpawnReason.COMMAND);
        Mob target=EntityTypes.ZOMBIE.create(level,EntitySpawnReason.COMMAND);
        helper.assertTrue(body!=null && target!=null,
            "Recipient sensory fixture requires real Chicken and Zombie entities");

        BlockPos origin=helper.absolutePos(new BlockPos(5,2,5));
        body.setPos(origin.getX()+0.5,origin.getY(),origin.getZ()+0.5);
        target.setPos(origin.getX()+3.5,origin.getY(),origin.getZ()+0.5);
        body.setYRot(37.0f);
        body.setXRot(11.0f);
        target.setYRot(163.0f);
        target.setXRot(-4.0f);
        BlockPos logPos=body.blockPosition().offset(1,0,0);
        level.setBlockAndUpdate(logPos,Blocks.OAK_LOG.defaultBlockState()
            .setValue(RotatedPillarBlock.AXIS,Direction.Axis.X));
        helper.assertTrue(level.addFreshEntity(body) && level.addFreshEntity(target),
            "Recipient sensory fixture entities failed to register");

        UUID mind=UUID.randomUUID();
        NativeMindSenseFrame frame=MinecraftMindSenseCapture.capture(
            body,mind,level.getGameTime());
        helper.assertTrue(frame.bodyId().equals(body.getUUID())
                && frame.mindId().equals(mind),
            "Recipient sensory frame lost physical body/mind identities");
        helper.assertTrue(frame.body().width()==body.getBbWidth()
                && frame.body().height()==body.getBbHeight()
                && frame.body().yaw()==body.getYRot()
                && frame.body().pitch()==body.getXRot(),
            "Recipient body orientation or physical geometry was not captured");
        helper.assertTrue(frame.blocks().size()==17*9*17,
            "Bounded block sampling must retain explicit coverage for loaded and unloaded positions");
        var nearby=frame.entities().stream()
            .filter(e->e.id().equals(target.getUUID())).findFirst().orElse(null);
        helper.assertTrue(nearby!=null
                && nearby.yaw()==target.getYRot()
                && nearby.pitch()==target.getXRot()
                && nearby.width()==target.getBbWidth()
                && nearby.height()==target.getBbHeight(),
            "Nearby entity orientation/geometry was not captured");
        var log=frame.blocks().stream()
            .filter(b->b.dx()==1 && b.dy()==0 && b.dz()==0).findFirst().orElse(null);
        helper.assertTrue(log!=null && log.known()
                && log.blockType().equals("minecraft:oak_log")
                && "x".equals(log.properties().get("axis")),
            "Recipient terrain snapshot lost the exact rotated log state");

        byte[] key=new byte[NativeMindSenseWireCodec.KEY_BYTES];
        java.util.Arrays.fill(key,(byte)0x31);
        byte[] packet=NativeMindSenseWireCodec.encode(frame,key);
        NativeMindSenseFrame decoded=NativeMindSenseWireCodec.decode(
            packet,key,body.getUUID(),mind,frame.tick());
        helper.assertTrue(decoded.equals(frame),
            "Authenticated sensory codec did not preserve the complete recipient snapshot");
        helper.assertTrue(!MinecraftMindSenseCapture.withinRange(23,23,0)
                && !MinecraftMindSenseCapture.withinRange(Double.NaN,0,0),
            "The radial sensory entity range must reject diagonal overflow and non-finite values");
        target.discard();
        level.setBlockAndUpdate(logPos,Blocks.AIR.defaultBlockState());
        NativeMindMirrorState mirrorState=new NativeMindMirrorState();
        mirrorState.apply(NativeMindMirrorDelta.between(null,decoded));
        NativeMindMirrorWorld mirror=new NativeMindMirrorWorld();
        var report=mirror.reconcile(level,body,mirrorState.snapshot());
        Entity mirroredTarget=level.getEntity(target.getUUID());
        helper.assertTrue(mirroredTarget instanceof Mob
                && mirroredTarget.getType().equals(target.getType())
                && mirroredTarget.getYRot()==target.getYRot()
                && Math.abs(mirroredTarget.getBbWidth()-target.getBbWidth())<0.001
                && Math.abs(mirroredTarget.getBbHeight()-target.getBbHeight())<0.001,
            "Recipient nearby mob identity, type, orientation and geometry were not mirrored");
        helper.assertTrue(mirroredTarget instanceof Mob mirroredMob && mirroredMob.isNoAi(),
            "Mirrored target mob must not run competing AI");
        helper.assertTrue(level.getBlockState(logPos).getBlock()==Blocks.OAK_LOG
                && level.getBlockState(logPos).getValue(RotatedPillarBlock.AXIS)==Direction.Axis.X,
            "Mirror failed to materialize exact recipient block-state properties");
        helper.assertTrue(report.addedEntities()==1 && report.changedBlocks()>0,
            "Mirror reconciliation report omitted entity/block materialization");
        mirror.close(level);
        helper.assertTrue(level.getEntity(target.getUUID())==null && level.getBlockState(logPos).isAir(),
            "Mirror shutdown failed to remove its entities and restore original blocks");
        System.out.println("FF-SENSE recipient_snapshot PASS blocks="+frame.blocks().size()
            +" nearbyEntities="+frame.entities().size()
            +" blockStateProperties=true physicalGeometry=true explicitUnknownCoverage=true "
            +" authenticatedRoundTrip=true mirrorWorldReconciled=true cleanup=true");
        body.discard();
        helper.succeed();
    }
}
