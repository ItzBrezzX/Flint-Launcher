# Android runtime integration

Updated: 16 November 2026.

This document describes the current source-code path. It is **not** a claim that Minecraft has reached the title screen or is playable on a physical device.

## PLAY and Surface lifecycle

1. PLAY reads the selected installation from `:app` and opens `GameActivity` with the selected Minecraft version.
2. `GameActivity` creates a `FlintGameSurfaceView`. The Android Surface is retained for the launch request and is never recreated by the native runtime.
3. `PlayLaunchCoordinator` verifies the selected offline account, installed version, classpath, extracted natives, runtime, and GL4ES library. It sets LWJGL's library search path and pins `org.lwjgl.opengl.libname=libGL.so`, `org.lwjgl.egl.libname=libEGL.so`, and `org.lwjgl.opengles.libname=libGLESv2.so` so the Android process resolves GL4ES and Android system graphics libraries rather than desktop Linux sonames.
4. `PojavStyleGameLauncher` starts one dedicated `Flint-Minecraft-JVM` thread. That thread creates the ANativeWindow/EGL window, initializes GL4ES, attaches Flint's GLFW C ABI, and then enters `JLI_Launch`.
5. LWJGL resolves GLFW calls through `libglfw.so`. Flint's C ABI provides the Android-backed window and input callbacks. LWJGL owns logical GLFW window creation through its normal `glfwCreateWindow` call.
6. GLFW event dispatch consumes the native FIFO on the game/window thread. EGL, GLFW and ANativeWindow teardown are performed from that same thread after JLI returns.

The production path intentionally does not reuse the renderer-preview SurfaceView. Preview and Minecraft have separate lifecycle owners.

## Input path

`FlintGameSurfaceView` sends Android key, Unicode, cursor, mouse-button, scroll and focus records to `FlintNativeWindowBridge`. The FIFO is bounded and C++ maps each record into the registered GLFW callback/state.

`FlintTouchControlsOverlay` adds a virtual WASD joystick and Attack, Use, Jump, Sneak, Inventory and Pause controls. Touch outside those controls falls through to the game Surface: taps become mouse clicks and drags become cursor movement. When GLFW has disabled the cursor, the native backend converts Android pointer movement into relative cursor deltas for camera movement.

Native event IDs are stable:
- 1 key, 2 character, 3 cursor, 4 mouse button, 5 scroll, 6 focus
- 7 window size, 8 framebuffer size, 9 wake, 10 window close

## STOP and failure handling

STOP enqueues a GLFW window-close request. The native event queue wakes a waiting GLFW event loop; when the game polls events, `glfwWindowShouldClose` becomes true. This is a **graceful request**, not a force-kill. If JLI does not return within the timeout, Flint leaves EGL on its owner thread and reports that the session is still running rather than destroying EGL from the UI thread.

The app installs `FlintCrashHandler` before creating its dependency container. It writes redacted crash reports under the app-private `files/crash-reports/` directory, writes atomically, keeps the newest five reports, then delegates to Android's previous crash handler.

## Runtime prerequisites and limits

- A verified compatible mobile JRE must be imported or installed. Flint does not bundle an OpenJDK runtime in the APK.
- The selected Minecraft version must have its complete client JAR, libraries, assets and Android-compatible natives installed.
- The selected renderer library must be present in the APK and successfully initialize on the device.
- Microsoft authentication/session refresh remains gated; use an offline account for the currently wired launch path.
- Flint's GLFW C ABI implements the functions needed by the current LWJGL binding and reports unsupported desktop-only facilities explicitly. It is not a full desktop window manager.

## Validation boundary

CI can validate Kotlin compilation, unit tests, Android lint, native compilation, ELF packaging and exported GLFW symbols. It cannot verify the device GPU driver, native JRE behavior, actual Minecraft title screen, world entry, sound or real touch feel.

Physical-device acceptance remains:
- launch to Minecraft title screen;
- create and enter a single-player world;
- verify camera, WASD joystick, attack/use, jump, inventory and pause;
- verify audio;
- STOP returns to Flint without a stuck native thread;
- background/foreground, orientation and repeated launch behavior.

Until those tests pass on target Android hardware, Flint must not be described as READY TO PLAY.
