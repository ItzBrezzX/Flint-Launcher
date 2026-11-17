# Snapshot status: INCOMPLETE — host + game surface path

Microsoft authentication remains paused.

## Learned from references (implemented under Flint names)

| Reference | Idea taken | Flint implementation |
|---|---|---|
| mojoexec | native lib dir, display size, performance cores | `flint_host.cpp` + `NativeBridge.nativeSetNativeLibraryDir/SetDisplayMetrics/PreferPerformanceCores` |
| glfw-glfw34 Android | surface-sized window + input queue | existing `flint_glfw` + `GameActivity` touch/key → `nativePushInputEvent` |
| NativesPlugin zip | mostly scaffold/icons; no extra runtime logic used | — |

## New / updated files

- `native/.../flint_host.{h,cpp}`
- `native/.../flint_jni.cpp` (host JNI)
- `native/.../NativeBridge.kt`
- `native/.../CMakeLists.txt`
- `app/.../GameActivity.kt` (metrics, affinity, input, EGL→GL4ES→GLFW→JLI)
- Prior: `PendingLaunchStore`, coordinator → GameActivity, manifest

## Not verified on device

APK build, title screen, world load. You build and test locally.

READY TO PLAY: no.
