package com.flintlauncher.android.nativebridge

import android.view.Surface

/**
 * Flint-owned facade over the clean-room GLFW stub (native). Used by our own
 * render thread and the LWJGL compat shim (org.lwjgl.glfw.GLFW) to avoid
 * scattering NativeBridge calls. Single-window model (Spike 0).
 *
 * All methods must be called on the render thread (the thread that owns the
 * EGL context), except postResize/postClose which are thread-safe.
 */
object GlfwStubShim {
    const val WINDOW_HANDLE: Long = 1L // single window; LWJGL shim maps to this

    fun init(): Boolean =
        NativeBridge.isNativeLoaded && runCatching { NativeBridge.nativeGlfwInit() }.getOrDefault(false)

    fun terminate() {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwTerminate() }
    }

    /** Attach the Android Surface as the GLFW window. Returns status string. */
    fun attachSurface(surface: Surface, width: Int, height: Int): String =
        if (NativeBridge.isNativeLoaded) runCatching {
            NativeBridge.nativeGlfwAttachSurface(surface, width, height)
        }.getOrDefault("glfw-exception") else "native-not-loaded"

    fun detachSurface() {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwDetachSurface() }
    }

    fun makeContextCurrent() {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwMakeContextCurrent() }
    }

    fun pollEvents() {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwPollEvents() }
    }

    fun windowShouldClose(): Boolean =
        NativeBridge.isNativeLoaded && runCatching { NativeBridge.nativeGlfwWindowShouldClose() }.getOrDefault(true)

    fun swapBuffers() {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwSwapBuffers() }
    }

    fun getTime(): Double =
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwGetTime() }.getOrDefault(0.0) else 0.0

    fun versionString(): String = NativeBridge.safeGlfwVersionString()

    /** Thread-safe: post a resize from SurfaceHolder.Callback. */
    fun postResize(width: Int, height: Int) {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwPostResize(width, height) }
    }

    /** Thread-safe: request window close (e.g. back button). */
    fun postClose() {
        if (NativeBridge.isNativeLoaded) runCatching { NativeBridge.nativeGlfwPostClose() }
    }
}
