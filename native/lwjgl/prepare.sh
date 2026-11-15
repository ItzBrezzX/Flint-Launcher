#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
WORK="${ROOT}/_work"
SOURCE="${WORK}/lwjgl3-3.3.3"
GLFW_SOURCE="${WORK}/glfw-3.3.8"
mkdir -p "${WORK}"
exec 9>"${WORK}/prepare.lock"
flock 9
if [ ! -d "${SOURCE}/.git" ]; then
    rm -rf "${SOURCE}"
    git clone --depth 1 --branch 3.3.3 https://github.com/LWJGL/lwjgl3.git "${SOURCE}"
fi
if [ ! -f "${GLFW_SOURCE}/include/GLFW/glfw3.h" ]; then
    rm -rf "${GLFW_SOURCE}"
    git clone --depth 1 --branch 3.3.8 https://github.com/glfw/glfw.git "${GLFW_SOURCE}"
fi
test -f "${GLFW_SOURCE}/include/GLFW/glfw3.h"

GENERATED="${SOURCE}/modules/lwjgl/glfw/src/generated/c"
if [ ! -d "${GENERATED}" ] || [ -z "$(find -L "${GENERATED}" -maxdepth 1 -name '*.c' -print -quit 2>/dev/null)" ]; then
    if ! command -v ant >/dev/null 2>&1; then
        echo "Apache Ant is required to generate LWJGL JNI sources." >&2
        exit 2
    fi
    (
        cd "${SOURCE}"
        export LWJGL_BUILD_TYPE="release/3.3.3"
        ant -q compile-templates generate
    )
fi
python3 "${ROOT}/patch_libffi.py" "${SOURCE}/modules/lwjgl/core/src/generated/c/org_lwjgl_system_libffi_LibFFI.c"
echo "LWJGL 3.3.3 JNI sources prepared at ${SOURCE}; GLFW 3.3.8 headers at ${GLFW_SOURCE}"
