#!/usr/bin/env python3
"""Verify JNI methods against the dynamic symbols in both packaged Flint bridges."""

from __future__ import annotations

import argparse
import re
import shutil
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
BRIDGES = (
    (
        ROOT / "native/src/main/java/com/flintlauncher/android/nativebridge/NativeBridge.kt",
        "Java_com_flintlauncher_android_nativebridge_NativeBridge_",
        "libflint_native.so",
    ),
    (
        ROOT / "native/src/main/java/com/flintlauncher/android/nativebridge/GlfwBridge.kt",
        "Java_com_flintlauncher_android_nativebridge_GlfwBridge_",
        "libflint_game_glfw.so",
    ),
)


def declared_methods(source_path: Path) -> list[str]:
    source = source_path.read_text(encoding="utf-8")
    methods = re.findall(
        r"^\s*external\s+fun\s+([A-Za-z_]\w*)\s*\(",
        source,
        flags=re.MULTILINE,
    )
    if not methods:
        raise RuntimeError(f"No external JNI methods found in {source_path}")

    duplicates = sorted({name for name in methods if methods.count(name) > 1})
    if duplicates:
        raise RuntimeError(
            f"Overloaded JNI methods in {source_path} need explicit long-name handling: "
            + ", ".join(duplicates)
        )
    return methods


def exported_symbols(elf_path: Path) -> set[str]:
    readelf = shutil.which("readelf")
    if readelf is None:
        raise RuntimeError("readelf is required to inspect JNI dynamic symbols")

    result = subprocess.run(
        [readelf, "--dyn-syms", "--wide", str(elf_path)],
        check=False,
        capture_output=True,
        text=True,
    )
    if result.returncode != 0:
        raise RuntimeError(f"readelf failed for {elf_path}: {result.stderr.strip()}")

    names: set[str] = set()
    for line in result.stdout.splitlines():
        fields = line.split()
        if len(fields) < 8 or not fields[0].endswith(":"):
            continue
        symbol_type, visibility, section_index, name = (
            fields[3],
            fields[5],
            fields[6],
            fields[-1],
        )
        if symbol_type == "FUNC" and visibility == "DEFAULT" and section_index != "UND":
            names.add(name.split("@", 1)[0])
    return names


def verify(apk: Path, abis: list[str]) -> None:
    bridges = [
        (source, prefix, library, {prefix + method for method in declared_methods(source)})
        for source, prefix, library in BRIDGES
    ]
    failures: list[str] = []

    with zipfile.ZipFile(apk) as archive, tempfile.TemporaryDirectory() as temp:
        for abi in abis:
            for source, prefix, library, expected in bridges:
                entry = f"lib/{abi}/{library}"
                try:
                    info = archive.getinfo(entry)
                except KeyError:
                    failures.append(f"{abi}: APK is missing {entry}")
                    continue
                if info.file_size <= 0:
                    failures.append(f"{abi}: {entry} is empty")
                    continue

                elf_path = Path(temp) / f"{library}-{abi}.so"
                with archive.open(entry) as source_stream, elf_path.open("wb") as target:
                    shutil.copyfileobj(source_stream, target)

                try:
                    actual = exported_symbols(elf_path)
                except RuntimeError as error:
                    failures.append(f"{abi}: {error}")
                    continue

                missing = sorted(expected - actual)
                if missing:
                    failures.append(
                        f"{abi}: {library} is missing JNI exports: " + ", ".join(missing)
                    )
                else:
                    print(
                        f"{abi}: verified {len(expected)} JNI exports "
                        f"for {library}"
                    )

    if failures:
        raise RuntimeError("\n".join(failures))


def main() -> int:
    parser = argparse.ArgumentParser(
        description="Verify NativeBridge and GlfwBridge JNI exports in an Android APK."
    )
    parser.add_argument("apk", type=Path, help="APK produced by the Android build")
    parser.add_argument(
        "--abis",
        nargs="+",
        required=True,
        help="Android ABIs to verify (for example arm64-v8a armeabi-v7a)",
    )
    args = parser.parse_args()

    if not args.apk.is_file():
        print(f"APK not found: {args.apk}", file=sys.stderr)
        return 2

    try:
        verify(args.apk, args.abis)
    except (OSError, RuntimeError, zipfile.BadZipFile) as error:
        print(f"JNI export verification failed: {error}", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
