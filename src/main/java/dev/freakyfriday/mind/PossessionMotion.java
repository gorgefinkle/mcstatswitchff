package dev.freakyfriday.mind;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * One collision-resolved input step.
 *
 * A controlled Mob retains its actual entity class and is moved through that
 * class's native travel implementation. No species-based swim/fly/fall table
 * is maintained here. NoAI is temporarily lifted only during the physical
 * travel call so Mob.travel can apply the body's native medium/locomotion
 * behavior; goal/Brain decision ticking remains paused for player possession.
 *
 * Player bodies keep the account-exchange adapter's bounded movement path;
 * they are not disguised as Mobs and have no Mob navigation or JumpControl.
 */
public final class PossessionMotion {
    private PossessionMotion() {}

    /**
     * Apply a donor's directional intent through the RECIPIENT Mob's own
     * native travel implementation. The donor's displacement magnitude is
     * deliberately not copied: the physical body supplies its own speed,
     * collision, fluid, gravity, climbing and species-specific travel rules.
     */
    public static void applyNativeIntent(Mob body,Vec3 donorDisplacement,
                                         float yaw,float pitch) {
        body.setYRot(yaw);
        body.setYHeadRot(yaw);
        body.setXRot(pitch);

        double angle=Math.toRadians(yaw);
        double dx=donorDisplacement.x;
        double dz=donorDisplacement.z;
        double forward=-Math.sin(angle)*dx+Math.cos(angle)*dz;
        double strafe=Math.cos(angle)*dx+Math.sin(angle)*dz;
        double horizontal=Math.hypot(forward,strafe);
        if(horizontal>1.0e-5) {
            forward/=horizontal;
            strafe/=horizontal;
        } else {
            forward=0.0;
            strafe=0.0;
        }

        boolean mediumVertical=body.isInWater()||body.isInLava()||body.isNoGravity();
        double vertical=mediumVertical
            ?Math.max(-1.0,Math.min(1.0,donorDisplacement.y)):0.0;
        boolean jump=body.onGround()&&donorDisplacement.y>0.12;
        // Do not call travel for an idle mind: the real entity tick already
        // applied its gravity/drag this frame. A second zero-input travel
        // would double-apply vertical physics to the physical recipient.
        if(Math.abs(strafe)<1.0e-5&&Math.abs(forward)<1.0e-5
                &&Math.abs(vertical)<1.0e-5&&!jump)return;
        double nativeSpeed=body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        body.setSpeed((float)Math.max(0.0,Math.min(1.5,nativeSpeed)));
        boolean originallyNoAi=body.isNoAi();
        if(originallyNoAi)body.setNoAi(false);
        try {
            if(jump)body.getJumpControl().jump();
            body.travel(new Vec3(strafe,vertical,forward));
        } finally {
            if(originallyNoAi)body.setNoAi(true);
        }
    }

    public static double step(LivingEntity body,float rawForward,float rawStrafe,
                              boolean jump,float yaw,float pitch) {
        double forward=rawForward,strafe=rawStrafe;
        double magnitude=Math.sqrt(forward*forward+strafe*strafe);
        if(magnitude>1.0) {
            forward/=magnitude;
            strafe/=magnitude;
        }

        body.setYRot(yaw);
        body.setXRot(pitch);
        body.setYHeadRot(yaw);

        if(body instanceof Mob mob) {
            // Mob.travel consumes Mob#getSpeed(), not the attribute directly.
            // Native MoveControl normally refreshes this value each AI tick;
            // player-controlled movement bypasses MoveControl, so refresh it
            // from the physical body's own movement attribute here.
            double nativeSpeed=mob.getAttributeValue(Attributes.MOVEMENT_SPEED);
            mob.setSpeed((float)Math.max(0.0,Math.min(1.5,nativeSpeed)));
            if(Boolean.getBoolean("freakyfriday.debugMotion"))
                System.out.println("[FF-MOTION] type="+mob.getType()+" attr="+nativeSpeed
                    +" speed="+mob.getSpeed()+" forward="+forward+" strafe="+strafe);
            boolean originallyNoAi=mob.isNoAi();
            if(originallyNoAi) mob.setNoAi(false);
            try {
                if(jump) {
                    mob.getJumpControl().jump();
                    mob.getJumpControl().tick();
                }
                // Vec3 components are local strafe / vertical / forward
                // input. Mob.travel selects the correct native behavior for
                // land, water, lava, climbing, flying and modded bodies.
                mob.travel(new Vec3(strafe,jump?1.0:0.0,forward));
            } finally {
                if(originallyNoAi) mob.setNoAi(true);
            }
            return body.getDeltaMovement().y;
        }

        // Player↔Player control is intentionally not represented as a Mob.
        // Keep its server-side body adapter bounded and collision-resolved.
        double nativeSpeed=body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double travel=Double.isFinite(nativeSpeed)
            ? Math.max(0,Math.min(0.45,nativeSpeed*0.76)):0;
        double angle=Math.toRadians(yaw);
        double dx=(-Math.sin(angle)*forward+Math.cos(angle)*strafe)*travel;
        double dz=(Math.cos(angle)*forward+Math.sin(angle)*strafe)*travel;
        double previousY=body.getDeltaMovement().y;
        double dy=jump&&body.onGround()?0.42
            :(body.onGround()||body.isNoGravity())?0.0:Math.max(-0.58,previousY-0.08);
        body.move(MoverType.SELF,new Vec3(dx,dy,dz));
        double nextY=body.onGround()?0.0:dy*0.98;
        body.setDeltaMovement(new Vec3(0,nextY,0));
        return nextY;
    }
}
