package dev.freakyfriday.mind;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Species-independent native mind hosts.
 *
 * Each donor is recreated through its registered EntityType factory. That
 * same native Mob class runs its own goals/Brain at the recipient body's
 * position. The host is hidden and non-physical; its observed movement and
 * look are applied to the ORIGINAL recipient entity, whose collision,
 * attributes, health, equipment and identity remain authoritative.
 *
 * Direct melee damage is intercepted at Minecraft's damage boundary and
 * replayed by the physical recipient body. Projectile ownership is likewise
 * rebound to the physical body. This is an in-process runtime prototype:
 * arbitrary modded world writes and every possible AI side effect are not yet
 * sandboxed, so this class is not a claim of complete mind-transfer support.
 */
public final class NativeMindProxySessions {
    private NativeMindProxySessions() {}

    private static final Map<UUID, Pair> ACTIVE = new ConcurrentHashMap<>();
    private static final Map<Mob, Host> PROXY_TO_BODY = new IdentityHashMap<>();
    private static final Map<Mob, Host> BODY_TO_PROXY = new IdentityHashMap<>();

    private static final class Host {
        final UUID owner;
        final Mob body;
        final Mob proxy;
        final boolean originalNoAi;
        final boolean originalNoGravity;
        final LivingEntity originalTarget;
        Vec3 lastProxyPosition;

        Host(UUID owner,Mob body,Mob proxy) {
            this.owner=owner;
            this.body=body;
            this.proxy=proxy;
            this.originalNoAi=body.isNoAi();
            this.originalNoGravity=body.isNoGravity();
            this.originalTarget=body.getTarget();
            this.lastProxyPosition=proxy.position();
        }
    }

    private record Pair(UUID owner,Host firstMindInSecondBody,
                        Host secondMindInFirstBody,String dimension) {}

    /** Begin a reciprocal native-AI exchange without replacing either body. */
    public static synchronized String begin(UUID owner,Mob first,Mob second) {
        if(owner==null || first==null || second==null)
            return "Missing owner or selected Mob";
        if(first==second)return "Choose two different bodies";
        if(!first.isAlive() || !second.isAlive())
            return "Both selected bodies must be alive";
        if(first.level()!=second.level() || !(first.level() instanceof ServerLevel level))
            return "Both bodies must be in the same live server level";
        if(ACTIVE.containsKey(owner))return "This account already owns an active mind exchange";
        if(level.getEntity(first.getUUID())!=first || level.getEntity(second.getUUID())!=second)
            return "Both physical bodies must be registered in the world";

        Mob firstMindProxy=createNativeProxy(first,second);
        Mob secondMindProxy=createNativeProxy(second,first);
        if(firstMindProxy==null || secondMindProxy==null) {
            if(firstMindProxy!=null)firstMindProxy.discard();
            if(secondMindProxy!=null)secondMindProxy.discard();
            return "A selected registered Mob type could not construct its native AI host";
        }

        boolean firstAdded=level.addFreshEntity(firstMindProxy);
        boolean secondAdded=level.addFreshEntity(secondMindProxy);
        if(!firstAdded || !secondAdded) {
            if(firstAdded)firstMindProxy.discard();
            if(secondAdded)secondMindProxy.discard();
            return "The world rejected one of the native AI hosts";
        }

        // first donor's native mind now drives second's physical body;
        // second donor's native mind now drives first's physical body.
        Host firstMindInSecond=new Host(owner,second,firstMindProxy);
        Host secondMindInFirst=new Host(owner,first,secondMindProxy);
        Pair pair=new Pair(owner,firstMindInSecond,secondMindInFirst,
            level.dimension().identifier().toString());

        first.getNavigation().stop();
        second.getNavigation().stop();
        first.setTarget(null);
        second.setTarget(null);
        first.setNoAi(true);
        second.setNoAi(true);

        PROXY_TO_BODY.put(firstMindProxy,firstMindInSecond);
        PROXY_TO_BODY.put(secondMindProxy,secondMindInFirst);
        BODY_TO_PROXY.put(second,firstMindInSecond);
        BODY_TO_PROXY.put(first,secondMindInFirst);
        ACTIVE.put(owner,pair);
        return null;
    }

    private static Mob createNativeProxy(Mob donor,Mob body) {
        if(!(body.level() instanceof ServerLevel level))return null;
        Entity created=donor.getType().create(level,EntitySpawnReason.COMMAND);
        if(!(created instanceof Mob proxy))return null;
        proxy.setPos(body.getX(),body.getY(),body.getZ());
        proxy.setYRot(body.getYRot());
        proxy.setXRot(body.getXRot());
        proxy.setYHeadRot(body.getYHeadRot());
        proxy.setYBodyRot(body.getYRot());
        proxy.setNoAi(false);
        proxy.setNoGravity(true);
        proxy.noPhysics=true;
        proxy.setInvisible(true);
        proxy.setSilent(true);
        proxy.setPersistenceRequired();
        proxy.setCanPickUpLoot(false);
        LivingEntity target=donor.getTarget();
        if(target!=null && target.isAlive() && target.level()==body.level())
            proxy.setTarget(target);
        return proxy;
    }

