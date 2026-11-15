# Flint Android native runtime

The `:native` module builds the Android/Bionic native libraries used by the
launcher and the Minecraft runtime. All native binaries are built for the
configured Android ABIs; Linux/GLIBC binaries from Mojang's desktop native
archives are not accepted as Android libraries.

## Native libraries

| Library | Purpose |
|---|---|
| `libflint_native.so` | JNI entry points for JLI, GL4ES setup, native environment configuration, launch logging, and the renderer preview |
| `libGL.so` | Pinned GL4ES build used as the OpenGL compatibility layer over Android EGL/GLES |
| `libglfw.so` | Flint's C++ GLFW-compatible preview backend and native input FIFO from the main runtime |
| `libflint_game_glfw.so` | Separate C GLFW backend used by `FlintGameActivity` and the Android-built LWJGL game process |
| `liblwjgl.so`, `liblwjgl_opengl.so` | Android/Bionic LWJGL core and OpenGL JNI libraries built from the pinned LWJGL 3.3.3 sources |

The two GLFW libraries intentionally have different names. They are separate
implementations with different owners and JNI contracts. Do not rename either
one to `libglfw.so` or combine their source files into a single shared object:
that would produce duplicate GLFW symbols and break one of the JNI bridges.

## Minecraft game surface and GLFW path

`FlintGameActivity` runs in the separate `:game` process. Its Android
Surface is published through `GlfwBridge` into `libflint_game_glfw.so`.
The game thread owns GLFW initialization, EGL context creation, event polling,
buffer swaps, and teardown. Android input callbacks only enqueue events; the
game thread dispatches them through the GLFW callbacks.

`LwjglAndroid.jvmArguments()` selects the same game backend through
`org.lwjgl.glfw.libname`. The per-game native installer copies the required
APK libraries and validates their ELF class and machine before launch.
Desktop Linux `.so` files are excluded from the extracted Minecraft natives.

## Renderer preview

`GL4ESSurfaceView` uses the separate `libglfw.so` C++ preview backend with
the `NativeBridge` JNI API. It exercises Android surface creation, EGL/GL4ES,
the GLFW-compatible input FIFO, rendering, and buffer swapping. This preview
does not substitute for verification that a real Minecraft session reaches
the title screen.

## Build and verification

The CMake build fetches GL4ES at its pinned Git revision and builds the
Android/Bionic LWJGL core and OpenGL natives. The LWJGL Android workflow also
builds the version-matched Java jars and auxiliary audio/STB natives used by
the app packaging workflow.

Useful checks:

- `bash native/src/test/c/run.sh` — host tests for the game GLFW event queue
  and callbacks.
- `python3 native/tools/verify_glfw_exports.py <apk> --abi arm64-v8a` —
  checks the preview GLFW exports in an APK.
- Android CI builds the APK, checks native library packaging, runs unit tests,
  and runs Android lint.

A successful build or symbol check proves that the expected native code is
compiled and packaged. It does not prove that a physical device successfully
starts Minecraft or renders an in-game frame; that still requires a device
launch test and inspection of the game log.
