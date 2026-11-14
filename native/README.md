# :native

Flint-owned JNI/C++ runtime and graphics bridge. The module is built with the Android NDK and CMake.

## Current implementation

- JNI smoke test.
- EGL/GLES2 pbuffer smoke test and driver capability probe.
- Mobile JRE loader that validates the expected `libjvm.so` and `libjli.so` files, loads the VM and JLI libraries, and resolves `JLI_Launch`.
- Native JRE status diagnostics exposed to Kotlin.
- Flint platform loader for public Android EGL/Vulkan system libraries and display metadata.
- Pinned GL4ES shared-library build for Android. The resulting `libGL.so` is intended to provide the desktop OpenGL compatibility entry points required by legacy LWJGL applications.

## Flint platform loader

`flint_platform.c` is Flint-owned code. It opens only public Android system libraries (`libEGL.so` and `libvulkan.so`) with the app's normal linker namespace and retains the handles for process lifetime. It does not use private linker APIs, namespace bypasses, injected hooks, or copied MojoExec source.

The pinned MojoLauncher GLFW Android fork is adapted at CMake configure time to call the Flint API. The adaptation is deliberately narrow and fails the configure step if the expected upstream source anchors change. GLFW remains a separately licensed third-party component; see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md). This does not make the GLFW fork Flint-authored.

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
- Flint's loader uses public Android library loading only. It intentionally does not reproduce MojoExec's private linker-namespace bypass or low-level driver hooks; components that require those behaviors need a separately reviewed, supported design.
- HotSpot libraries remain loaded for the lifetime of the Android app process. Switching Java runtimes requires restarting Flint.
- Flint still needs a complete game-process lifecycle, LWJGL2/LWJGL3 integration, input mapping, audio, and renderer validation against real game versions.
- The bridge does not yet provide a complete LWJGL2 compatibility runtime.

The next runtime milestone is to connect the Android game process and its real Surface lifecycle to the GLFW/EGL backend, then prove a GL clear/swap cycle on a physical device before attempting Minecraft 1.2.5.
