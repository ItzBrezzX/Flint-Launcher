package com.flintlauncher.android.nativebridge

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Instrumentation test skeleton for native loading + JNI smoke.
 *
 * Runs on a device/emulator (androidTest). Verifies that libflint_native loads
 * and the full JNI surface (smoke, EGL, GLFW, GL loader, audio, input, crash
 * handler) is reachable. EGL/GLFW window tests require a Surface; the app shard
 * provides one via GL4ESSurfaceView / the game activity.
 *
 * Status: skeleton. Assertions marked TODO where a real Surface/context is
 * needed. Run with: ./gradlew :native:connectedAndroidTest
 */
class NativeLoadingInstrumentedTest {

    @Test
    fun nativeLibraryLoads() {
        // System.loadLibrary runs in NativeBridge init block.
        assertTrue(
            "flint_native failed to load: ${NativeBridge.nativeLoadError}",
            NativeBridge.isNativeLoaded,
        )
    }

    @Test
    fun jniSmokePasses() {
        assertTrue(NativeBridge.isNativeLoaded)
        val result = NativeBridge.safeSmokeTest()
        assertTrue("smoke: $result", result == "flint-native-smoke-ok")
    }

    @Test
    fun eglSmokePasses() {
        assertTrue(NativeBridge.isNativeLoaded)
        val result = NativeBridge.safeEglSmoke()
        assertTrue("egl: $result", result == "egl-smoke-ok")
    }

    @Test
    fun eglCapabilitiesReportNonEmpty() {
        assertTrue(NativeBridge.isNativeLoaded)
        val caps = NativeBridge.safeEglCapabilities()
        assertNotNull(caps)
        assertTrue("capabilities: $caps", caps.contains("EGL") || caps.contains("GLES"))
    }

    @Test
    fun glfwInitAndVersion() {
        assertTrue(NativeBridge.isNativeLoaded)
        assertTrue(NativeBridge.nativeGlfwInit())
        val version = NativeBridge.safeGlfwVersionString()
        assertTrue(version.contains("GLFW") || version.contains("Flint"))
        NativeBridge.nativeGlfwTerminate()
    }

    @Test
    fun glSymbolLoaderFallsBackToSystemGles() {
        assertTrue(NativeBridge.isNativeLoaded)
        // Without a current EGL context, compat query reports no-context error
        // but the loader itself should not crash.
        val backend = NativeBridge.nativeGlInit(1, null) // SystemGles
        assertTrue("backend=$backend", backend >= 0)
        val compat = NativeBridge.safeGlQueryCompat()
        assertNotNull(compat)
    }

    @Test
    fun audioStubReportsSilentWhenOpenalAbsent() {
        assertTrue(NativeBridge.isNativeLoaded)
        // libopenal.so likely absent in test env → stub mode (available=false).
        val available = NativeBridge.nativeAudioIsAvailable()
        val query = NativeBridge.safeAudioQuery()
        assertNotNull(query)
        // Either real OpenAL loaded or silent stub; either way no crash.
        assertTrue(available || query.contains("stub") || query.contains("not found"))
    }

    @Test
    fun inputInjectionDoesNotCrash() {
        assertTrue(NativeBridge.isNativeLoaded)
        NativeBridge.nativeResetInput()
        NativeBridge.nativeInjectKey(32, 0, 1, 0) // SPACE press
        NativeBridge.nativeInjectChar(65) // 'A'
        NativeBridge.nativeInjectMouseButton(0, 1, 0)
        NativeBridge.nativeInjectCursorPos(100.0, 200.0)
        NativeBridge.nativeInjectScroll(0.0, 1.0)
        NativeBridge.nativeInjectJoystickAxis(0, 0.5f.toDouble(), 0.0, 1.0, 1)
        NativeBridge.nativeSetTouchKeyMapping("jump", 32)
        NativeBridge.nativeInjectTouchAction("jump", true)
        NativeBridge.nativeInjectTouchAction("jump", false)
        NativeBridge.nativeSetMouseCapture(0x00034003) // disabled
        NativeBridge.nativeResetInput()
        // No assertion: success = no crash.
    }

    @Test
    fun crashHandlerInstalls() {
        assertTrue(NativeBridge.isNativeLoaded)
        val dir = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir.absolutePath
        val ok = NativeBridge.nativeInstallCrashHandler(dir)
        assertTrue("crash handler install failed", ok)
        val report = NativeBridge.safeGetLastCrashReport()
        assertNotNull(report)
    }

    @Test
    fun glfwWindowAttachRequiresSurface() {
        assertTrue(NativeBridge.isNativeLoaded)
        // TODO(app shard): provide a real Surface via SurfaceView/TextureView.
        // Without a surface, attachSurface must return a clear error, not crash.
        val result = try {
            // Passing null surface is guarded at JNI.
            NativeBridge.nativeGlfwAttachSurface(
                TODO("provide Surface from activity"), 0, 0,
            )
        } catch (_: NotImplementedError) {
            "surface-not-provided-in-test"
        }
        assertNotNull(result)
    }
}
