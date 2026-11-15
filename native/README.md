# :native

Android JNI/C++ bridge for Flint's mobile Java runtime and game graphics surface.

## Implemented

- JNI smoke test and JRE `libjli.so` loading / `JLI_Launch` entry point.
- EGL/GLES2 pbuffer diagnostics (`nativeEglSmoke`, `nativeEglCapabilities`).
- Android `SurfaceView` lifecycle bridge:
  - `FlintGameSurfaceView` owns the `SurfaceHolder.Callback` lifecycle.
  - JNI converts `Surface` to a retained `ANativeWindow`.
  - Native code creates an EGL window surface and GLES2 context, matches the Android buffer format to the selected EGL config, updates the viewport on resize, and releases EGL/`ANativeWindow` resources on detach.
  - EGL context ownership is explicitly acquired/released by the calling render thread; buffer swaps are exposed for frame presentation.
  - `FlintRenderSession` provides a dedicated, event-driven render thread. It coalesces frame requests, invokes a renderer callback only when requested, swaps after successful frame rendering, and releases EGL ownership on the same thread.
  - `FlintGameSurfaceView` quiesces the render thread before resize and waits for in-flight rendering to finish before EGL teardown.
  - The current window bridge clears a diagnostic background only; it is not a Minecraft renderer.

## Not implemented yet

- GL4ES, MobileGlues, LTW, or Zink renderer integration.
- A complete GLFW 3 ABI implementation (GLFWwindow/monitor objects, exported GLFW symbols, context hints, clipboard, timers, joysticks, window callbacks, and LWJGL native-library loading).
- LWJGL 2/3 Android GLFW compatibility and event pump.
- Wiring a concrete Minecraft renderer into the `FlintRenderSession.renderFrame` callback.
- An end-to-end Activity/Compose game-session route that creates, attaches, and closes the render session.
- Android touch/keyboard input now enters a bounded native event queue using Flint's documented internal GLFW-compatible event protocol (key, character, cursor, mouse button, scroll, focus, size, close).
- A consumer/event pump that translates the internal protocol into real GLFW callbacks and LWJGL-visible state.
- Audio and actual Minecraft frame presentation.

Do not mark a renderer available or report Minecraft as playable until those components are integrated and tested on a physical Android device.