    public static boolean active(UUID owner) {
        return ACTIVE.containsKey(owner);
    }

    /** Test/diagnostic access; returns the hidden native host for one body. */
    public static Mob proxyForBody(UUID owner,UUID bodyId) {
        Pair pair=ACTIVE.get(owner);
        if(pair==null)return null;
        if(pair.firstMindInSecondBody.body.getUUID().equals(bodyId))
            return pair.firstMindInSecondBody.proxy;
        if(pair.secondMindInFirstBody.body.getUUID().equals(bodyId))
            return pair.secondMindInFirstBody.proxy;
        return null;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if(ACTIVE.isEmpty())return;
        for(Pair pair:new ArrayList<>(ACTIVE.values())) {
            Host a=pair.firstMindInSecondBody;
            Host b=pair.secondMindInFirstBody;
            if(!a.body.isAlive() || a.body.isRemoved()
                    || !b.body.isAlive() || b.body.isRemoved()
                    || !a.proxy.isAlive() || a.proxy.isRemoved()
                    || !b.proxy.isAlive() || b.proxy.isRemoved()) {
                end(pair.owner);
                continue;
            }
            step(a);
            step(b);
        }
    }

    private static void step(Host host) {
        Mob body=host.body,proxy=host.proxy;
        Vec3 now=proxy.position();
        Vec3 delta=now.subtract(host.lastProxyPosition);
        double horizontal=Math.hypot(delta.x,delta.z);
        if(horizontal>0.0005) {
            double bodySpeed=body.getAttributeValue(Attributes.MOVEMENT_SPEED);
            double travel=Math.max(0.025,Math.min(0.34,bodySpeed*0.82));
            Vec3 movement=new Vec3(delta.x/horizontal*travel,0,
                delta.z/horizontal*travel);
            // Apply the donor's native direction to the recipient's real
            // collision box. The body's own world physics resolves the move.
            body.move(MoverType.SELF,movement);
        }
        if(delta.y>0.08 && body.onGround()) {
            Vec3 velocity=body.getDeltaMovement();
            body.setDeltaMovement(velocity.x,Math.max(velocity.y,0.42),velocity.z);
        }

        body.setYRot(proxy.getYRot());
        body.setXRot(proxy.getXRot());
        body.setYHeadRot(proxy.getYHeadRot());
        body.setYBodyRot(proxy.getYRot());

        // Keep the hidden native host co-located with the physical body so
        // its next native AI tick observes the recipient's current world.
        proxy.setPos(body.getX(),body.getY(),body.getZ());
        proxy.setDeltaMovement(Vec3.ZERO);
        host.lastProxyPosition=proxy.position();
    }

    @SubscribeEvent
    public static void onNativeMelee(LivingIncomingDamageEvent event) {
        if(event.getEntity() instanceof Mob damagedProxy
                && PROXY_TO_BODY.containsKey(damagedProxy)) {
            event.setCanceled(true);
            return;
        }
        Entity attacker=event.getSource().getEntity();
        if(!(attacker instanceof Mob proxy))return;
        Host host=PROXY_TO_BODY.get(proxy);
        if(host==null)return;
        LivingEntity target=event.getEntity();
        // Cancel the shadow hit. The original recipient performs the action.
        event.setCanceled(true);
        if(!target.isAlive() || target==host.body
                || target.level()!=host.body.level()
                || host.body.distanceToSqr(target)>16.0)return;
        if(host.body.getAttribute(Attributes.ATTACK_DAMAGE)==null) {
            dev.freakyfriday.compat.PlatformHooks.nativeSwing(host.body);
        } else if(host.body.level() instanceof ServerLevel level) {
            host.body.doHurtTarget(level,target);
        }
    }

    @SubscribeEvent
    public static void onNativeProjectileJoin(EntityJoinLevelEvent event) {
        if(!(event.getEntity() instanceof Projectile projectile))return;
        Entity owner=projectile.getOwner();
        if(!(owner instanceof Mob proxy))return;
        Host host=PROXY_TO_BODY.get(proxy);
        if(host!=null)projectile.setOwner(host.body);
    }

    public static synchronized boolean end(UUID owner) {
        Pair pair=ACTIVE.remove(owner);
        if(pair==null)return false;
        restore(pair.firstMindInSecondBody);
        restore(pair.secondMindInFirstBody);
        return true;
    }

    private static void restore(Host host) {
        PROXY_TO_BODY.remove(host.proxy);
        BODY_TO_PROXY.remove(host.body);
        if(!host.proxy.isRemoved())host.proxy.discard();
        if(!host.body.isRemoved()) {
            host.body.setNoAi(host.originalNoAi);
            host.body.setNoGravity(host.originalNoGravity);
            LivingEntity target=host.originalTarget;
            if(target!=null && target.isAlive() && target.level()==host.body.level())
                host.body.setTarget(target);
        }
    }

    public static synchronized void clearOnLogout(UUID owner) {
        end(owner);
    }

    public static synchronized void clearOnServerStop() {
        for(UUID owner:new ArrayList<>(ACTIVE.keySet()))end(owner);
        PROXY_TO_BODY.clear();
        BODY_TO_PROXY.clear();
    }
}
