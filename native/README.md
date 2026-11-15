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
  - Android touch, pointer, scroll, focus, and common hardware-key input is encoded into a bounded 1,024-entry native queue. `FlintRenderSession` drains that queue on the render thread and exposes events through its `dispatchEvents` callback.
  - `FlintGlfwWindow` exposes Flint-owned window state (pixel size, focus, close flag) and explicit frame requests over the Android Surface. Native event enqueue updates this state, and surface attach/detach resets it.
  - A separate `libglfw.so` target now exports a first GLFW C ABI slice: init/terminate, one Android-backed window, EGL context current/release, swap, poll/wait events, window size/focus/close state, key/mouse/cursor queries, core callbacks, timer functions, cursor-mode state with virtual relative pointer movement, and GL procedure lookup. It consumes the same native event queue as the JNI bridge; the render-session worker deliberately does not drain that queue.
  - The current window bridge clears a diagnostic background only; it is not a Minecraft renderer.

## Not implemented yet

- GL4ES, MobileGlues, LTW, or Zink renderer integration.
- Full GLFW 3 ABI compatibility. The current `libglfw.so` is a limited Flint-owned Android backend: it supports one host-provided Android Surface and does not yet implement monitor/fullscreen management, real joystick/gamepad devices, Vulkan, Android clipboard integration, hardware cursor shapes or pointer capture, shared contexts, or every optional GLFW extension expected by arbitrary LWJGL builds. The joystick API symbols return no connected devices.
- Loading and validating the matching Android LWJGL native modules (`liblwjgl*.so`) for each resolved Minecraft LWJGL version and app ABI; desktop Linux natives must not be loaded on Android.
- Verifying the partial GLFW ABI against the exact JNI symbols imported by the pinned LWJGL native libraries and extending it until the Minecraft-required surface is covered.
- Wiring a concrete Minecraft renderer into the `FlintRenderSession.renderFrame` callback.
- An end-to-end Activity/Compose game-session route that creates, attaches, and closes the render session.
- Full physical-device verification of callback timing, touch-to-relative-mouse behavior, focus loss, and surface recreation under a real Minecraft render loop.
- Audio and actual Minecraft frame presentation.

Do not mark a renderer available or report Minecraft as playable until those components are integrated and tested on a physical Android device.
