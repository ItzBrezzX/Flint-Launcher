# Flint Native Window and GLFW Backend

## Current implementation

Flint now builds its own Android GLFW-compatible platform backend as
`libglfw.so` for each configured ABI. It is original Flint code and does not
copy or link PojavLauncher source.

The backend is a **single-window GLFW 3.3 C-ABI subset**, not a complete
drop-in replacement for every GLFW 3.3/3.4 function. It is backed by an
Android `SurfaceView` and the EGL context already created on the surface-owner
thread.

## Data flow

`GL4ESSurfaceView` receives Android surface, touch, mouse, keyboard and focus
callbacks. Key codes and modifiers are mapped to GLFW values in Kotlin. The
callbacks enqueue records through `FlintNativeWindowBridge` into the bounded
native FIFO.

The Flint GLFW event loop drains that FIFO from the calling window thread:
`glfwPollEvents`, `glfwWaitEvents` and `glfwWaitEventsTimeout` dispatch key,
Unicode character, cursor, mouse button, scroll, focus, window-size and
framebuffer-size callbacks. Queue insertion notifies the condition variable
so a waiting GLFW event loop can wake without busy-polling.

The current Android GL4ES preview now exercises the same backend path:
attach EGL surface -> `glfwInit` -> `glfwCreateWindow` -> event dispatch ->
GL4ES draw -> `glfwSwapBuffers` -> `glfwDestroyWindow`.

## Exported GLFW surface

The first C ABI layer includes window lifecycle and state, key/mouse/cursor
state, callback registration, event polling/waiting, monitor and video-mode
queries, timing, user pointers, clipboard storage, context-current, proc
address lookup and EGL buffer swaps.

Android owns the physical surface. Window position changes, true window
resizing, cursor capture/raw motion and system clipboard integration are not
implemented as native Android operations yet. The clipboard is currently
process-local storage. Monitor physical size and content scale are placeholders.
Unsupported GLFW functions must not be assumed to work merely because
`libglfw.so` exports a core subset.

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
consumer must drain events regularly on the GLFW/window thread.

## EGL and thread ownership

The backend attaches to the EGL display, draw surface and context current on
the calling thread. `glfwMakeContextCurrent` can make that context current
again only when EGL permits it; it cannot steal a context that is still current
on another thread. All current preview calls are made by the EGL owner thread.

This is important for Minecraft: the existing `PojavStyleGameLauncher`
invokes JLI on a dedicated JVM thread, while the preview's EGL context belongs
to the SurfaceView render thread. A production launch must explicitly transfer
EGL ownership or move game rendering onto the context-owning thread. This
handoff is not implemented yet.

## LWJGL compatibility boundary

`libglfw.so` exports GLFW C symbols for an Android-built LWJGL GLFW JNI
adapter to call. The repository does not yet package an Android/Bionic build
of `liblwjgl_glfw.so` or the corresponding LWJGL JNI bindings. The normal
Minecraft `natives-linux` artifacts are Linux/GLIBC binaries and cannot be
treated as Android natives. Therefore this commit connects the Android event
queue to Flint's GLFW C event loop, but **does not yet prove that
`org.lwjgl.glfw.GLFW` from a Minecraft classpath can call it**.

## Remaining work before a real Minecraft title-screen test

- Build/package a version-matched Android `liblwjgl_glfw.so` JNI adapter for
  the exact LWJGL artifacts used by the target Minecraft version.
- Complete the additional GLFW functions requested by those LWJGL/Minecraft
  versions, including callbacks and monitor/context queries not yet supported.
- Transfer EGL context ownership safely from the SurfaceView thread to the
  Minecraft render thread, or redesign the launch/render thread ownership.
- Route Android clipboard, cursor capture, key text composition, lifecycle and
  controller/gamepad APIs through the platform boundary.
- Verify `liblwjgl_glfw.so` loads on arm64-v8a, then run the actual Minecraft
  main class through the selected JRE and confirm the title screen and gameplay.

CI compilation and the GL4ES/GLFW preview are not substitutes for that real
Minecraft runtime test.
