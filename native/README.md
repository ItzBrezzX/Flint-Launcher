# Flint Android native rendering and GLFW backend

Flint fetches upstream [GL4ES](https://github.com/ptitSeb/gl4es.git) at the
pinned revision `8b1a3da5d2331d0f88b70be25963182d46eed857` and builds it as
`libGL.so` for each configured ABI.

Flint also builds its own Android GLFW-compatible platform layer as
`libglfw.so`. It exports the 117 GLFW function names used by the pinned
LWJGL 3.3.x generated binding table and reports GLFW 3.3.8 compatibility.
This is symbol-level compatibility; several desktop-only facilities have
explicit Android fallbacks (no-op or unavailable values). It is original
Flint code, not a copied PojavLauncher implementation.

## Surface and event path

`GL4ESSurfaceView` owns an Android Surface and an EGL render thread. On that
same thread it attaches the current EGL window to Flint GLFW, initializes one
logical GLFW window, drains keyboard/touch/mouse/focus/resize events through
`glfwPollEvents`, draws through GL4ES and presents with
`glfwSwapBuffers`.

The native FIFO is bounded to 1024 events. Producers notify a condition
variable, allowing `glfwWaitEvents` and `glfwWaitEventsTimeout` to sleep
until new input is queued. Input callbacks are dispatched only by the thread
that calls the GLFW event pump.

## Android capability boundary

The C ABI includes core window lifecycle, input state, callback registration,
event polling/waiting, timing, monitor/video-mode queries, context-current,
GL proc lookup and EGL swap operations. Android-controlled physical window
placement/resizing, system cursor capture, raw mouse motion, monitor gamma,
joystick/gamepad discovery and system clipboard are limited or unavailable.
The process-local clipboard string and no-op methods only keep the symbol
table resolvable; they are not full implementations.

CI verifies that every GLFW symbol declared in
`flint_glfw_backend.h` is exported from `libglfw.so` for all four configured
Android ABIs.

## LWJGL/Minecraft runtime is not ready yet

The GLFW C ABI is present and global symbol lookup is prepared for an
Android-built LWJGL GLFW JNI adapter. However, Flint does **not** yet build
or package Android/Bionic versions of `liblwjgl.so` and
`liblwjgl_opengl.so`. Minecraft's `natives-linux` libraries target
Linux/GLIBC and cannot be used as Android shared libraries. Flint still needs
the version-matched Android LWJGL native build and native library resolution.

The EGL context is currently owned by the SurfaceView render thread; the
Minecraft JLI launcher runs on a separate JVM thread. Production rendering
still needs an explicit EGL context-owner handoff or a render-loop architecture
that preserves single-thread EGL ownership.

Therefore the current CI proves APK packaging and GLFW symbol exports, while
the preview proves the Flint GLFW event loop and GL4ES swap path. Neither
proves that Minecraft has reached its title screen or is playable.
