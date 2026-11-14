# :native

Flint-owned JNI/C++ runtime and graphics bridge. The module is built with the Android NDK and CMake.

## Current implementation

- JNI smoke test.
- EGL/GLES2 pbuffer smoke test and driver capability probe.
- Mobile JRE loader that validates the expected `libjvm.so` and `libjli.so` files, loads the VM and JLI libraries, and resolves `JLI_Launch`.
- Native JRE status diagnostics exposed to Kotlin.
- Pinned GL4ES shared-library build for Android. The resulting `libGL.so` is intended to provide the desktop OpenGL compatibility entry points required by legacy LWJGL applications.

## GL4ES dependency

Flint fetches GL4ES from the upstream repository at the immutable revision:

- Repository: https://github.com/ptitSeb/gl4es
- Revision: `8b1a3da5d2331d0f88b70be25963182d46eed857`
- License: MIT; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- Android build: GLES 2 backend, no X11/GBM, Android logging enabled.
- Android output name: `libGL.so` (rather than upstream's Unix `libGL.so.1`).

The dependency is fetched at CMake configure time; its source is not copied or modified in this repository. The upstream license and attribution are retained.

## Explicit limitations

- A successful JRE load only proves that the selected native libraries loaded and the JLI entry point resolved. It does not prove that Java can execute a class, that Minecraft can start, or that LWJGL can create a window.
- A successful GL4ES compile only proves that the compatibility library was built. It does not prove that a game can create a valid Android EGL window surface or render frames.
- HotSpot libraries remain loaded for the lifetime of the Android app process. Switching Java runtimes requires restarting Flint.
- Flint still needs its own Android `Surface` / `ANativeWindow` lifecycle, EGL context ownership, GL symbol loading for the selected LWJGL line, input mapping, and persistent game-session lifecycle.
- The bridge does not yet provide game audio or a complete LWJGL2 compatibility runtime.
- No Amethyst source files are included in this module.

The next runtime milestone is to connect a real Android `Surface` to an EGL window surface, make that context current on the game thread, and prove a GL clear/swap cycle before attempting Minecraft 1.2.5.
