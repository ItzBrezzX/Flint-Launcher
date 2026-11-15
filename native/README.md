# :native

Android JNI/C++ bridge for Flint's mobile Java runtime and game graphics surface.

## Implemented

- JNI smoke test and JRE `libjli.so` loading / `JLI_Launch` entry point.
- EGL/GLES2 pbuffer diagnostics (`nativeEglSmoke`, `nativeEglCapabilities`).
- Android `SurfaceView` lifecycle bridge:
  - `FlintGameSurfaceView` owns the `SurfaceHolder.Callback` lifecycle.
  - JNI converts `Surface` to a retained `ANativeWindow`.
  - Native code creates an EGL window surface and GLES2 context, updates the viewport on resize, and releases EGL/`ANativeWindow` resources on detach.
  - The current window bridge clears a diagnostic background only; it is not a Minecraft renderer.

## Not implemented yet

- GL4ES, MobileGlues, LTW, or Zink renderer integration.
- LWJGL 2/3 Android GLFW compatibility and event pump.
- Minecraft render-thread ownership, input forwarding, and frame presentation.
- An end-to-end Activity/Compose game-session route.

Do not mark a renderer available or report Minecraft as playable until those components are integrated and tested on a physical Android device.
