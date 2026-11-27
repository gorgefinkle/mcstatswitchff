#!/usr/bin/env python3
"""Offline audit that Freaky Friday is deliberately pinned to Minecraft 26.2.

Dormant later-version source/profile files may be retained for future porting,
but the current build must not select, compile, package, or test them.
"""
from pathlib import Path
import tomllib

ROOT = Path(__file__).resolve().parent.parent
MC = "26.2"
profile_path = ROOT / "versions" / f"minecraft-{MC}.properties"
assert profile_path.is_file(), f"missing active profile: {profile_path}"
profile = dict(
    line.strip().split("=", 1)
    for line in profile_path.read_text().splitlines()
    if line.strip() and not line.startswith("#")
)
assert profile["minecraft_version"] == MC
assert profile["neo_version"].startswith(MC + ".0.")
assert profile["java_version"] == "25"

platform = ROOT / "src/platform/minecraft-26.2/java/dev/freakyfriday/compat/PlatformHooks.java"
assert platform.is_file(), f"missing 26.2 platform integration: {platform}"
assert "addBookToCreativeTab" in platform.read_text()

build = (ROOT / "build.gradle").read_text()
assert "def selectedMinecraft = '26.2'" in build
assert "src/platform/minecraft-26.2/java" in build
assert "freakyfriday-mc26.2-neoforge" in build
assert "providers.gradleProperty('targetMinecraft')" in build
assert "requested targetMinecraft=" in build
assert "src/platform/minecraft-${selectedMinecraft}/java" not in build
assert "freakyfriday-mc${selectedMinecraft}-neoforge" not in build

workflow = (ROOT / ".github/workflows/build.yml").read_text()
assert "NeoForge Minecraft 26.2" in workflow
assert "freakyfriday-mc26.2-build" in workflow
assert "matrix:" not in workflow
assert "26.3" not in workflow

assert (ROOT / "src/main/java/dev/freakyfriday/core/CategoryExchange.java").is_file()
assert not list((ROOT / "src/platform").rglob("CategoryExchange.java")), "shared core may not be duplicated"

metadata = (ROOT / "src/main/resources/META-INF/neoforge.mods.toml").read_text()
expanded = (
    metadata.replace("${ff_mod_version}", "0.13.2-alpha")
    .replace("${ff_mc_version}", MC)
    .replace("${ff_neo_version}", profile["neo_version"])
)
info = tomllib.loads(expanded)
assert info["mods"][0]["version"] == "0.13.2-alpha"
deps = {entry["modId"]: entry["versionRange"] for entry in info["dependencies"]["freakyfriday"]}
assert deps["minecraft"] == f"[{MC}]"
assert deps["neoforge"] == f'[{profile["neo_version"]},)'

assert "freakyfriday-mc26.2-neoforge" in build
print(f"PASS active target is Minecraft {MC} / NeoForge {profile['neo_version']} / Java 25")
print("PASS only the 26.2 platform source tree is compiled")
print("PASS CI builds and verifies only the 26.2 artifact")
print("PASS exact Minecraft and NeoForge metadata expansion")
print("NOTE later-version profiles/source, if present, are dormant and excluded")
