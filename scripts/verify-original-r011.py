#!/usr/bin/env python3
"""Verify that the original R0.11 ZIP and its Git archive branch match byte-for-byte.

Requires Git and the original ZIP only; no JDK, Gradle or dependency caches.
Run from a clone:
  python3 scripts/verify-original-r011.py FreakyFriday_R0_11_Full_Source.zip
"""
import argparse
import hashlib
from pathlib import Path, PurePosixPath
import subprocess
import sys
import zipfile

def blob_sha(data):
    header = b"blob " + str(len(data)).encode("ascii") + b"\0"
    return hashlib.sha1(header + data).hexdigest()

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("--ref", default="archive/r0.11-original")
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    args = parser.parse_args()

    tree = subprocess.run(
        ["git", "ls-tree", "-rz", "--full-tree", args.ref],
        cwd=args.root, check=True, stdout=subprocess.PIPE,
    ).stdout
    actual = {}
    for record in tree.split(b"\0"):
        if not record:
            continue
        metadata, path = record.split(b"\t", 1)
        mode, kind, sha = metadata.split()
        if kind == b"blob":
            actual[path.decode("utf-8")] = sha.decode("ascii")

    original = {}
    with zipfile.ZipFile(args.source_zip) as z:
        for entry in z.infolist():
            if entry.is_dir():
                continue
            name = entry.filename
            p = PurePosixPath(name)
            if p.is_absolute() or ".." in p.parts:
                raise ValueError(f"Unsafe ZIP path: {name}")
            if name in original:
                raise ValueError(f"Duplicate ZIP entry: {name}")
            original[name] = blob_sha(z.read(entry))

    missing = sorted(set(original) - set(actual))
    extra = sorted(set(actual) - set(original))
    differing = sorted(
        name for name in original.keys() & actual.keys()
        if original[name] != actual[name]
    )
    print(f"Original ZIP files: {len(original)}")
    print(f"Archive branch files: {len(actual)}")
    print(f"Matching Git blob hashes: {sum(original.get(n) == actual.get(n) for n in original)}")
    for label, names in (
        ("MISSING", missing), ("EXTRA", extra), ("DIFFERENT", differing)
    ):
        for name in names:
            print(f"{label}: {name}")
    if missing or extra or differing:
        print("FAIL: archive branch is not an exact source snapshot", file=sys.stderr)
        return 1
    print(f"PASS: {len(original)}/{len(original)} original R0.11 files match {args.ref} byte-for-byte")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
