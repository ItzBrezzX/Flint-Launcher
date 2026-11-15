# Flint Android native rendering and GLFW backend

Flint fetches upstream [GL4ES](https://github.com/ptitSeb/gl4es.git) at the
pinned revision `8b1a3da5d2331d0f88b70be25963182d46eed857` and builds it as
`libGL.so` for each configured ABI.

Flint also builds its own GLFW-compatible Android platform layer as
`libglfw.so`. This is original Flint code: it adapts an Android Surface and
EGL context to a bounded input/event queue and exports a documented subset of
the GLFW 3.3 C ABI. It is not a copied PojavLauncher implementation and is
not yet a complete replacement for all GLFW APIs.

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

## Current compatibility boundary

The C ABI includes core window lifecycle, input state, callback registration,
event polling/waiting, timing, monitor/video-mode queries, context-current,
GL proc lookup and EGL swap operations. Android-controlled operations such as
physical window placement, actual Surface resizing, cursor lock/raw motion,
system clipboard and monitor DPI are limited or placeholders.

The C ABI is now available in `libglfw.so`, but a version-matched
Android/Bionic `liblwjgl_glfw.so` JNI binding is not yet built or packaged.
Minecraft's `natives-linux` shared libraries target Linux/GLIBC and cannot
be loaded as Android libraries. The existing EGL context is owned by the
SurfaceView render thread; the production Minecraft render thread still needs
an explicit context handoff.

Consequently, the current preview validates the Flint GLFW event loop and
GL4ES buffer path, not the complete LWJGL-to-Minecraft launch path.

## Build

A clean native build fetches the pinned GL4ES revision, so GitHub network
access is required on the first build. Gradle/CMake dependency caches can be
reused on subsequent builds. Both `libGL.so`, `libglfw.so` and
`libflint_native.so` are built per configured Android ABI.
