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

    /** dlopen mobile OpenJDK libjli and resolve JLI_Launch. */
    external fun nativeLoadJre(jreHomePath: String): Boolean

    /** Invoke JLI_Launch with the given argv. */
    external fun nativeLaunchJvm(args: Array<String>): Int

    external fun nativeShutdown()

    /**
     * Create an Android ANativeWindow-backed EGL/GLES2 surface.
     * Call create/swap/destroy from the same dedicated render thread.
     */
    external fun nativeCreateWindowSurface(surface: android.view.Surface): String

    /** Update the GLES viewport after Android reports a surface size change. */
    external fun nativeResizeWindowSurface(width: Int, height: Int): Boolean

    /** Present the current GLES back buffer to the Android window. */
    external fun nativeSwapWindowBuffers(): Boolean

    /** Release EGL context, surface, display and the retained ANativeWindow. */
    external fun nativeDestroyWindowSurface()


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
