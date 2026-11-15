#!/usr/bin/env python3
"""Verify the GLFW C ABI symbols exported by an Android APK's libglfw.so."""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("apk", type=Path)
    parser.add_argument("--abi", required=True)
    parser.add_argument("--readelf", default="readelf")
    args = parser.parse_args()

    header = Path(__file__).resolve().parents[1] / "src/main/cpp/flint_glfw_backend.h"
    if not header.is_file():
        print(f"GLFW header not found: {header}", file=sys.stderr)
        return 2

    declared = set(
        re.findall(r"FLINT_GLFW_API\s+[^;\n]*?\b(glfw[A-Za-z0-9_]*)\s*\(", header.read_text())
    )
    if not declared:
        print("No GLFW declarations found in the C ABI header.", file=sys.stderr)
        return 2

    apk_entry = f"lib/{args.abi}/libglfw.so"
    try:
        with zipfile.ZipFile(args.apk) as archive:
            binary = archive.read(apk_entry)
    except (OSError, KeyError, zipfile.BadZipFile) as error:
        print(f"Could not read {apk_entry} from {args.apk}: {error}", file=sys.stderr)
        return 2

    with tempfile.NamedTemporaryFile(suffix=".so") as native_file:
        native_file.write(binary)
        native_file.flush()
        result = subprocess.run(
            [args.readelf, "--dyn-syms", "--wide", native_file.name],
            check=False,
            capture_output=True,
            text=True,
        )
    if result.returncode != 0:
        print(result.stderr or "readelf failed", file=sys.stderr)
        return result.returncode or 2

    exported: set[str] = set()
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) < 8 or fields[3] != "FUNC" or fields[4] not in {"GLOBAL", "WEAK"}:
            continue
        if fields[5] != "DEFAULT" or fields[6] == "UND":
            continue
        exported.add(fields[-1].split("@", 1)[0])

    missing = sorted(declared - exported)
    if missing:
        print(f"{args.abi}: {len(missing)} declared GLFW symbols are not exported:")
        for name in missing:
            print(f"  - {name}")
        return 1

    print(f"{args.abi}: all {len(declared)} declared GLFW functions are exported.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
