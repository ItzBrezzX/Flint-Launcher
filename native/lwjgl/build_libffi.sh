#!/usr/bin/env bash
set -euo pipefail
NDK="$1"
ABI="$2"
API="\${3:-26}"
OUT="$4"
VERSION="3.4.6"
TOOLCHAIN="\${NDK}/toolchains/llvm/prebuilt/linux-x86_64"
case "\${ABI}" in
    arm64-v8a) HOST="aarch64-linux-android"; COMPILER="\${HOST}\${API}-clang" ;;
    armeabi-v7a) HOST="armv7a-linux-androideabi"; COMPILER="\${HOST}\${API}-clang" ;;
    x86) HOST="i686-linux-android"; COMPILER="\${HOST}\${API}-clang" ;;
    x86_64) HOST="x86_64-linux-android"; COMPILER="\${HOST}\${API}-clang" ;;
    *) echo "Unsupported Android ABI: \${ABI}" >&2; exit 2 ;;
esac
PREFIX="\${OUT}/install"
SOURCE="\${OUT}/libffi-\${VERSION}"
BUILD="\${OUT}/build"
mkdir -p "\${OUT}"
if [ -s "\${PREFIX}/lib/libffi.a" ]; then
    echo "Using cached libffi for \${ABI}: \${PREFIX}/lib/libffi.a"
    exit 0
fi
ARCHIVE="\${OUT}/libffi-\${VERSION}.tar.gz"
if [ ! -s "\${ARCHIVE}" ]; then
    curl -fL --retry 3 "https://github.com/libffi/libffi/releases/download/v\${VERSION}/libffi-\${VERSION}.tar.gz" -o "\${ARCHIVE}"
fi
if [ ! -d "\${SOURCE}" ]; then
    tar -xzf "\${ARCHIVE}" -C "\${OUT}"
fi
mkdir -p "\${BUILD}"
cd "\${BUILD}"
CC="\${TOOLCHAIN}/bin/\${COMPILER}"
AR="\${TOOLCHAIN}/bin/llvm-ar"
RANLIB="\${TOOLCHAIN}/bin/llvm-ranlib"
SYSROOT="\${TOOLCHAIN}/sysroot"
env CC="\${CC}" AR="\${AR}" RANLIB="\${RANLIB}" CFLAGS="-fPIC -O2" \
    "\${SOURCE}/configure" --host="\${HOST}" --with-sysroot="\${SYSROOT}" \
    --disable-shared --enable-static --prefix="\${PREFIX}"
make -j"\${FLINT_BUILD_JOBS:-2}"
make install
test -s "\${PREFIX}/lib/libffi.a"
echo "Built libffi \${VERSION} for \${ABI}"
