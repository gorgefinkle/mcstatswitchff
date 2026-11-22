package dev.freakyfriday;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import dev.freakyfriday.mind.PossessionMotion;
import java.util.ArrayDeque;
import java.util.Deque;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;
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
    private static float originalYaw,originalPitch,lookYaw,lookPitch;
    private static float lastAppliedLookYaw,lastAppliedLookPitch;
    private static boolean initializedLook;
    private static long attackSequence;
    private static long lastAcknowledged=-1;
    private static final int MAX_REPLAY=50;
    private record Frame(long sequence,float forward,float strafe,boolean jump,
                         float yaw,float pitch) {}
    private static final Deque<Frame> pending=new ArrayDeque<>();
    private static volatile PossessionStatePayload snapshot;

    private static void resetPrediction() {
        sequence=0;
        attackSequence=0;
        ticks=0;
        lastAcknowledged=-1;
        initializedLook=false;
        pending.clear();
        snapshot=null;
    }

    /** Client-only, server-selected body ID for occupied-body HUD and GUI. */
    public static int occupiedEntityId() { return bodyId; }

    @SubscribeEvent
    public static void registerPayload(RegisterClientPayloadHandlersEvent event) {
        event.register(PossessionViewPayload.TYPE,(data,context)-> {
            int newBody=data.active()?data.entityId():-1;
            // Player/body exchange refreshes the body's own authoritative
            // anchor repeatedly. Do not reset input sequence on refresh.
            if (newBody!=bodyId) resetPrediction();
            bodyId=newBody;
            if(newBody>=0) ClientSelectionHud.clear();
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
    public static void receiveCorrection(RegisterClientPayloadHandlersEvent event) {
        event.register(PossessionStatePayload.TYPE,(message,context)-> {
            if (message.entityId()==bodyId) snapshot=message;
        });
    }

    @SubscribeEvent
    public static void suppressLocalBodyMovement(MovementInputUpdateEvent event) {
        Minecraft mc=Minecraft.getInstance();
        if (bodyId>=0 && event.getEntity()==mc.player) {
            // Inputs are sent to the server-owned OCCUPIED body instead of
            // locally moving the account's original body.
            event.getInput().keyPresses=net.minecraft.world.entity.player.Input.EMPTY;
        }
    }

    /**
     * Cancel ORIGINAL player's vanilla attack/use/pick packets during
     * possession. An authenticated intent lets the SERVER choose the
     * occupied body as attacker and validate the target/line of sight.
     * This prevents the vanilla invalid-self-attack disconnect.
     */
    @SubscribeEvent
    public static void onAttack(InputEvent.InteractionKeyMappingTriggered event) {
        if(bodyId<0) return;
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null||mc.level==null||mc.gui.screen()!=null) return;
        event.setCanceled(true);
        event.setSwingHand(false);
        if(!event.isAttack()) return;
        Entity body=mc.level.getEntity(bodyId);
        if(body==null||body.isRemoved()) return;
        if(mc.hitResult instanceof EntityHitResult hit)
            ClientPacketDistributor.sendToServer(
                new PossessionAttackPayload(hit.getEntity().getId(),++attackSequence));
    }

    @SubscribeEvent
    public static void tick(ClientTickEvent.Post event) {
        Minecraft mc=Minecraft.getInstance();
        if(mc.player==null || mc.level==null || mc.player.connection==null) {
            bodyId=-1;
            resetPrediction();
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
                resetPrediction();
            }
            return;
        }
        if(mc.getCameraEntity()!=body)mc.setCameraEntity(body);
        // Vanilla mouse input rotates the authenticated LocalPlayer, not
        // the server-selected remote entity. P05 mistakenly sent the remote
        // entity's last network yaw back to the server, making the camera
        // appear locked while the original player's head still turned.
        // Project the LOCAL mouse look into the occupied body first, then
        // send the same angles to the authoritative server. Do not use the
        // remote body's stale yaw as the source of new mouse input.
        if(mc.gui.screen()!=null) return; // no gameplay input while typing/in menus
        if (!initializedLook) {
            originalYaw=mc.player.getYRot();
            originalPitch=mc.player.getXRot();
            lookYaw=body.getYRot();
            lookPitch=body.getXRot();
            lastAppliedLookYaw=lookYaw;
            lastAppliedLookPitch=lookPitch;
            initializedLook=true;
        }
        float playerDeltaYaw=Mth.wrapDegrees(mc.player.getYRot()-originalYaw);
        float playerDeltaPitch=mc.player.getXRot()-originalPitch;
        float cameraDeltaYaw=Mth.wrapDegrees(body.getYRot()-lastAppliedLookYaw);
        float cameraDeltaPitch=body.getXRot()-lastAppliedLookPitch;
        if(Math.abs(playerDeltaYaw)+Math.abs(playerDeltaPitch)>0.001f){
            lookYaw+=playerDeltaYaw;
            lookPitch+=playerDeltaPitch;
        } else if(Math.abs(cameraDeltaYaw)<15.0f&&Math.abs(cameraDeltaPitch)<15.0f){
            lookYaw+=cameraDeltaYaw;
            lookPitch+=cameraDeltaPitch;
        }
        lookPitch=Mth.clamp(lookPitch,-90.0f,90.0f);
        mc.player.setYRot(originalYaw);
        mc.player.setXRot(originalPitch);
        mc.player.setYHeadRot(originalYaw);
        body.setYRot(lookYaw);
        body.setXRot(lookPitch);
        body.yRotO=lookYaw;
        body.xRotO=lookPitch;
        lastAppliedLookYaw=lookYaw;
        lastAppliedLookPitch=lookPitch;
        float forward=(mc.options.keyUp.isDown()?1:0)-(mc.options.keyDown.isDown()?1:0);
        float strafe=(mc.options.keyLeft.isDown()?1:0)-(mc.options.keyRight.isDown()?1:0);
        boolean jump=mc.options.keyJump.isDown();
        // Server-acknowledged state + local replay avoids waiting for normal
        // remote-Mob interpolation before the camera follows WASD input.
        PossessionStatePayload correction=snapshot;
        snapshot=null;
        if(correction!=null&&correction.entityId()==bodyId
                &&correction.acknowledgedSequence()>=lastAcknowledged){
            lastAcknowledged=correction.acknowledgedSequence();
            body.setPos(correction.x(),correction.y(),correction.z());
            body.setDeltaMovement(new net.minecraft.world.phys.Vec3(
                0.0,correction.velocityY(),0.0));
            while(!pending.isEmpty()&&pending.peekFirst().sequence()<=lastAcknowledged)
                pending.removeFirst();
            if(body instanceof LivingEntity living){
                for(Frame frame:pending)
                    PossessionMotion.step(living,frame.forward(),frame.strafe(),
                        frame.jump(),frame.yaw(),frame.pitch());
            }
        }
        long nextSequence=++sequence;
        pending.addLast(new Frame(nextSequence,forward,strafe,jump,lookYaw,lookPitch));
        while(pending.size()>MAX_REPLAY)pending.removeFirst();
        ClientPacketDistributor.sendToServer(new PossessionInputPayload(
            forward,strafe,jump,lookYaw,lookPitch,nextSequence));
        if(body instanceof LivingEntity living){
            PossessionMotion.step(living,forward,strafe,jump,lookYaw,lookPitch);
            body.yRotO=lookYaw;
            body.xRotO=lookPitch;
            lastAppliedLookYaw=lookYaw;
            lastAppliedLookPitch=lookPitch;
        }
    }
}
