package com.flintlauncher.android.nativebridge

/**
 * JNI surface for Spike 0.
 *
 * loadLibrary is attempted once; if the .so is missing (no NDK build yet),
 * all methods degrade gracefully and report the failure instead of crashing.
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

    /** dlopen mobile OpenJDK libjli and resolve JLI_Launch. */
    external fun nativeLoadJre(jreHomePath: String): Boolean

    /**
     * Invoke JLI_Launch with the given argv.
     * Returns process-style exit code, or negative on bridge errors.
     * Not fully wired until a verified JRE is present.
     */
    external fun nativeLaunchJvm(args: Array<String>): Int

    external fun nativeShutdown()

    fun safeSmokeTest(): String =
        if (!loaded) "native-not-loaded: $loadError" else runCatching { nativeSmokeTest() }.getOrElse { "smoke-exception: ${it.message}" }

    fun safeEglSmoke(): String =
        if (!loaded) "native-not-loaded: $loadError" else runCatching { nativeEglSmoke() }.getOrElse { "egl-exception: ${it.message}" }
}
