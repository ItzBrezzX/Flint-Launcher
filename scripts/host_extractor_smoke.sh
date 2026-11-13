#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
f="$ROOT/runtime/src/main/kotlin/com/flintlauncher/android/runtime/SafeTarGzExtractor.kt"
test -f "$f"
grep -q 'Path traversal rejected' "$f"
grep -q 'Absolute path rejected' "$f"
grep -q 'MAX_TOTAL_BYTES' "$f"
echo "host_extractor_smoke: OK (source-level checks only)"
