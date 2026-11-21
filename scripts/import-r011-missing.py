#!/usr/bin/env python3
"""Import historical R0.11 files safely without replacing the working GitHub build.

Run at the repository root:
  python3 scripts/import-r011-missing.py /path/to/FreakyFriday_R0_11_Full_Source.zip

Only files absent from the checkout are written. Existing GitHub sources, build
profiles, CI workflows and resources are never overwritten. This tool never
runs git add/commit/push; inspect the added files before committing.
"""
import argparse
import pathlib
import zipfile
import subprocess
import sys

SKIP_PREFIXES = ("logs/", ".gradle/", "build/", "caches/", "__pycache__/")
SKIP_NAMES = {"MANIFEST_PROJECT_SHA256.txt"}  # manifest refers to original ZIP snapshot

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("archive", type=pathlib.Path)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parents[1]
    added, preserved, excluded = [], [], []
    with zipfile.ZipFile(args.archive) as bundle:
        for info in bundle.infolist():
            name = info.filename
            if info.is_dir() or any(name.startswith(p) for p in SKIP_PREFIXES) or name in SKIP_NAMES:
                excluded.append(name)
                continue
            posix = pathlib.PurePosixPath(name)
            if posix.is_absolute() or ".." in posix.parts or "\\" in name:
                raise ValueError(f"Unsafe archive name: {name}")
            target = root.joinpath(*posix.parts)
            if target.exists():
                preserved.append(name)
                continue
            target.parent.mkdir(parents=True, exist_ok=True)
            with target.open("xb") as out:
                out.write(bundle.read(info))
            added.append(name)
    print(f"Restored {len(added)} missing files; preserved {len(preserved)} existing files; excluded {len(excluded)} generated/legacy items")
    for path in added: print("RESTORED", path)
    print("Existing GitHub sources were NOT replaced.")
    print("Review 'git status --short', run tests, then commit and push when approved.")
    return 0

if __name__ == "__main__":
    sys.exit(main())
