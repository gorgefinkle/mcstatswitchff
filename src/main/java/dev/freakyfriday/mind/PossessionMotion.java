package dev.freakyfriday.mind;

import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * One collision-resolved physical movement step, used on BOTH sides.
 * Server owns real damage, block interaction, and the final position.
 * Client runs the same step immediately for responsive visual prediction.
 */
public final class PossessionMotion {
    private PossessionMotion() {}

    public static double step(LivingEntity body, float rawForward, float rawStrafe,
                              boolean jump, float yaw, float pitch, double priorYVelocity) {
        double f=rawForward, s=rawStrafe;
        double length=Math.sqrt(f*f+s*s);
        if(length>1.0){f/=length;s/=length;}
        double nativeSpeed=body.getAttributeValue(Attributes.MOVEMENT_SPEED);
        double speed=Double.isFinite(nativeSpeed)
            ? Math.max(0.0,Math.min(0.45,nativeSpeed*0.76)) : 0.0;
        double angle=Math.toRadians(yaw);
        double dx=(-Math.sin(angle)*f+Math.cos(angle)*s)*speed;
        double dz=( Math.cos(angle)*f+Math.sin(angle)*s)*speed;
        double dy=jump&&body.onGround()
            ? (body instanceof Mob ? 0.40 : 0.42)
            : body.onGround()||body.isNoGravity()
                ? 0.0 : Math.max(-0.58,priorYVelocity-0.08);
        // Vanilla chicken aiStep damps falling velocity each tick. Paused
        // AI does not execute that native damping, so preserve it here.
        if(dy<0 && body.getType()==EntityTypes.CHICKEN)dy*=0.6;
        body.setYRot(yaw);
        body.setXRot(pitch);
        body.setYHeadRot(yaw);
        body.move(MoverType.SELF,new Vec3(dx,dy,dz));
        double nextY=body.onGround()?0.0:dy*0.98;
        body.setDeltaMovement(new Vec3(0,nextY,0));
        return nextY;
    }
}
