#!/usr/bin/env python3
"""Real NeoForge build gate; never confuses synthetic compilation with a real build."""
import argparse
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent

def check_java():
    java = Path(os.environ.get("JAVA_HOME", "")) / "bin" / "java" if os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java or not Path(java).exists():
        print("BLOCKED: java executable unavailable")
        return False
    result = subprocess.run([str(java), "-version"], text=True, capture_output=True, check=False)
    version = result.stderr + result.stdout
    print("JAVA:", version.splitlines()[0] if version else "unknown")
    major = re.search(r'version\s+"(\d+)', version)
    if not major or int(major.group(1)) != 25:
        print("BLOCKED: Minecraft 26.2 / NeoForge requires Java 25")
        return False
    return True

def gradle_command():
    # The complete official MDK wrapper is bundled in the source package. A
    # provided binary distribution can be selected to avoid a redundant download.
    supplied = os.environ.get("GRADLE_BIN")
    if supplied and Path(supplied).is_file():
        return [str(Path(supplied))]
    home = os.environ.get("GRADLE_HOME")
    if home:
        candidate = Path(home) / "bin" / ("gradle.bat" if os.name == "nt" else "gradle")
        if candidate.is_file(): return [str(candidate)]
    wrapper = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
    wrapper_jar = ROOT / "gradle" / "wrapper" / "gradle-wrapper.jar"
    if wrapper.is_file() and wrapper_jar.is_file():
        return ([str(wrapper)] if os.name == "nt" else ["sh", str(wrapper)])
    installed = shutil.which("gradle")
    if installed: return [installed]
    print("BLOCKED: no Gradle installation, GRADLE_BIN, GRADLE_HOME or complete wrapper")
    return None

def main():
    parser = argparse.ArgumentParser(description='Run an actual selected-version NeoForge Gradle build')
    parser.add_argument('--mc', choices=('26.2','26.3'), default='26.2')
    parser.add_argument('--offline', action='store_true', help='Resolve from existing Gradle caches only')
    args=parser.parse_args()
    if not (ROOT / f'versions/minecraft-{args.mc}.properties').is_file():
        print('BLOCKED: missing target profile')
        return 2
    if not check_java(): return 2
    command = gradle_command()
    if command is None: return 2
    flags=["--no-daemon"] + (["--offline"] if args.offline else []) + ["clean", "build", f"-PtargetMinecraft={args.mc}"]
    print("RUNNING REAL NEOFORGE BUILD:", " ".join(command + flags), flush=True)
    result = subprocess.run(command + flags, cwd=ROOT, text=True,
                            stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    output = result.stdout or ""
    print(output, end="" if output.endswith("\n") else "\n")
    if result.returncode == 0:
        print("PASS: real NeoForge Gradle build (runtime/in-game tests still required)")
        return 0
    if ("net.neoforged.moddev" in output and
        ("Plugin [id:" in output or "Could not resolve plugin artifact" in output)):
        print("BLOCKED: ModDevGradle plugin artifacts are not cached or reachable. "
              "Run this build once with internet access to populate Gradle dependencies.")
        return 2
    print("FAIL: real NeoForge build; inspect compiler/dependency errors above")
    return result.returncode

if __name__ == "__main__":
    sys.exit(main())
