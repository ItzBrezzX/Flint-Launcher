# :runtime

Java runtime management, renderer selection, launch readiness and the in-process game lifecycle.

## Current source integration

| Component | State |
|-----------|-------|
| `LaunchReadinessResolver` | Implemented |
| `MobileJreManager` | Pinned Android OpenJDK catalog with size/SHA-256 download verification, local archive import and runtime probing |
| `PojavStyleGameLauncher` | Production Surface/EGL/GL4ES/GLFW/JLI sequence wired to a single dedicated game thread |
| `AndroidLwjglNativesInstaller` | Stages the APK-built LWJGL JNI core/OpenGL libraries; GLFW and GL4ES are reused by absolute APK path to avoid duplicate native state |
| Native `flint_native` | JNI, ANativeWindow EGL, JRE loading, JLI entry and input FIFO are wired in source |
| Graceful STOP | GLFW close request routed through the native event queue; no unsafe cross-thread EGL destruction |
| Minecraft title screen / world | **NOT VERIFIED on a physical device** |

A `LaunchResult.Started` means native EGL/GL4ES/GLFW setup completed and the worker entered `JLI_Launch`. It is not proof that Minecraft reached the title screen. See [docs/ANDROID_RUNTIME_INTEGRATION.md](../docs/ANDROID_RUNTIME_INTEGRATION.md) and [docs/SPIKE0.md](../docs/SPIKE0.md).
