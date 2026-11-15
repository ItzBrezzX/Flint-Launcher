#!/usr/bin/env python3
"""Guard libffi ABI constants unavailable on Android targets."""
from pathlib import Path
import re
import sys

if len(sys.argv) != 2:
    raise SystemExit("usage: patch_libffi.py <generated-LibFFI.c>")

path = Path(sys.argv[1])
source = path.read_text(encoding="utf-8")

def replace_constant(name: str, condition: str, supported_value: str) -> None:
    global source
    pattern = re.compile(rf"(?m)^([ \t]*)return \(jint\){re.escape(name)};$")
    def replacement(match):
        indent = match.group(1)
        return (
            f"{indent}#if {condition}\n"
            f"{indent}return (jint){supported_value};\n"
            f"{indent}#else\n"
            f"{indent}return 0;\n"
            f"{indent}#endif"
        )
    if pattern.search(source):
        source, count = pattern.subn(replacement, source)
        if count != 1:
            raise SystemExit(f"expected one {name} JNI constant, patched {count}")

for name in (
    "FFI_WIN64", "FFI_GNUW64", "FFI_UNIX64", "FFI_EFI64",
    "FFI_STDCALL", "FFI_THISCALL", "FFI_FASTCALL", "FFI_MS_CDECL",
    "FFI_PASCAL", "FFI_REGISTER",
):
    replace_constant(name, "!defined(__ANDROID__)", name)

replace_constant(
    "FFI_VFP",
    "defined(__ANDROID__) && defined(__arm__) && !defined(__aarch64__)",
    "FFI_VFP",
)
path.write_text(source, encoding="utf-8")
