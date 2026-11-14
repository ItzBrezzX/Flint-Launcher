package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Device-side smoke checks. Results come from the native library or an explicit
 * failure string; EGL/GLES availability is not treated as renderer availability.
 */
data class SmokeReport(
    val nativeLoaded: Boolean,
    val nativeLoadError: String?,
    val jniSmoke: String,
    val eglSmoke: String,
    val eglCapabilities: String,
    val jreProbe: String,
) {
    val allCriticalPassed: Boolean
        get() = nativeLoaded &&
            jniSmoke == "flint-native-smoke-ok" &&
            eglSmoke == "egl-smoke-ok"
}

object RuntimeSmoke {
    fun run(jreHome: java.io.File? = null): SmokeReport {
        val jreProbe = when {
            jreHome == null -> "no-jre-provided"
            !jreHome.isDirectory -> "jre-home-missing"
            else -> {
                if (NativeBridge.isNativeLoaded) {
                    NativeBridge.nativeLoadJre(jreHome.absolutePath)
                }
                NativeBridge.safeJreStatus()
            }
        }
        return SmokeReport(
            nativeLoaded = NativeBridge.isNativeLoaded,
            nativeLoadError = NativeBridge.nativeLoadError,
            jniSmoke = NativeBridge.safeSmokeTest(),
            eglSmoke = NativeBridge.safeEglSmoke(),
            eglCapabilities = NativeBridge.safeEglCapabilities(),
            jreProbe = jreProbe,
        )
    }
}
