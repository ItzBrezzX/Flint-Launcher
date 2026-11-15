# Flint Native Window and GLFW Backend

## Current implementation

Flint builds its own Android GLFW-compatible platform layer as `libglfw.so`
for every configured ABI. The backend exports all 117 GLFW function names
referenced by the pinned LWJGL 3.3.x generated Java binding table. The
reported compatibility version is GLFW 3.3.8.

This is **symbol-level compatibility, not complete behavioral parity** with
desktop GLFW. Some desktop-only facilities return an unavailable/empty
result or perform a documented no-op on Android. The implementation is
original Flint code and does not copy or link PojavLauncher source.

## Data flow

`GL4ESSurfaceView` receives Android Surface, touch, mouse, keyboard and focus
callbacks. Key codes, modifiers and text are converted to GLFW values in
Kotlin. The callbacks enqueue records through `FlintNativeWindowBridge` into
the bounded native FIFO.

The Flint GLFW event loop drains that FIFO from the calling window thread:
`glfwPollEvents`, `glfwWaitEvents` and `glfwWaitEventsTimeout` dispatch key,
Unicode character, cursor, mouse button, scroll, focus, window-size and
framebuffer-size callbacks. Queue insertion notifies the condition variable,
so a waiting GLFW event loop can wake without busy-polling.

The Android GL4ES preview exercises this path:
attach EGL surface -> `glfwInit` -> `glfwCreateWindow` -> event dispatch ->
GL4ES draw -> `glfwSwapBuffers` -> `glfwDestroyWindow`.

## Android-specific behavior and limits

The backend uses one Android-owned window and the EGL display, draw surface
and context current on the thread that attaches the surface. Physical window
position, frame insets, opacity and size constraints are not controlled by
GLFW on Android. System cursor creation/locking, raw mouse motion, monitor
gamma, hardware joystick/gamepad discovery and system clipboard integration
are not implemented; unsupported queries report empty/unavailable values,
and the clipboard currently stores a process-local string. These stubs keep
the LWJGL function table resolvable but do not imply those features work.

## Event record

Each queued event is six 64-bit values:

1. event type
2. code (GLFW key/button code or Unicode code point)
3. action (0 = release, 1 = press/focus gained, 2 = repeat)
4. GLFW modifier mask
5. X coordinate or horizontal scroll (IEEE-754 double bits)
6. Y coordinate or vertical scroll (IEEE-754 double bits)

The FIFO capacity is 1024 records. When full, new events are rejected and
`nativeDroppedInputEventCount()` exposes the cumulative drop count. The
consumer must drain events regularly on the GLFW/window thread. CI now checks
that every GLFW function declared in the C ABI header is exported from
`libglfw.so` for arm64-v8a, armeabi-v7a, x86 and x86_64.

## EGL and thread ownership

The backend attaches to the EGL display, draw surface and context current on
the calling thread. It cannot steal a context that remains current on another
thread. Preview calls all execute on the SurfaceView render thread.

This matters for Minecraft: `PojavStyleGameLauncher` invokes JLI on a
dedicated JVM thread, while the preview's EGL context belongs to the
SurfaceView render thread. Production launch still requires a safe context
ownership handoff or a design that runs the game render loop on the context
owner thread. That handoff is not implemented yet.

## LWJGL and Minecraft integration boundary

The backend's GLFW function symbols are available to an Android-built LWJGL
GLFW JNI adapter; `JNI_OnLoad` promotes `libglfw.so` to process-global
symbol scope. The repository does **not yet build or package the matching
Android/Bionic `liblwjgl.so` and `liblwjgl_opengl.so`** native libraries.
Minecraft's `natives-linux` artifacts are Linux/GLIBC binaries and cannot be
loaded as Android libraries. Flint must build/package the Android LWJGL
natives matching each selected Minecraft LWJGL version, then configure native
library resolution to prefer those files.

A passing Android CI build and the GLFW/GL4ES preview are not a real
Minecraft runtime test. No title-screen or gameplay launch has been verified.

## Remaining work before a real Minecraft title-screen test

- Build/package version-matched Android `liblwjgl.so` and
  `liblwjgl_opengl.so` for the target Minecraft LWJGL versions.
- Wire the native libraries into Minecraft's runtime extraction and
  `java.library.path`/LWJGL load path without attempting to load Linux
  native binaries.
- Transfer EGL ownership safely to the Minecraft render thread or run
  rendering on the current EGL owner thread.
- Replace clipboard/cursor/joystick placeholders with Android integrations
  where required by Minecraft.
- Launch the actual Minecraft main class and verify the title screen, input,
  world load and gameplay on arm64-v8a.

