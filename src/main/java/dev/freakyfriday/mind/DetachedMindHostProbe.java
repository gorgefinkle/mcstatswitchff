package dev.freakyfriday.mind;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;

/**
 * Exact donor EntityType factory feasibility test. Never world-registers or
 * ticks the detached entity. This is not an arbitrary-mod-code sandbox:
 * constructors / class initializers / setPos may perform real world effects.
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
        return construct(donor, recipient, ProbeTelemetry.newTrace());
    }

    public static Creation construct(Mob donor, Mob recipient, String trace) {
        if (donor == null || recipient == null || donor == recipient
                || !donor.isAlive() || !recipient.isAlive()
                || donor.level() != recipient.level()
                || !(donor.level() instanceof ServerLevel level)) {
            ProbeTelemetry.warn(trace, "constructor_reject",
                "Invalid pair; distinct living NPC bodies in one ServerLevel required");
            return new Creation(null,
                "Two different, alive NPCs must share the same loaded server world.");
        }
        ProbeTelemetry.info(trace, "constructor_begin",
            "donorId=%s donorType=%s donorClass=%s recipientId=%s recipientType=%s recipientClass=%s donorPos=%s recipientPos=%s donorHealth=%.2f recipientHealth=%.2f",
            donor.getUUID(), donor.getType(), donor.getClass().getName(),
            recipient.getUUID(), recipient.getType(), recipient.getClass().getName(),
            donor.position(), recipient.position(),
            donor.getHealth(), recipient.getHealth());

        // Baselines must precede the untrusted native factory, not be
        // captured after potential constructor side effects have occurred.
        ProbeBodySnapshot donorBefore = ProbeBodySnapshot.take(donor);
        ProbeBodySnapshot recipientBefore = ProbeBodySnapshot.take(recipient);
        ProbeTelemetry.info(trace, "constructor_body_baseline",
            "donorId=%s donorHealth=%.2f donorMax=%.2f donorPos=%s recipientId=%s recipientHealth=%.2f recipientMax=%.2f recipientPos=%s",
            donorBefore.id(), donorBefore.health(), donorBefore.maxHealth(), donorBefore.position(),
            recipientBefore.id(), recipientBefore.health(),
            recipientBefore.maxHealth(), recipientBefore.position());

        // The registered EntityType factory, never spawn/finalizeSpawn or tick.
        final Entity created;
        try {
            created = donor.getType().create(level, EntitySpawnReason.COMMAND);
        } catch (Exception | LinkageError error) {
            ProbeTelemetry.error(trace, "constructor_exception", error,
                "Factory threw before a host was returned; original mob AI not executed");
            try {
                String donorChanges = donorBefore.changes(donor);
                String recipientChanges = recipientBefore.changes(recipient);
                if (!donorChanges.isEmpty() || !recipientChanges.isEmpty()) {
                    ProbeTelemetry.warn(trace, "constructor_exception_body_mutation",
                        "Constructor threw AND changed bodies: donor=[%s] recipient=[%s]",
                        donorChanges, recipientChanges);
                }
            } catch (Exception | LinkageError diagnosticsFailed) {
                ProbeTelemetry.error(trace, "constructor_failure_snapshot", diagnosticsFailed,
                    "Could not measure original body state after failed constructor");
            }
            return new Creation(null, "Native donor factory failed: "
                + error.getClass().getSimpleName() + " (trace " + trace + ")");
        }
        String donorFactoryChanges = donorBefore.changes(donor);
        String recipientFactoryChanges = recipientBefore.changes(recipient);
        if (!donorFactoryChanges.isEmpty() || !recipientFactoryChanges.isEmpty()) {
            ProbeTelemetry.warn(trace, "constructor_body_mutation",
                "Donor changes=[%s] Recipient changes=[%s] Host rejected. Side effects may already have occurred.",
                donorFactoryChanges, recipientFactoryChanges);
            return new Creation(null, "Host factory changed original donor/recipient body properties. "
                + "Inspect constructor_body_mutation trace " + trace);
        }
        if (!(created instanceof Mob host)) {
            ProbeTelemetry.warn(trace, "constructor_reject",
                "Non-Mob or null donor factory result: %s",
                created == null ? "null" : created.getClass().getName());
            return new Creation(null, "Donor factory did not create an NPC Mob.");
        }

        boolean exactClass = host.getClass() == donor.getClass();
        boolean exactType = host.getType() == donor.getType();
        boolean uniqueId = !host.getUUID().equals(donor.getUUID())
            && !host.getUUID().equals(recipient.getUUID());
        boolean detached = level.getEntity(host.getUUID()) == null;
        ProbeTelemetry.info(trace, "constructor_result",
            "hostId=%s class=%s type=%s exactClass=%s exactType=%s uniqueUUID=%s worldUnregistered=%s hostGoalCount=%d hostTargetCount=%d hostBrainActive=%s",
            host.getUUID(), host.getClass().getName(), host.getType(),
            exactClass, exactType, uniqueId, detached,
            host.goalSelector.getAvailableGoals().size(),
            host.targetSelector.getAvailableGoals().size(),
            !host.getBrain().isBrainDead());

        if (!exactClass) return reject(trace, "Donor factory returned a different Java class.");
        if (!exactType) return reject(trace, "Donor factory returned a different EntityType.");
        if (!uniqueId) return reject(trace, "Host shares an original body's UUID.");
        if (!detached) return reject(trace,
            "Host is ALREADY world registered; experimental construction unsafe.");
        return new Creation(host, null);
    }

    private static Creation reject(String trace, String reason) {
        ProbeTelemetry.warn(trace, "constructor_reject", "%s", reason);
        return new Creation(null, reason);
    }

    public static String inspect(Mob donor, Mob recipient) {
        String trace = ProbeTelemetry.newTrace();
        Creation candidate = construct(donor, recipient, trace);
        if (candidate.host() == null) {
            ProbeTelemetry.warn(trace, "one_shot_abort", "reason=%s", candidate.problem());
            return "Detached host rejected (" + trace + "): " + candidate.problem()
                + ". No host AI was ticked.";
        }

        Mob host = candidate.host();
        ServerLevel level = (ServerLevel) recipient.level();
        ProbeBodySnapshot donorBeforePosition = ProbeBodySnapshot.take(donor);
        ProbeBodySnapshot recipientBeforePosition = ProbeBodySnapshot.take(recipient);
        final java.util.UUID originalRecipientId = recipient.getUUID();
        final String originalRecipientType = recipient.getType().toString();
        final float originalHealth = recipient.getHealth();
        final float originalMaxHealth = recipient.getMaxHealth();

        try {
            ProbeTelemetry.info(trace, "one_shot_before_setpos",
                "hostPos=%s recipientPos=%s hostPresentInLevel=%s",
                host.position(), recipient.position(),
                level.getEntity(host.getUUID()) != null);
            host.setPos(recipient.getX(), recipient.getY(), recipient.getZ());
            boolean detached = level.getEntity(host.getUUID()) == null;
            double alignmentErrorSq = host.position().distanceToSqr(recipient.position());
            boolean aligned = alignmentErrorSq < 0.000001;
            boolean originalIntact = recipient.getUUID().equals(originalRecipientId)
                && recipient.getType().toString().equals(originalRecipientType)
                && Float.compare(recipient.getHealth(), originalHealth) == 0
                && Float.compare(recipient.getMaxHealth(), originalMaxHealth) == 0;

            String result = "trace=" + trace
                + " donorType=" + donor.getType()
                + " recipientType=" + recipient.getType()
                + " hostType=" + host.getType()
                + " hostUUID=" + host.getUUID()
                + " registered=" + !detached
                + " aligned=" + aligned
                + " alignmentErrorSquared=" + alignmentErrorSq
                + " bodyUnchanged=" + originalIntact
                + " goalCountDonor=" + donor.goalSelector.getAvailableGoals().size()
                + " goalCountHost=" + host.goalSelector.getAvailableGoals().size()
                + " targetCountDonor=" + donor.targetSelector.getAvailableGoals().size()
                + " targetCountHost=" + host.targetSelector.getAvailableGoals().size()
                + " brainActiveDonor=" + !donor.getBrain().isBrainDead()
                + " brainActiveHost=" + !host.getBrain().isBrainDead();
            String donorChanges = donorBeforePosition.changes(donor);
            String recipientChanges = recipientBeforePosition.changes(recipient);
            boolean noBodySideEffects = donorChanges.isEmpty() && recipientChanges.isEmpty();
            if (!noBodySideEffects) {
                ProbeTelemetry.warn(trace, "one_shot_body_mutation",
                    "Donor changes=[%s] Recipient changes=[%s]", donorChanges, recipientChanges);
            }
            if (!detached || !aligned || !originalIntact || !noBodySideEffects)
                ProbeTelemetry.warn(trace, "one_shot_result", "%s bodiesUnchanged=%s", result, noBodySideEffects);
            else
                ProbeTelemetry.info(trace, "one_shot_result", "%s bodiesUnchanged=%s", result, noBodySideEffects);
            return "Dual-instance host feasibility (" + trace
                + "): host NOT world-registered=" + detached
                + "; host aligned=" + aligned
                + "; recipient unchanged=" + originalIntact
                + "; donor/recipient body snapshots unchanged=" + noBodySideEffects
                + "; see latest.log [FF-PROBE] for detailed entity identities and AI counts."
                + " Host AI never ticked; constructor effects are NOT sandboxed.";
        } catch (Exception | LinkageError error) {
            ProbeTelemetry.error(trace, "one_shot_exception", error,
                "Host inspection / setPos failed; AI not ticked");
            // Do not hide mutation that occurred *before* a throwing
            // position override. Compare against pre-position snapshots.
            try {
                String donorChanges = donorBeforePosition.changes(donor);
                String recipientChanges = recipientBeforePosition.changes(recipient);
                if (!donorChanges.isEmpty() || !recipientChanges.isEmpty()) {
                    ProbeTelemetry.warn(trace, "one_shot_exception_body_mutation",
                        "donorChanges=[%s] recipientChanges=[%s] position failed after body mutation",
                        donorChanges, recipientChanges);
                }
            } catch (Exception | LinkageError diagnosticFailure) {
                ProbeTelemetry.error(trace, "one_shot_exception_snapshot_failure",
                    diagnosticFailure, "Failed to audit originals after one-shot setPos error");
            }
            return "Detached host inspection failed (" + trace + "): "
                + error.getClass().getSimpleName()
                + ". Full stack trace in latest.log; no host AI tick.";
        }
    }
}
