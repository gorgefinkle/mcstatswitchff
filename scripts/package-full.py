#!/usr/bin/env python3
"""Versioned single ZIP: exact one main JAR + nested complete buildable sources.

Never package an old JAR, generated world, Gradle cache, secrets or test log.
Artifact is built only after the current version-specific JAR integrity gate.
"""
from pathlib import Path
from hashlib import sha256
import argparse
import io
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent.parent
TAG = "R0_15_10_OccupiedHealthPreviewFinalP07C"
SOURCE_PREFIXES = ("src/", "scripts/", "versions/", "gradle/", "docs/")
SOURCE_FILES = {
    "build.gradle", "settings.gradle", "gradle.properties",
    "gradlew", "gradlew.bat", "README.md", "LICENSE",
    ".gitignore", "AGENTS.md",
}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--minecraft", choices=("26.2","26.3"),required=True)
    opts = parser.parse_args()
    jars = [p for p in (ROOT/"build/libs").glob("*.jar")
            if not p.name.endswith(("-sources.jar","-javadoc.jar"))]
    if len(jars)!=1 or "mc"+opts.minecraft not in jars[0].name:
        raise SystemExit("Expected exactly one correct, current version-specific mod JAR")
    jar=jars[0]
    digest=sha256(jar.read_bytes()).hexdigest()
    listing=subprocess.check_output(["git","ls-files","-z"],cwd=ROOT)
    tracked=[Path(p.decode("utf-8")) for p in listing.split(b"\0") if p]
    source_files=sorted(
        (p for p in tracked
         if (str(p).startswith(SOURCE_PREFIXES) or str(p) in SOURCE_FILES)
         and (ROOT/p).is_file()),
        key=lambda p:p.as_posix(),
    )
    required={
        "build.gradle", "settings.gradle", "gradlew",
        "src/main/java/dev/freakyfriday/FreakyFriday.java",
        "src/main/java/dev/freakyfriday/mind/NativeAiWorkerServer.java",
        "src/main/java/dev/freakyfriday/mind/PlayerMobPossession.java",
        "src/main/java/dev/freakyfriday/ClientPossession.java",
        "src/main/java/dev/freakyfriday/ClientOccupiedBodyUI.java",
        "src/main/java/dev/freakyfriday/mind/PlayerMobGameTests.java",
        "src/main/java/dev/freakyfriday/mind/PlayerPlayerExchange.java",
        "src/main/java/dev/freakyfriday/mind/PlayerPlayerGameTests.java",
        "src/main/java/dev/freakyfriday/mind/CapturedIntentWireCodec.java",
        "src/main/java/dev/freakyfriday/mind/DetachedProbeGameTests.java",
        "scripts/test-native-worker-e2e.py",
        "scripts/test-core.sh",
        "versions/minecraft-26.2.properties",
        "versions/minecraft-26.3.properties",
    }
    missing=required-set(p.as_posix() for p in source_files)
    if missing:
        raise SystemExit("Buildable complete source missing: "+", ".join(sorted(missing)))
    source_zip_io=io.BytesIO()
    with zipfile.ZipFile(source_zip_io,"w",zipfile.ZIP_DEFLATED,compresslevel=8) as archive:
        for path in source_files:
            archive.write(ROOT/path,path.as_posix())
    source_bytes=source_zip_io.getvalue()
    package_stem=f"FreakyFriday_{TAG}_mc{opts.minecraft}"
    folder=ROOT/"build/packages"
    folder.mkdir(parents=True,exist_ok=True)
    outer=folder/(package_stem+"_FULL.zip")
    description=(
        f"Freaky Friday {TAG} for Minecraft {opts.minecraft}\n"
        "ONE currently built version-specific NeoForge JAR and ONE complete source archive.\n"
        "Install JAR under the server/client mods directory for your exact Minecraft/NeoForge version.\n"
        "The native AI worker is EXPERIMENTAL and the first stage observes native\n"
        "target/navigation decisions in a separate disposable NeoForge server.\n"
        "It is NOT yet generic world-effect interception or a complete mind swap.\n"
        "Player command-body routing is included in the P05 reciprocal player session;\n"
        "real-client camera, combat, inventory and named-player lookup remain unproven.\n"
        "Do not run untrusted modded code in the same OS security domain as\n"
        "production worlds. See source/docs and scripted CI validation.\n"
    )
    manifest=(
        f"version={TAG}\nminecraft={opts.minecraft}\n"
        f"jar={jar.name}\nsha256={digest}\n"
        f"source_zip_sha256={sha256(source_bytes).hexdigest()}\n"
        f"source_file_count={len(source_files)}\n"
    )
    with zipfile.ZipFile(outer,"w",zipfile.ZIP_DEFLATED,compresslevel=8) as archive:
        archive.write(jar,jar.name)
        archive.writestr(package_stem+"_SOURCE.zip",source_bytes)
        archive.writestr("SHA256.txt",manifest)
        archive.writestr("README_INSTALL.txt",description)
    with zipfile.ZipFile(outer) as archive:
        assert archive.testzip() is None
        jar_members=[n for n in archive.namelist() if n.endswith(".jar")]
        assert jar_members==[jar.name],jar_members
        nested=[n for n in archive.namelist() if n.endswith("_SOURCE.zip")]
        assert len(nested)==1
        with zipfile.ZipFile(io.BytesIO(archive.read(nested[0]))) as src:
            assert src.testzip() is None
            assert required.issubset(set(src.namelist()))
    print("FF-FULL-PACKAGE PASS",outer.name,
          "one_current_jar=true source_files=",len(source_files),
          "jar_sha256=",digest)

if __name__ == "__main__":
    main()
