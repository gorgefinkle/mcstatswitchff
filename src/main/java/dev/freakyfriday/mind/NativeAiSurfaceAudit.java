package dev.freakyfriday.mind;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

/**
 * Read-only inventory of a mob's NATIVE decision execution surfaces.
 * It never calls Goal.tick, Brain.tick, Mob.serverAiStep, or other AI code.
 *
 * The report is not a safety certificate. A vanilla or modded goal can call
 * arbitrary world-mutating code, even when the Mob has no unusual overrides.
 */
public final class NativeAiSurfaceAudit {
    private NativeAiSurfaceAudit() {}

    public record GoalInfo(int priority, boolean running, String implementation) {}
    public record OverrideInfo(String declaringClass, String method) {}
    public record Report(String entityType, boolean brainActive,
                         List<GoalInfo> goals, List<GoalInfo> targeting,
                         List<OverrideInfo> nativeOverrides,
                         List<String> effectCandidates) {
        public Report {
            goals = List.copyOf(goals);
            targeting = List.copyOf(targeting);
            nativeOverrides = List.copyOf(nativeOverrides);
            effectCandidates = List.copyOf(effectCandidates);
        }

        /** NEVER say a count of goals proves a world-side-effect-free mind. */
        public List<String> summary() {
            List<String> lines = new ArrayList<>();
            lines.add("Native mind audit for " + entityType + ": "
                + goals.size() + " goals, " + targeting.size()
                + " target goals, active Brain=" + brainActive);
            for (GoalInfo goal : goals.stream().limit(6).toList())
                lines.add("  goal [" + goal.priority() + "]" + (goal.running() ? " RUNNING " : " idle ") + goal.implementation());
            for (GoalInfo goal : targeting.stream().limit(6).toList())
                lines.add("  target [" + goal.priority() + "]" + (goal.running() ? " RUNNING " : " idle ") + goal.implementation());
            if (goals.size() > 6 || targeting.size() > 6)
                lines.add("  additional goals omitted from chat display (count remains exact)");
            if (nativeOverrides.isEmpty()) {
                lines.add("  No candidate mob-class overrides found; goals may STILL have direct effects");
            } else {
                lines.add("  Additional native code surfaces (" + nativeOverrides.size() + "):");
                for (OverrideInfo info : nativeOverrides.stream().limit(6).toList())
                    lines.add("  " + info.declaringClass() + "." + info.method());
            }
            lines.add("  Potential side-effect API references: " + effectCandidates.size());
            for (String call : effectCandidates.stream().limit(8).toList())
                lines.add("  candidate " + call);
            if (effectCandidates.size() > 8)
                lines.add("  remaining candidates omitted (full count above)");
            lines.add("  ISOLATION UNVERIFIED: classfile references do not prove executed code "
                + "and cannot detect all dynamic/indirect effects");
            return List.copyOf(lines);
        }
    }

    private static List<GoalInfo> goals(GoalSelector selector) {
        List<GoalInfo> result = new ArrayList<>();
        for (WrappedGoal wrapper : selector.getAvailableGoals()) {
            result.add(new GoalInfo(wrapper.getPriority(), wrapper.isRunning(),
                wrapper.getGoal().getClass().getName()));
        }
        result.sort((a,b) -> Integer.compare(a.priority(),b.priority()));
        return result;
    }

    private static final List<String> EXECUTION_HOOKS = List.of(
        "customServerAiStep", "serverAiStep", "aiStep", "tick",
        "doHurtTarget", "registerGoals", "hurtServer",
        "attack", "mobInteract", "onSyncedDataUpdated");

    public static Report inspect(LivingEntity entity) {
        if (!(entity instanceof Mob mob))
            throw new IllegalArgumentException("Only NPC Mob AI can be audited currently");
        List<OverrideInfo> methods = new ArrayList<>();
        for (Class<?> cls=mob.getClass(); cls != null && cls != Mob.class
                && cls != LivingEntity.class && cls != Object.class;
                cls=cls.getSuperclass()) {
            for (Method method : cls.getDeclaredMethods()) {
                if (EXECUTION_HOOKS.contains(method.getName())
                        && !method.isSynthetic() && !method.isBridge()) {
                    String owner = cls.getName();
                    String signature = method.getName() + "("
                        + String.join(",", Arrays.stream(method.getParameterTypes())
                            .map(Class::getSimpleName).toList()) + ")";
                    methods.add(new OverrideInfo(owner,signature));
                }
            }
        }
        Set<String> candidates = new LinkedHashSet<>();
        Set<Class<?>> classes = new LinkedHashSet<>();
        classes.add(mob.getClass());
        classes.add(mob.getBrain().getClass());
        for (var slot : mob.goalSelector.getAvailableGoals())
            classes.add(slot.getGoal().getClass());
        for (var slot : mob.targetSelector.getAvailableGoals())
            classes.add(slot.getGoal().getClass());
        // Include inherited native/goal code. Avoid loading arbitrary linked
        // classes: this remains a bounded, read-only classfile survey.
        Set<Class<?>> expanded = new LinkedHashSet<>();
        for (Class<?> source : classes) {
            for (Class<?> cls=source; cls != null && cls != Object.class
                    && expanded.size() < 96; cls=cls.getSuperclass()) {
                expanded.add(cls);
            }
        }
        for (Class<?> cls : expanded) {
            var survey = NativeAiEffectSurvey.inspect(cls);
            if (!survey.bytesAvailable()) {
                candidates.add("UNSCANNABLE " + cls.getName() + ": " + survey.limitation());
                continue;
            }
            for (var effect : survey.candidates()) {
                candidates.add(cls.getSimpleName() + " -> "
                    + effect.className() + "." + effect.operation()
                    + " (" + effect.concern() + ")");
                if (candidates.size() >= 256) break;
            }
            if (candidates.size() >= 256) {
                candidates.add("TRUNCATED: additional effects may exist");
                break;
            }
        }
        return new Report(mob.getType().toString(), !mob.getBrain().isBrainDead(),
            goals(mob.goalSelector), goals(mob.targetSelector), methods,
            new ArrayList<>(candidates));
    }
}
