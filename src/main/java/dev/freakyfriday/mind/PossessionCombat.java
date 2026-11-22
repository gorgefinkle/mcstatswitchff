package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** The client supplies a potential target, never permission to deal damage. */
public final class PossessionCombat {
    private PossessionCombat(){}
    public static Entity validTarget(ServerLevel level,LivingEntity body,int candidateId){
        if(candidateId<0||!body.isAlive())return null;
        Entity target=level.getEntity(candidateId);
        if(!(target instanceof LivingEntity living) || !living.isAlive()
                || target==body || target.isRemoved() || target.level()!=body.level())
            return null;
        double range=3.0+(body.getBbWidth()+target.getBbWidth())*0.5;
        if(body.distanceToSqr(target)>range*range || !body.hasLineOfSight(target))
            return null;
        Vec3 direction=target.getBoundingBox().getCenter().subtract(body.getEyePosition());
        if(direction.lengthSqr()>1e-8
                && body.getViewVector(1.0f).dot(direction.normalize())<0.65)
            return null;
        return target;
    }
}
