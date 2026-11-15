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
  - A separate `libglfw.so` target exports the GLFW entry points used by LWJGL's generated function table. The Android implementation covers one host-backed window, thread-owned EGL context current/release, swap, event polling, size/focus/close state, key/mouse/cursor queries, callbacks, timers, virtual relative pointer movement, and GL procedure lookup. Monitor/fullscreen, physical cursors, real joystick devices, clipboard, Vulkan, and some optional extensions remain unsupported.
  - `nativeGlfwAbiSmoke` uses `dlopen("libglfw.so")` and `dlsym` to verify the required runtime symbols. `PojavStyleGameLauncher` runs this preflight before JLI launch; it reports a specific failure if the library cannot be loaded.
  - The current window bridge clears a diagnostic background only; it is not a Minecraft renderer.

## Not implemented yet

- GL4ES, MobileGlues, LTW, or Zink renderer integration.
- Full GLFW 3 ABI compatibility. The current `libglfw.so` is a limited Flint-owned Android backend: it tracks EGL context ownership per thread and supports one host-provided Android Surface and does not yet implement monitor/fullscreen management, real joystick/gamepad devices, Vulkan, Android clipboard integration, hardware cursor shapes or pointer capture, shared contexts, or every optional GLFW extension expected by arbitrary LWJGL builds. The joystick API symbols return no connected devices.
- Loading and validating the matching Android LWJGL native modules (`liblwjgl*.so`) for each resolved Minecraft LWJGL version and app ABI; desktop Linux natives must not be loaded on Android.
- Building and packaging the Android LWJGL JNI modules (`liblwjgl.so`, `liblwjgl_glfw.so`, `liblwjgl_opengl.so`, and the additional modules required by each Minecraft profile), then verifying the ABI against those exact binaries.
- Wiring a concrete Minecraft renderer into the `FlintRenderSession.renderFrame` callback.
- An end-to-end Activity/Compose game-session route that creates, attaches, and closes the render session.
- The end-to-end game Activity must stop the LWJGL render thread and release its context before the host destroys the Android Surface. `FlintRenderSession` is for Flint-managed renderers; do not attach it to the same EGL context while LWJGL owns the GLFW loop.
- Full physical-device verification of callback timing, touch-to-relative-mouse behavior, focus loss, and surface recreation under a real Minecraft render loop.
- Audio and actual Minecraft frame presentation.

Do not mark a renderer available or report Minecraft as playable until those components are integrated and tested on a physical Android device.
