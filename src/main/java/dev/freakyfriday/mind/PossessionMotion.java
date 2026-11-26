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
    // One unit of native Mob input is calibrated to the common vanilla
    // ground-speed baseline. The actual recipient attribute scales that
    // input, so a turtle, chicken, zombie, or modded Mob retains its own
    // movement-speed profile without a species lookup table.
    private static final double MOB_INPUT_BASELINE=0.25;
    private static final float MOB_TRAVEL_BASE_SPEED=0.25F;

    private static double nativeInputScale(Mob body) {
        double speed=body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        return Double.isFinite(speed)
            ?Math.max(0.0,Math.min(4.0,speed/MOB_INPUT_BASELINE)):0.0;
    }

    private PossessionMotion() {}

    /**
     * Apply a donor's directional intent through the RECIPIENT Mob's own
     * native travel implementation. The donor's displacement magnitude is
     * deliberately not copied: the physical body supplies its own speed,
     * collision, fluid, gravity, climbing and species-specific travel rules.
     */
    public static void applyNativeIntent(Mob body,Vec3 donorDisplacement,
                                         float yaw,float pitch) {
        applyNativeIntent(body,donorDisplacement,yaw,pitch,1.0);
    }

    /**
     * Apply the worker's bounded 3D direction through the physical Mob's own
     * travel override. The multiplier scales native input, never a copied
     * donor-world displacement or a direct Entity.move translation.
     */
    public static void applyNativeIntent(Mob body,Vec3 donorDisplacement,
                                         float yaw,float pitch,double speedMultiplier) {
        if(!Double.isFinite(speedMultiplier)||speedMultiplier<=0.0||speedMultiplier>2.0)
            throw new IllegalArgumentException("Invalid native movement multiplier");
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
        double nativeScale=Math.min(4.0,nativeInputScale(body)*speedMultiplier);
        forward*=nativeScale;
        strafe*=nativeScale;

        // Pass vertical intent to the body's native travel override as
        // well. Ordinary ground travel may ignore it; fluid/flying/modded
        // travel implementations can consume it without species branching.
        double vertical=Math.max(-1.0,Math.min(1.0,donorDisplacement.y))*nativeScale;
        boolean jump=body.onGround()&&donorDisplacement.y>0.12;
        // The original Mob's NoAI flag suppresses LivingEntity.aiStep's
        // ordinary travel() call as well as its decision loop. Its physical
        // entity tick still runs, but gravity, fluid response and collision
        // movement would silently stop on idle frames unless this adapter
        // supplies the one native travel step for the body. Always execute it,
        // including zero-input frames; this is not a second travel step.
        // The native recipient travel implementation remains authoritative
        // for gravity, collision, fluids, climbing, flying and modded movement.
        body.setSpeed(MOB_TRAVEL_BASE_SPEED);
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
            double nativeScale=nativeInputScale(mob);
            mob.setSpeed(MOB_TRAVEL_BASE_SPEED);
            strafe*=nativeScale;
            forward*=nativeScale;
            boolean originallyNoAi=mob.isNoAi();
            if(originallyNoAi) mob.setNoAi(false);
            Vec3 beforeTravel=Boolean.getBoolean("freakyfriday.debugMotion")
                ?mob.position():Vec3.ZERO;
            try {
                if(jump) {
                    mob.getJumpControl().jump();
                    mob.getJumpControl().tick();
                }
                // Vec3 components are local strafe / vertical / forward
                // input. Mob.travel selects the correct native behavior for
                // land, water, lava, climbing, flying and modded bodies.
                mob.travel(new Vec3(strafe,jump?1.0:0.0,forward));
                if(Boolean.getBoolean("freakyfriday.debugMotion"))
                    System.out.println("[FF-MOTION] type="+mob.getType()
                        +" attr="+mob.getAttributeValue(Attributes.MOVEMENT_SPEED)
                        +" scale="+nativeScale+" speed="+mob.getSpeed()
                        +" input="+forward+","+strafe
                        +" moved="+mob.position().subtract(beforeTravel));
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
