# Flint Android native rendering and GLFW backend

Flint builds the Android graphics/runtime libraries through the `:native` module:

- `libGL.so`: GL4ES built from the pinned upstream revision.
- `liblwjgl.so` and `liblwjgl_opengl.so`: LWJGL 3.3.3 Android/Bionic builds from the pinned upstream source using the Android NDK and a matching libffi cross-build.
- `libglfw.so`: Flint's own Android GLFW-compatible C ABI backend.
- `libflint_native.so`: JNI surface, EGL, JLI and input-queue bridge.

## GLFW and LWJGL library contract

LWJGL's Java GLFW binding resolves the native GLFW C ABI functions from `libglfw.so`. It does not require a second JNI-per-method `liblwjgl_glfw.so` adapter. Flint's `libglfw.so` is therefore the actual window-system ABI used by LWJGL, while `liblwjgl.so` and `liblwjgl_opengl.so` provide LWJGL's Android native bindings.

The backend exports the GLFW entry points declared in `flint_glfw_backend.h` and reports compatibility with the pinned binding version. Desktop-only facilities that Android cannot provide are explicitly unavailable or return safe fallback values; symbol presence is not a claim that every desktop feature works.

## Production Surface and thread ownership

There are two separate paths:

- `GL4ESSurfaceView` is the renderer preview and smoke-test screen.
- `GameActivity` owns the production Android Surface. It does not create EGL or launch a render loop itself.

For a production launch, `PojavStyleGameLauncher` creates one `Flint-Minecraft-JVM` worker thread. On that same thread it:
1. Creates the ANativeWindow-backed EGL window.
2. Initializes GL4ES while the EGL context is current.
3. Attaches the same EGL display/surface/context to Flint's GLFW C ABI.
4. Initializes the GLFW platform.
5. Calls `JLI_Launch` without switching threads.

LWJGL's normal `glfwCreateWindow` call creates the logical game window backed by the already-attached Android Surface. GLFW event polling and buffer swaps happen through the same C ABI. GLFW/EGL/ANativeWindow resources are released from the worker thread after JLI returns.

## Input path

Android events are placed in a bounded native FIFO by `FlintNativeWindowBridge`; the GLFW event thread drains them through `glfwPollEvents` or `glfwWaitEvents`. Supported event records cover key, Unicode character, cursor, mouse button, scroll, focus, window size, framebuffer size, wake and window close.

The virtual touch overlay provides a WASD joystick plus attack, use, jump, sneak, inventory and pause. Touch outside those controls falls through to the game Surface. In `GLFW_CURSOR_DISABLED` mode, native cursor dispatch converts physical Android pointer movement into relative deltas to support camera movement.

STOP queues a close request; it is not a force-kill. If the game does not process the GLFW close event, Flint leaves EGL on its owner thread and reports that it is still running instead of destroying resources from the UI thread.

## Validation boundary

CI validates Android compilation, unit tests, lint, native build, native library packaging and exported GLFW symbols. It does not exercise a real GPU driver, downloaded JRE, Minecraft title screen or world entry.

The physical-device checklist remains in [docs/ANDROID_RUNTIME_INTEGRATION.md](../docs/ANDROID_RUNTIME_INTEGRATION.md). Do not claim READY TO PLAY until title-screen, world, input, audio and stop/restart tests pass on hardware.
