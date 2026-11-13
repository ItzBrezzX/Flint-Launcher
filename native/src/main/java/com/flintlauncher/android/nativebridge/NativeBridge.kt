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

    /** Human-readable state/error from the native JRE loader. */
    external fun nativeJreStatus(): String

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

    fun safeJreStatus(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeJreStatus() }.getOrElse { "jre-status-exception: ${it.message}" }
}
