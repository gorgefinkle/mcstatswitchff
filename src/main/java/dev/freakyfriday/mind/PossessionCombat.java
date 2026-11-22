package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/** Do not trust candidate identity/range/facing supplied by a client. */
public final class PossessionCombat {
    private PossessionCombat() {}
    public static Entity findValidMeleeTarget(ServerLevel level,LivingEntity actor,int id) {
        return findValidMeleeTarget(level,actor,id,null);
    }

    /**
     * The authenticated controller's original ServerPlayer can be targeted
     * without trusting the packet to nominate an arbitrary off-world player.
     * Vanilla's server player list and the GameTest mock-player lifecycle do
     * not use identical entity-registration paths. A direct owner match is
     * safe only for the already authenticated connection; all other targets
     * still come from the server's tracked entity index.
     */
    public static Entity findValidMeleeTarget(ServerLevel level,LivingEntity actor,
                                              int id,net.minecraft.server.level.ServerPlayer owner){
        if(id<0||!actor.isAlive())return null;
        Entity target=owner!=null && owner.getId()==id
            && owner.level()==level && !owner.isRemoved() ? owner : level.getEntity(id);
        if(!(target instanceof LivingEntity living)||target==actor
           ||!living.isAlive()||target.isRemoved()||target.level()!=actor.level())return null;
        double reach=3.0+(actor.getBbWidth()+target.getBbWidth())*0.5;
        if(actor.distanceToSqr(target)>reach*reach||!actor.hasLineOfSight(target))return null;
        Vec3 toward=target.getBoundingBox().getCenter().subtract(actor.getEyePosition());
        if(toward.lengthSqr()<1e-8)return target;
        return actor.getViewVector(1.0f).dot(toward.normalize())>=0.65?target:null;
    }
}
