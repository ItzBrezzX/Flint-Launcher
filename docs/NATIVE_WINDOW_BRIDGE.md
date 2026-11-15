# Flint Native Window Bridge

## Scope

This document describes the original Android-to-native event bridge. It is a
foundation for a future LWJGL GLFW platform backend; it is **not** a GLFW
implementation and does not currently make Minecraft launchable.

## Data flow

`GL4ESSurfaceView` receives Android surface, touch, mouse, keyboard and focus
callbacks. It enqueues compact records through `FlintNativeWindowBridge`.
The JNI boundary forwards them to a bounded, mutex-protected native FIFO.
The future game/window thread drains the FIFO and translates Android input
codes into the exact GLFW key, button, cursor, scroll and window callbacks.

## Event record

Each native event is six 64-bit values:

1. event type
2. code (Android key code, pointer ID, or mouse button)
3. action (0 = release/focus lost, 1 = press/focus gained)
4. Android modifier/meta state
5. X coordinate or horizontal scroll (IEEE-754 double bits)
6. Y coordinate or vertical scroll (IEEE-754 double bits)

Types are declared in `FlintInputEvent.kt` and mirrored by
`flint_input_queue.h`. Window and framebuffer sizes are currently reported
in Android surface pixels.

The FIFO capacity is 1024 records. When full, new events are rejected and
`nativeDroppedInputEventCount()` exposes the cumulative drop count. The
consumer must drain events regularly on the game/window thread.

## Threading and EGL ownership

Android callbacks only enqueue input. They must not call GLFW or OpenGL.
EGL creation, GL4ES initialization, buffer swaps and teardown remain owned by
the existing dedicated render thread. A later GLFW integration must explicitly
bind its window/context lifecycle to that owner thread; it must not assume that
a SurfaceView callback thread is a valid OpenGL context thread.

## Remaining work before GLFW compatibility

- Implement the LWJGL-version-specific JNI entry points and GLFW C ABI.
- Map Android key codes and Unicode input to GLFW key/scancode/character events.
- Implement window creation, monitor/video-mode queries, clipboard, cursor modes,
  gamepad policy, timing and event-wait semantics required by Minecraft.
- Connect the GLFW context functions to GL4ES and the EGL owner thread.
- Build and run the exact LWJGL native/JAR versions used by target Minecraft
  versions on arm64-v8a, then run a real title-screen and gameplay smoke test.

No PojavLauncher source is copied or linked by this bridge.
