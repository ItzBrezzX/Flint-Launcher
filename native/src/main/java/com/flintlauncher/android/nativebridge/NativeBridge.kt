package com.flintlauncher.android.nativebridge

/**
 * JNI surface for the native runtime and graphics diagnostics.
 *
 * loadLibrary is attempted once; if the .so is missing, methods degrade
 * gracefully and report the failure instead of crashing the launcher.
 */
object NativeBridge {
    private var loaded = false
    private var loadError: String? = null

    init {
        try {
            System.loadLibrary("flint_native")
            loaded = true
        } catch (e: UnsatisfiedLinkError) {
            loadError = e.message
            loaded = false
        }
    }

    val isNativeLoaded: Boolean get() = loaded
    val nativeLoadError: String? get() = loadError

    /** Pure JNI smoke (no JRE). */
    external fun nativeSmokeTest(): String

    /** EGL + GLES2 pbuffer clear test. */
    external fun nativeEglSmoke(): String

    /** Actual EGL/GLES driver strings from a temporary pbuffer context. */
    external fun nativeEglCapabilities(): String

    /** Attach an Android Surface and create an EGL window surface for game rendering. */
    external fun nativeSurfaceCreated(surface: android.view.Surface): Boolean

    /** Update the EGL viewport when Android changes the game surface dimensions. */
    external fun nativeSurfaceChanged(width: Int, height: Int): Boolean

    /** Acquire the EGL context on the calling render thread. */
    external fun nativeSurfaceMakeCurrent(): Boolean

    /** Present the current EGL back buffer on the calling render thread. */
    external fun nativeSurfaceSwapBuffers(): Boolean

    /** Release EGL context ownership from the calling render thread. */
    external fun nativeSurfaceReleaseCurrent()

    /** Release EGL resources and the retained ANativeWindow reference. */
    external fun nativeSurfaceDestroyed()

    /** Enqueue one Flint GLFW-compatibility event (internal event ID 1..8). */
    external fun nativePostGlfwEvent(type: Int, a: Int, b: Int, c: Int, d: Int)

    /** Drain pending events as flat groups of five integers. */
    external fun nativeDrainGlfwEvents(): IntArray?

    /** Total number of events dropped because the bounded queue was full. */
    external fun nativeGlfwDroppedEventCount(): Long

    /** dlopen mobile OpenJDK libjli and resolve JLI_Launch. */
    external fun nativeLoadJre(jreHomePath: String): Boolean

    /** Invoke JLI_Launch with the given argv. */
    external fun nativeLaunchJvm(args: Array<String>): Int

    external fun nativeShutdown()

    fun safeSmokeTest(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeSmokeTest() }.getOrElse { "smoke-exception: ${it.message}" }

    fun safeEglSmoke(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeEglSmoke() }.getOrElse { "egl-exception: ${it.message}" }

    fun safeEglCapabilities(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeEglCapabilities() }.getOrElse { "gpu-probe-exception: ${it.message}" }
}
