package com.flintlauncher.android.nativebridge

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

    external fun nativeSmokeTest(): String
    external fun nativeEglSmoke(): String
    external fun nativeEglCapabilities(): String
    external fun nativeLoadJre(jreHomePath: String): Boolean
    external fun nativeLaunchJvm(args: Array<String>): Int
    external fun nativeShutdown()

    /** Must be called on the thread where the Android EGL context is current. */
    external fun nativeLoadGL4ES(libraryPath: String): Boolean
    external fun nativeGL4ESVersion(): String
    external fun nativeGL4ESClear(): Boolean

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
