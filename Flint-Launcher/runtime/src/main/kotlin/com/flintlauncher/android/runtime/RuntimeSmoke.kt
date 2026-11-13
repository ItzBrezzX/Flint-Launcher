package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Device-side smoke checklist for Spike 0.
 * Call from a debug screen or instrumented test.
 * Every result is a real string from native or a clear failure — never mocked success.
 */
data class SmokeReport(
    val nativeLoaded: Boolean,
    val nativeLoadError: String?,
    val jniSmoke: String,
    val eglSmoke: String,
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
                val ok = if (NativeBridge.isNativeLoaded) {
                    NativeBridge.nativeLoadJre(jreHome.absolutePath)
                } else false
                if (ok) "jre-symbols-ok" else "jre-load-failed"
            }
        }
        return SmokeReport(
            nativeLoaded = NativeBridge.isNativeLoaded,
            nativeLoadError = NativeBridge.nativeLoadError,
            jniSmoke = NativeBridge.safeSmokeTest(),
            eglSmoke = NativeBridge.safeEglSmoke(),
            jreProbe = jreProbe,
        )
    }
}
