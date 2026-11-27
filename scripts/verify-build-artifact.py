#!/usr/bin/env python3
"""Fail CI if a successful Gradle invocation did not produce a usable mod archive."""
import argparse
import hashlib
from pathlib import Path
import zipfile

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft", required=True, choices=("26.2", "26.3"))
    parser.add_argument("--output", default="build/reports/freakyfriday")
    args = parser.parse_args()
    jars = sorted(p for p in Path("build/libs").glob("*.jar") if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
    if len(jars) != 1:
        raise SystemExit(f"Expected exactly one main mod JAR, found: {[str(p) for p in jars]}")
    jar = jars[0]
    if f"mc{args.minecraft}" not in jar.name:
        raise SystemExit(f"Wrong Minecraft variant: {jar}")
    required = {
        "META-INF/neoforge.mods.toml",
        "META-INF/accesstransformer.cfg",
        "dev/freakyfriday/FreakyFriday.class",
        "dev/freakyfriday/AttributeCategorySwapper.class",
        "dev/freakyfriday/mind/MindLedger.class",
        "dev/freakyfriday/mind/NativeBrainSwap.class",
        "dev/freakyfriday/mind/GoalMindRuntime.class",
        "dev/freakyfriday/mind/GoalSelectorState.class",
        "dev/freakyfriday/mind/DynamicObjectGraph.class",
        "dev/freakyfriday/mind/GoalTransferAdapters.class",
        "dev/freakyfriday/mind/GoalTransferAdapters$RecipientRemoveBlockGoal.class",
        "dev/freakyfriday/mind/RecipientAttackIntentGoal.class",
        "dev/freakyfriday/mind/MindSwapSessions.class",
        "dev/freakyfriday/CategoryPickerPayload.class",
        "dev/freakyfriday/CategoryPickerNetworking.class",
        "assets/freakyfriday/lang/en_us.json",
        "data/freakyfriday/recipe/book.json",
    }
    with zipfile.ZipFile(jar) as archive:
        failed = archive.testzip()
        if failed:
            raise SystemExit(f"Corrupt ZIP member: {failed}")
        missing = required - set(archive.namelist())
        if missing:
            raise SystemExit(f"Missing required mod entries: {sorted(missing)}")
        access = archive.read("META-INF/accesstransformer.cfg").decode("utf-8")
        required_scheduler_access = (
            "public net.minecraft.world.entity.ai.goal.GoalSelector lockedFlags",
            "public net.minecraft.world.entity.ai.goal.GoalSelector disabledFlags",
            "public net.minecraft.world.entity.ai.goal.WrappedGoal isRunning",
            "public net.minecraft.world.entity.ai.goal.MeleeAttackGoal speedModifier",
            "public net.minecraft.world.entity.ai.goal.MeleeAttackGoal followingTargetEvenIfNotSeen",
            "public net.minecraft.world.entity.ai.goal.RangedBowAttackGoal speedModifier",
            "public net.minecraft.world.entity.ai.goal.RangedBowAttackGoal attackIntervalMin",
            "public net.minecraft.world.entity.ai.goal.RangedBowAttackGoal attackRadiusSqr",
            "public net.minecraft.world.entity.ai.goal.RemoveBlockGoal blockToRemove",
            "public net.minecraft.world.entity.ai.goal.MoveToBlockGoal verticalSearchRange",
        )
        missing_scheduler_access = [entry for entry in required_scheduler_access if entry not in access]
        if missing_scheduler_access:
            raise SystemExit(f"GoalSelector scheduler access transformers missing: {missing_scheduler_access}")
        if "public net.minecraft.world.entity.LivingEntity brain" not in access:
            raise SystemExit("Whole-Brain access transformer missing or incorrect")
        metadata = archive.read("META-INF/neoforge.mods.toml").decode("utf-8")
        if "${ff_" in metadata:
            raise SystemExit("Unexpanded Gradle metadata placeholder")
        if f'versionRange="[{args.minecraft}]"' not in metadata:
            raise SystemExit(f"Minecraft metadata does not target {args.minecraft}")
    digest = hashlib.sha256(jar.read_bytes()).hexdigest()
    out = Path(args.output)
    out.mkdir(parents=True, exist_ok=True)
    (out / f"SHA256-mc{args.minecraft}.txt").write_text(f"{digest}  {jar.name}\n", encoding="utf-8")
    (out / f"BUILD-mc{args.minecraft}.txt").write_text(
        f"minecraft={args.minecraft}\njar={jar.name}\nsha256={digest}\n"
        "verification=zip-integrity,mod-metadata,core-classes,resource-entries\n"
        "runtime-smoke-test=not-performed\n", encoding="utf-8"
    )
    print(f"Verified {jar} ({jar.stat().st_size} bytes), sha256={digest}")

if __name__ == "__main__":
    main()
