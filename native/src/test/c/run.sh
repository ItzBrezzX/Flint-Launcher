#!/usr/bin/env bash
# Builds and runs the GLFW shim tests on the host with sanitizers.
set -euo pipefail
here="$(cd "$(dirname "$0")" && pwd)"
src="$here/../../main/cpp/glfw"
out="${TMPDIR:-/tmp}/flint_glfw_shim_test"
${CC:-gcc} -std=c11 -g -O1 -Wall -Wextra -Werror -D_GNU_SOURCE -pthread \
  -fsanitize=address,undefined -fno-sanitize-recover=undefined \
  "$here/glfw_shim_test.c" "$src/flint_glfw_core.c" "$src/flint_glfw_api.c" -o "$out"
"$out"
