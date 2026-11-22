#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "${BASH_SOURCE[0]}")/.."
MC="${1:-26.2}"
case "$MC" in 26.2|26.3) ;; *) echo "Usage: scripts/BUILD_LINUX.sh [26.2|26.3]" >&2; exit 2;; esac
python3 scripts/build_doctor.py --mc "$MC"
python3 scripts/package-full.py --minecraft "$MC"
echo "Build and full ZIP ready in build/packages"
