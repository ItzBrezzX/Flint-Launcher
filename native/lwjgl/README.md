# Flint Android LWJGL native modules

Flint pins the first Android JNI build to **LWJGL 3.3.3**. The source is fetched from the official LWJGL repository at build time; it is not copied from another launcher.

The Android CMake targets currently produce:

- **liblwjgl.so** — LWJGL core JNI and libffi callback support
- **liblwjgl_glfw.so** — LWJGL GLFW JNI bindings, linked to Flint's own libglfw.so
- **liblwjgl_opengl.so** — LWJGL OpenGL JNI bindings
- **libglfw.so** — Flint's Android EGL/event-queue backend
- **libflint_native.so** — Flint JNI, JVM lifecycle and EGL bridge

prepare.sh checks out the pinned LWJGL tag and runs the official Ant template/source generator. build_libffi.sh builds static libffi 3.4.6 for the selected NDK ABI. patch_libffi.py guards calling-convention constants that do not exist on Android targets while retaining the ARM32 VFP ABI.

The CMake project builds these modules for the app's configured Android ABIs. CI verifies their ELF dependencies, JNI exports, and APK packaging.

## Current limitations

This is the baseline native set, not complete Minecraft runtime support. The following modules still need Android builds or compatible backends when a resolved Minecraft profile requires them:

- OpenAL / audio
- STB and image codecs
- FreeType
- TinyFD and other optional LWJGL modules

The OpenGL JNI module also does not provide desktop OpenGL by itself. Flint still needs to route LWJGL's GL entry points through its Android GL4ES/translation backend and verify them with an actual Minecraft render loop. A successful native build is not proof that Minecraft is playable.
