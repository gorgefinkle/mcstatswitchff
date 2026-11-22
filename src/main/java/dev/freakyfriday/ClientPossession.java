package dev.freakyfriday;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * Player camera and WASD/jump input follow the actual server-owned Mob.
 * No client can select another Mob's ID; only server camera payload sets it.
 */
@EventBusSubscriber(modid=FreakyFriday.MODID,value=Dist.CLIENT)
public final class ClientPossession {
    private ClientPossession(){}
    private static int bodyId=-1;
    private static double anchorX,anchorY,anchorZ;
    private static long sequence;
    private static int ticks;

    @SubscribeEvent
    public static void registerPayload(RegisterClientPayloadHandlersEvent event) {
        event.register(PossessionViewPayload.TYPE,(data,context)-> {
            bodyId=data.active()?data.entityId():-1;
            sequence=0;
            ticks=0;
            if(data.active()){
                anchorX=data.anchorX();
                anchorY=data.anchorY();
                anchorZ=data.anchorZ();
            }
            if(bodyId<0){
                Minecraft mc=Minecraft.getInstance();
                if(mc.player!=null)mc.setCameraEntity(mc.player);
            }
        });
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.player.connection==null) {
            bodyId=-1;
            sequence=0;
            return;
        }
        if(bodyId<0) return;
        // Keep the local authenticated player at the same original-body
        // location the server protects, rather than letting keyboard input
        // carry the hidden LocalPlayer away from its body while the camera
        // follows the possessed mob.
        mc.player.setPos(anchorX,anchorY,anchorZ);
        mc.player.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
        Entity body=mc.level.getEntity(bodyId);
        if(body==null || body.isRemoved()) {
            // Body may not yet be tracked on the first network tick.
            if(++ticks>80) {
                mc.setCameraEntity(mc.player);
                bodyId=-1;
            }
            return;
        }
        if(mc.getCameraEntity()!=body)mc.setCameraEntity(body);
        // The mouse still rotates the local authenticated player. Apply that
        // view to the Mob body in the client while server accepts bounded yaw.
        body.setYRot(mc.player.getYRot());
        body.setXRot(mc.player.getXRot());
        if(mc.gui.screen()!=null) return; // no gameplay input while typing/in menus
        float forward=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0);
        float strafe=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);
        ClientPacketDistributor.sendToServer(new PossessionInputPayload(
            forward,strafe,mc.options.keyJump.isDown(),
            mc.player.getYRot(),mc.player.getXRot(),++sequence));
    }
}
