#!/usr/bin/env python3
"""Compare an authoritative R0.11 full-source ZIP against a checked-out repository.

Run locally with: python3 scripts/audit-source-import.py path/to/FreakyFriday_R0_11_Full_Source.zip
The script does not alter the archive or Git checkout. It ignores downloaded
dependencies and build outputs, but does not silently ignore source/tests/docs.
"""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import subprocess
import sys
import zipfile

EXCLUDED_PARTS = {
    ".git", ".gradle", ".idea", ".vscode", "__pycache__", "build",
    "out", "target", "run", "runs", "logs", "caches", "cache",
}
EXCLUDED_SUFFIXES = {".class", ".pyc", ".log"}
HISTORICAL_CHECKS = (
    "self_audit.py",
    "test-mojang-api.py",
    "test-mojang-attributes.py",
    "inspect-neoforge-api.py",
)

def include(name):
    p = PurePosixPath(name)
    return (
        bool(p.parts) and
        not any(x in EXCLUDED_PARTS for x in p.parts) and
        p.suffix.lower() not in EXCLUDED_SUFFIXES and
        not name.endswith("/") and
        not name.startswith("/")
    )

def sha256(data):
    return hashlib.sha256(data).hexdigest()

def repo_files(root):
    completed = subprocess.run(
        ["git", "ls-files", "-z"], cwd=root, check=True, stdout=subprocess.PIPE
    )
    return {
        path: sha256((root / path).read_bytes())
        for path in completed.stdout.decode("utf-8", "surrogateescape").split("\0")
        if path and include(path) and (root / path).is_file()
    }

def zip_files(archive):
    entries = {}
    with zipfile.ZipFile(archive) as z:
        names = [n for n in z.namelist() if include(n)]
        # Handle the common ZIP packaging style with one enclosing project directory.
        first = {PurePosixPath(n).parts[0] for n in names}
        root = next(iter(first)) if len(first) == 1 and all("/" in n for n in names) else None
        for name in names:
            normalized = name[len(root) + 1:] if root else name
            if not normalized or ".." in PurePosixPath(normalized).parts:
                raise ValueError(f"Unsafe ZIP path: {name}")
            if normalized in entries:
                raise ValueError(f"Duplicate ZIP path: {normalized}")
            entries[normalized] = sha256(z.read(name))
    return entries

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("source_zip", type=Path)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()
    original = zip_files(args.source_zip)
    current = repo_files(args.root)
    missing = sorted(set(original) - set(current))
    different = sorted(n for n in original.keys() & current.keys() if original[n] != current[n])
    new = sorted(set(current) - set(original))
    historical = {
        name: {
            "in_original": any(p == name or p.endswith("/" + name) for p in original),
            "in_repo": any(p == name or p.endswith("/" + name) for p in current),
        }
        for name in HISTORICAL_CHECKS
    }
    result = {
        "source_zip": str(args.source_zip),
        "original_files": len(original),
        "repo_files": len(current),
        "missing_from_repo": missing,
        "changed_from_original": different,
        "repo_only_files": new,
        "historical_checks": historical,
    }
    rendered = json.dumps(result, indent=2) + "\n"
    if args.output:
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(rendered, encoding="utf-8")
    print(rendered)
    if missing or different:
        print("FAIL: archive reconciliation incomplete", file=sys.stderr)
        return 1
    print("PASS: every retained original source-file hash matches")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
