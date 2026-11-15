# Flint Native Window and GLFW Backend

## Implemented native path

Flint owns an Android GLFW-compatible platform layer built as `libglfw.so`.
It exports the GLFW C entry points consumed by the pinned LWJGL 3.3.x Java
bindings and reports the GLFW 3.3.8 compatibility version. The implementation
is Flint code; it does not copy or link PojavLauncher source.

The Android native build also compiles the LWJGL 3.3.3 core and OpenGL JNI
libraries against the Android NDK/Bionic toolchain for the configured ABIs.
The APK native-library verification checks that `liblwjgl.so`,
`liblwjgl_opengl.so`, `libglfw.so` and `libGL.so` are packaged. A second
check reads the dynamic symbol table and confirms that every GLFW C function
declared by Flint's ABI header is exported for each configured ABI.

At launch preparation, `AndroidLwjglNativesInstaller` copies those four APK
libraries into the per-game native directory and validates their ELF class and
machine type against the active Android ABI. The launch arguments set both
`java.library.path` and `org.lwjgl.librarypath` to that directory.
Android extraction explicitly excludes shared objects from Minecraft's
`natives-linux` archives: those are Linux/GLIBC binaries and must not be
passed to Android's Bionic linker. Flint's Bionic libraries are staged instead.

## Android surface and input flow

`GL4ESSurfaceView` owns a real Android Surface and a dedicated EGL render
thread. It receives Android surface, touch, mouse, keyboard and focus
callbacks. Key codes, modifiers and text are converted to GLFW values in
Kotlin; the callbacks enqueue records through `FlintNativeWindowBridge` into
the bounded native FIFO.

The GLFW event loop drains that FIFO on its calling window thread:
`glfwPollEvents`, `glfwWaitEvents` and `glfwWaitEventsTimeout` dispatch key,
Unicode character, cursor, mouse button, scroll, focus, window-size and
framebuffer-size callbacks. Queue insertion notifies the condition variable,
so a waiting event loop can wake without busy-polling.

The Android renderer preview exercises the path:
attach EGL surface -> `glfwInit` -> `glfwCreateWindow` -> event dispatch ->
GL4ES draw -> `glfwSwapBuffers` -> `glfwDestroyWindow`.

The FIFO capacity is 1024 records. When full, new events are rejected and
`nativeDroppedInputEventCount()` exposes the cumulative drop count. The
consumer must drain events regularly on the GLFW/window thread.

## Android-specific behavior and limits

This is symbol-level GLFW compatibility, not complete behavioral parity with
desktop GLFW. Flint uses one Android-owned window and the EGL display, draw
surface and context current on the thread that attaches the Surface. Physical
window position, frame insets, opacity and size constraints are controlled by
Android rather than GLFW.

System cursor creation/locking, raw mouse motion, monitor gamma, hardware
joystick/gamepad discovery and system clipboard integration are not implemented.
Unsupported queries return empty/unavailable values; the clipboard currently
stores a process-local string. These stubs keep the LWJGL function table
resolvable but do not imply those facilities work.

## Production Minecraft boundary

The renderer preview proves that Flint's EGL surface, GL4ES, GLFW C ABI and
event FIFO can run together on the SurfaceView render thread. It is not yet a
production Minecraft rendering session.

`PojavStyleGameLauncher` runs `JLI_Launch` on a dedicated JVM worker thread,
while the preview EGL context belongs to the SurfaceView render thread. The
backend cannot take an EGL context away from another thread while it remains
current there. The production game surface lifecycle therefore still needs a
coordinated EGL ownership transfer (or a design that runs the game render loop
on the context-owning thread) before Minecraft can render through GLFW.

The LWJGL native build is pinned to 3.3.3 today. This is not a claim that every
historical Minecraft LWJGL version has been independently validated against
that native ABI. Legacy/version-specific native compatibility must be checked
against the actual version metadata and runtime tests.

## Verification boundary

Android CI has previously compiled all modules, packaged the Android native
libraries and passed the GLFW export check. The repository's latest run before
the native-extractor test correction failed three installer unit tests; that
regression is corrected in the source and is being rechecked by CI.

A CI build, ELF/symbol checks and the renderer preview do not prove a real
Minecraft startup. No Minecraft title screen, world load or gameplay session
has been verified on a physical Android device.
