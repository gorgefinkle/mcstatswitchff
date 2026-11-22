#!/usr/bin/env python3
"""Offline contract test: extracted P07C source can reproduce full releases.

Uses a deliberately FAKE ZIP-format JAR in a temporary directory. Passing
does not certify Java/NeoForge compilation or a playable mod.
"""
import importlib.util
import io
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import zipfile

ROOT=Path(__file__).resolve().parent.parent
spec=importlib.util.spec_from_file_location("freakyfriday_packager",ROOT/"scripts/package-full.py")
pkg=importlib.util.module_from_spec(spec)
spec.loader.exec_module(pkg)

def main():
    expected=pkg.select_source_files()
    assert len(expected)>=150, f"unexpectedly partial source set: {len(expected)}"
    assert Path("src/main/java/dev/freakyfriday/ClientOccupiedBodyUI.java") in expected
    assert Path("gradle/wrapper/gradle-wrapper.jar") in expected
    assert all(not (ROOT/p).is_symlink() for p in expected)
    assert all("build" not in p.parts and ".git" not in p.parts for p in expected)
    with tempfile.TemporaryDirectory(prefix="freakyfriday-packager-test-") as temp:
        root=Path(temp)
        for p in expected:
            dest=root/p
            dest.parent.mkdir(parents=True,exist_ok=True)
            shutil.copyfile(ROOT/p,dest)
        # This deliberately has no .git metadata. The packager MUST still
        # enumerate all source files and produce the normal ZIP structure.
        assert not (root/".git").exists()
        jar=root/"build/libs/freakyfriday-mc26.2-neoforge-0.15.10-alpha.jar"
        jar.parent.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(jar,"w") as dummy:
            dummy.writestr("UNCOMPILED_TEST_FIXTURE.txt","NOT A REAL MINECRAFT MOD")
        run=subprocess.run(
            [sys.executable,str(root/"scripts/package-full.py"),"--minecraft","26.2"],
            cwd=root,text=True,capture_output=True,check=False)
        assert run.returncode==0,(run.stdout,run.stderr)
        outer=next((root/"build/packages").glob("*_FULL.zip"))
        with zipfile.ZipFile(outer) as archive:
            assert archive.testzip() is None
            assert len([n for n in archive.namelist() if n.endswith(".jar")])==1
            source=next(n for n in archive.namelist() if n.endswith("_SOURCE.zip"))
            with zipfile.ZipFile(io.BytesIO(archive.read(source))) as nested:
                assert nested.testzip() is None
                names=set(nested.namelist())
                assert names=={p.as_posix() for p in expected},(
                    sorted(names-{p.as_posix() for p in expected}),
                    sorted({p.as_posix() for p in expected}-names))
    print("PASS extracted ZIP source repackages standalone; dummy JAR is TEST FIXTURE only")

if __name__=="__main__":
    main()
