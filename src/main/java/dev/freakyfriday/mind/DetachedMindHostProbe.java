package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;

/**
 * Builds a real, donor-class Mob without registering/ticking it in the world.
 * Used ONLY by explicit disposable-world diagnostics.
 *
 * This is not a sandbox: arbitrary mod constructors/position updates can
 * perform unexpected effects even if the entity is never world-registered.
 */
public final class DetachedMindHostProbe {
    private DetachedMindHostProbe() {}

    public record Creation(Mob host, String problem) {
        public Creation {
            if ((host == null) == (problem == null))
                throw new IllegalArgumentException("Exactly one of host/error must exist");
        }
    }

    public static Creation construct(Mob donor, Mob recipient) {
        if (donor == null || recipient == null || donor == recipient
                || !donor.isAlive() || !recipient.isAlive()
                || donor.level() != recipient.level()
                || !(donor.level() instanceof ServerLevel level)) {
            return new Creation(null,
                "Two different, alive NPCs must share the same loaded server world.");
        }
        // This is the donor's real EntityType factory, not a hand-written
        // cow/zombie/creeper/mobbed-pair constructor or a copied Goal graph.
        // No spawn/finalizeSpawn, addFreshEntity, or AI tick is requested.
        final Entity created;
        try {
            created = donor.getType().create(level, EntitySpawnReason.COMMAND);
        } catch (Exception | LinkageError constructionFailure) {
            return new Creation(null, "Native donor factory failed: "
                + constructionFailure.getClass().getSimpleName());
        }
        if (!(created instanceof Mob host))
            return new Creation(null, "Donor factory did not create an NPC Mob.");
        if (host.getClass() != donor.getClass())
            return new Creation(null, "Donor factory returned a different Java class.");
        if (host.getType() != donor.getType())
            return new Creation(null, "Donor factory returned a different EntityType.");
        if (host.getUUID().equals(donor.getUUID())
                || host.getUUID().equals(recipient.getUUID()))
            return new Creation(null, "New host unexpectedly shares an original body UUID.");
        if (level.getEntity(host.getUUID()) != null)
            return new Creation(null, "New host is ALREADY world registered; refuse.");
        return new Creation(host, null);
    }

    public static String inspect(Mob donor, Mob recipient) {
        Creation candidate = construct(donor, recipient);
        if (candidate.host() == null)
            return "Detached donor host rejected: " + candidate.problem()
                + " No host AI was ticked.";

        Mob host = candidate.host();
        ServerLevel level = (ServerLevel) recipient.level();
        final java.util.UUID recipientId = recipient.getUUID();
        final String recipientType = recipient.getType().toString();
        final float recipientHealth = recipient.getHealth();
        final float recipientMaxHealth = recipient.getMaxHealth();

        try {
            // Position mirror only. A mod can override setPos, so this test
            // must never be used on important worlds.
            host.setPos(recipient.getX(), recipient.getY(), recipient.getZ());
            boolean unregistered = level.getEntity(host.getUUID()) == null;
            boolean mirrored = host.position().distanceToSqr(recipient.position()) < 0.000001;
            boolean physicalBodyIntact = recipient.getUUID().equals(recipientId)
                && recipient.getType().toString().equals(recipientType)
                && Float.compare(recipient.getHealth(), recipientHealth) == 0
                && Float.compare(recipient.getMaxHealth(), recipientMaxHealth) == 0;

            return "Dual-instance host feasibility: donor=" + donor.getType()
                + " recipient=" + recipient.getType()
                + "; original donor Java class preserved=true"
                + "; distinct host UUID=true"
                + "; host NOT world-registered=" + unregistered
                + "; host mirrored recipient position=" + mirrored
                + "; original physical body intact=" + physicalBodyIntact
                + "; goal counts donor/host="
                + donor.goalSelector.getAvailableGoals().size() + "/"
                + host.goalSelector.getAvailableGoals().size()
                + "; target counts donor/host="
                + donor.targetSelector.getAvailableGoals().size() + "/"
                + host.targetSelector.getAvailableGoals().size()
                + "; Brain active donor/host=" + !donor.getBrain().isBrainDead()
                + "/" + !host.getBrain().isBrainDead()
                + ". No ghost AI was ticked. Native AI isolation and "
                + "world-effect-free construction remain UNVERIFIED.";
        } catch (Exception | LinkageError issue) {
            return "Detached host inspection failed without AI tick: "
                + issue.getClass().getSimpleName();
        }
    }
}
