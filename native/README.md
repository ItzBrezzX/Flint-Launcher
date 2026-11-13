# :native

Flint-owned JNI/C++ runtime and graphics bridge. The module is built with the Android NDK and CMake.

## Current implementation

- JNI smoke test.
- EGL/GLES2 pbuffer smoke test and driver capability probe.
- Mobile JRE loader that validates the expected `libjvm.so` and `libjli.so` files, loads the VM and JLI libraries, and resolves `JLI_Launch`.
- Native JRE status diagnostics exposed to Kotlin.

## Explicit limitations

- A successful JRE load only proves that the selected native libraries loaded and the JLI entry point resolved. It does not prove that Java can execute a class, that Minecraft can start, or that LWJGL can create a window.
- HotSpot libraries remain loaded for the lifetime of the Android app process. Switching Java runtimes requires restarting Flint.
- The bridge does not yet provide an Android window/surface, input, audio, or LWJGL compatibility implementation.
- No Amethyst source files are included in this module.

The next runtime milestone is a controlled Java 8 ARM64 execution probe, followed by the Android graphics/window bridge.
