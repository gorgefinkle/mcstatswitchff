package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;

/**
 * Experimental dual-instance construction test: a real recipient Mob remains
 * the only world-registered physical body, while a separate entity of the
 * exact donor's registered type is constructed, never spawned and NEVER ticked.
 *
 * Note: arbitrary third-party entity factories/constructors can themselves
 * have world effects. This is an explicit diagnostic for disposable worlds,
 * not an automatic production swap or a world-side-effect sandbox.
 */
public final class DetachedMindHostProbe {
    private DetachedMindHostProbe() {}

    public static String inspect(Mob donor, Mob recipient) {
        if (donor == recipient || !donor.isAlive() || !recipient.isAlive()
                || donor.level() != recipient.level()
                || !(donor.level() instanceof ServerLevel level)) {
            return "Rejected: two different, alive NPC bodies must share a server world.";
        }

        final java.util.UUID donorId = donor.getUUID();
        final java.util.UUID recipientId = recipient.getUUID();
        final String recipientType = recipient.getType().toString();
        final float recipientHealth = recipient.getHealth();
        final float recipientMaxHealth = recipient.getMaxHealth();
        final double recipientX = recipient.getX(), recipientY = recipient.getY(),
                recipientZ = recipient.getZ();

        // This uses the donor's ACTUAL registered EntityType factory. There
        // are no species-specific AI recipes, Goal clones or pairing tables.
        // DO NOT call EntityType.spawn(), level.addFreshEntity(), finalizeSpawn()
        // or any Goal/Brain/mob tick on this candidate host.
        final Entity created;
        try {
            created = donor.getType().create(level, EntitySpawnReason.COMMAND);
        } catch (Throwable constructionFailure) {
            return "Detached host factory rejected " + donor.getType()
                + ": " + constructionFailure.getClass().getSimpleName()
                + ". No host AI was ticked; donor and recipient remain unchanged.";
        }
        if (!(created instanceof Mob host)) {
            return "Detached host could not be constructed as an NPC Mob: "
                + donor.getType() + ". No AI was ticked.";
        }

        // Check class identity before aligning position. A mod's factory may
        // intentionally produce a different Mob subtype; do not pretend that
        // such a result is equivalent to the donor's executable Java AI.
        boolean exactClass = host.getClass() == donor.getClass();
        boolean exactType = host.getType() == donor.getType();
        boolean uniqueIdentity = !host.getUUID().equals(donorId)
            && !host.getUUID().equals(recipientId);
        boolean unregisteredBefore = level.getEntity(host.getUUID()) == null;
        if (!exactClass || !exactType || !uniqueIdentity || !unregisteredBefore) {
            return "Detached host FAILED preflight: exactClass=" + exactClass
                + ", exactType=" + exactType + ", uniqueUUID=" + uniqueIdentity
                + ", absentFromWorld=" + unregisteredBefore
                + ". No host AI was ticked and no entity was deliberately spawned.";
        }

        try {
            // Position mirror is passive positioning only, not a physical AI
            // tick. Some modded subclasses can override position methods:
            // experimental constructor/position operations aren't sandboxed.
            host.setPos(recipientX, recipientY, recipientZ);
            boolean unregisteredAfter = level.getEntity(host.getUUID()) == null;
            boolean atRecipient = host.position().distanceToSqr(recipient.position()) < 0.000001;
            boolean bodyIntact = recipient.getUUID().equals(recipientId)
                && recipient.getType().toString().equals(recipientType)
                && Float.compare(recipient.getHealth(), recipientHealth) == 0
                && Float.compare(recipient.getMaxHealth(), recipientMaxHealth) == 0;
            int donorGoals = donor.goalSelector.getAvailableGoals().size();
            int donorTargets = donor.targetSelector.getAvailableGoals().size();
            int hostGoals = host.goalSelector.getAvailableGoals().size();
            int hostTargets = host.targetSelector.getAvailableGoals().size();
            boolean donorBrain = !donor.getBrain().isBrainDead();
            boolean hostBrain = !host.getBrain().isBrainDead();

            // Local variable only: do not cache, spawn or tick this candidate.
            // Its reference becomes eligible for GC after this diagnostic.
            return "Dual-instance host feasibility: donor=" + donor.getType()
                + " recipient=" + recipient.getType()
                + "; original donor Java class preserved=" + exactClass
                + "; host separate UUID=" + uniqueIdentity
                + "; host NOT world-registered=" + unregisteredAfter
                + "; host aligned to recipient coordinates=" + atRecipient
                + "; visible recipient body unchanged=" + bodyIntact
                + "; goal counts donor/host=" + donorGoals + "/" + hostGoals
                + "; target counts donor/host=" + donorTargets + "/" + hostTargets
                + "; Brain active donor/host=" + donorBrain + "/" + hostBrain
                + ". Host AI NEVER ticked; constructor safety, AI isolation, "
                + "state transfer and player controls remain UNPROVEN.";
        } catch (Throwable inspectionFailure) {
            return "Detached host diagnostic failed: "
                + inspectionFailure.getClass().getSimpleName()
                + ". No host AI was ticked; construction may have side effects.";
        }
    }
}
