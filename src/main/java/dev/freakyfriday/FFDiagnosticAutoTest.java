package dev.freakyfriday;

import dev.freakyfriday.mind.DetachedMindHostProbe;
import dev.freakyfriday.mind.ProbeBodySnapshot;
import dev.freakyfriday.mind.ProbeTelemetry;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;

/**
 * Operator-only one-command detached-host construction diagnostics.
 *
 * All three instances are constructed via real registered factories but NONE
 * are added to the level, finalized/spawned, or ticked. This is not a swap and
 * cannot certify arbitrary constructor/setPos world effects. Disposable worlds
 * only. Avoids asking a tester to spawn (or fight) an Ender Dragon.
 */
public final class FFDiagnosticAutoTest {
    private FFDiagnosticAutoTest() {}
    public record Case(String donor, String recipient) {}
    public record Result(String donor, String recipient, boolean passed, String detail) {}

    private static final List<Case> VANILLA_CASES = List.of(
        new Case("minecraft:zombie", "minecraft:cow"),
        new Case("minecraft:cow", "minecraft:zombie"),
        new Case("minecraft:villager", "minecraft:cow"),
        new Case("minecraft:creeper", "minecraft:cow"),
        new Case("minecraft:cow", "minecraft:creeper"),
        new Case("minecraft:zombie", "minecraft:villager"),
        new Case("minecraft:ender_dragon", "minecraft:chicken"),
        new Case("minecraft:chicken", "minecraft:ender_dragon")
    );

    public static List<Case> cases() { return VANILLA_CASES; }

    private static EntityType<?> resolve(String input) {
        String id = input.indexOf(':') == -1 ? "minecraft:" + input : input;
        Identifier key = Identifier.tryParse(id);
        // ENTITY_TYPE is a defaulted registry: getValue(missing_id) may
        // return a fallback entity, creating a dangerous false PASS.
        // Require an exact registered key before asking for its value.
        return key == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key)
            ? null : BuiltInRegistries.ENTITY_TYPE.getValue(key);
    }

    public static Result checkPair(ServerLevel level, String donorId, String recipientId) {
        String trace = ProbeTelemetry.newTrace();
        try {
            EntityType<?> sourceType = resolve(donorId);
            EntityType<?> targetType = resolve(recipientId);
            if (sourceType == null || targetType == null)
                return fail(trace, donorId, recipientId, "Unknown entity type");
            final Entity first, second;
            try {
                first = sourceType.create(level, EntitySpawnReason.COMMAND);
                second = targetType.create(level, EntitySpawnReason.COMMAND);
            } catch (Exception | LinkageError failure) {
                ProbeTelemetry.error(trace, "quicktest_factory_exception", failure,
                    "Constructing diagnostic donor or recipient failed");
                return fail(trace, donorId, recipientId,
                    "Native factory exception: " + failure.getClass().getSimpleName());
            }
            if (!(first instanceof Mob donor) || !(second instanceof Mob recipient))
                return fail(trace, donorId, recipientId,
                    "Requires two NPC Mob entity types; no player or non-Mob entities");
            if (donor.getType() != sourceType || recipient.getType() != targetType)
                return fail(trace, donorId, recipientId, "Factory returned a different EntityType");
            if (donor.getUUID().equals(recipient.getUUID())
                || level.getEntity(donor.getUUID()) != null
                || level.getEntity(recipient.getUUID()) != null)
                return fail(trace, donorId, recipientId,
                    "Factory produced shared identity or registered an original in the world");

            ProbeBodySnapshot beforeDonor = ProbeBodySnapshot.take(donor);
            ProbeBodySnapshot beforeRecipient = ProbeBodySnapshot.take(recipient);
            DetachedMindHostProbe.Creation created =
                DetachedMindHostProbe.construct(donor, recipient, trace);
            if (created.host() == null)
                return fail(trace, donorId, recipientId, created.problem());

            Mob host = created.host();
            if (host == donor || host == recipient
                || host.getClass() != donor.getClass()
                || host.getType() != donor.getType()
                || host.getUUID().equals(donor.getUUID())
                || host.getUUID().equals(recipient.getUUID())
                || level.getEntity(host.getUUID()) != null)
                return fail(trace, donorId, recipientId,
                    "Detached host type/class/UUID/registration invariant failed");

            try {
                host.setPos(recipient.getX(), recipient.getY(), recipient.getZ());
            } catch (Exception | LinkageError failure) {
                ProbeTelemetry.error(trace, "quicktest_position_exception", failure,
                    "Detached host setPos threw");
                return fail(trace, donorId, recipientId,
                    "Host position exception: " + failure.getClass().getSimpleName());
            }
            String donorMutation = beforeDonor.changes(donor);
            String recipientMutation = beforeRecipient.changes(recipient);
            if (!donorMutation.isEmpty() || !recipientMutation.isEmpty())
                return fail(trace, donorId, recipientId,
                    "Original body mutated: donor=[" + donorMutation
                        + "] recipient=[" + recipientMutation + "]");
            if (host.position().distanceToSqr(recipient.position()) > 0.000001
                || level.getEntity(host.getUUID()) != null)
                return fail(trace, donorId, recipientId,
                    "Detached host position or world-registration invariant failed");

            Result result = new Result(donorId, recipientId, true,
                "PASS; host=" + host.getType() + " (" + host.getClass().getName()
                + "), detached=true, bodyUnchanged=true, hostAITicked=false,"
                + " actualMindSwapped=false, trace=" + trace);
            ProbeTelemetry.info(trace, "quicktest_pass", "%s", result.detail());
            return result;
        } catch (Exception | LinkageError failure) {
            ProbeTelemetry.error(trace, "quicktest_unexpected_exception", failure,
                "Unexpected diagnostic failure; original bodies may already be affected");
            return fail(trace, donorId, recipientId,
                "Unexpected " + failure.getClass().getSimpleName());
        }
    }

    private static Result fail(String trace, String donorId, String recipientId,
                               String details) {
        ProbeTelemetry.warn(trace, "quicktest_fail",
            "donor=%s recipient=%s reason=%s", donorId, recipientId, details);
        return new Result(donorId, recipientId, false, "FAIL: " + details
            + "; no mind swap performed, trace=" + trace);
    }

    public static int runPair(ServerPlayer player, String donor, String recipient) {
        Result result = checkPair((ServerLevel) player.level(), donor, recipient);
        player.sendSystemMessage(Component.literal("[Freaky Friday] Quicktest "
            + donor + " -> " + recipient + ": " + result.detail()
            + " (DISPOSABLE WORLDS ONLY: factory effects are not sandboxed.)"));
        return result.passed() ? 1 : 0;
    }

    public static int runSuite(ServerPlayer player) {
        ServerLevel level = (ServerLevel) player.level();
        int passed = 0;
        for (Case pair : VANILLA_CASES) {
            Result result = checkPair(level, pair.donor(), pair.recipient());
            if (result.passed()) passed++;
            player.sendSystemMessage(Component.literal("[Freaky Friday] "
                + pair.donor() + " -> " + pair.recipient() + " : "
                + (result.passed() ? "PASS" : result.detail())));
        }
        player.sendSystemMessage(Component.literal("[Freaky Friday] QUICKTEST SUITE: "
            + passed + "/" + VANILLA_CASES.size() + " passed; "
            + "NO MIND SWAPS or AI ticks. World writes are NOT sandboxed; "
            + "use disposable worlds. Full traces in latest.log."));
        return passed == VANILLA_CASES.size() ? 1 : 0;
    }
}
