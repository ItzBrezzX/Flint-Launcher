package com.flintlauncher.android.nativebridge

/**
 * Owner-thread facade for Flint's GLFW-compatible Android backend.
 *
 * initialize(), pollEvents(), swapBuffers(), and destroy() must all be called
 * by the thread that owns the attached EGL context. Android callbacks only
 * publish to the native input FIFO.
 */
object FlintGlfwBackend {
    @Volatile
    private var initialized = false

    fun initialize(width: Int, height: Int, title: String = "Flint"): Boolean {
        check(NativeBridge.isNativeLoaded) {
            "Flint native library is not loaded: ${NativeBridge.nativeLoadError}"
        }
        require(width > 0 && height > 0) { "Surface dimensions must be positive" }
        if (initialized) return true

        if (!NativeBridge.nativeGlfwAttachAndroidSurface(width, height)) return false
        if (!NativeBridge.nativeGlfwInitialize()) {
            NativeBridge.nativeGlfwDestroyWindow()
            return false
        }
        if (!NativeBridge.nativeGlfwCreateWindow(width, height, title)) {
            NativeBridge.nativeGlfwDestroyWindow()
            return false
        }
        initialized = true
        return true
    }

    fun pollEvents(): Int {
        check(initialized) { "Flint GLFW backend is not initialized" }
        return NativeBridge.nativeGlfwPollEvents()
    }

    fun swapBuffers(): Boolean {
        check(initialized) { "Flint GLFW backend is not initialized" }
        return NativeBridge.nativeGlfwSwapBuffers()
    }

    fun shouldClose(): Boolean =
        initialized && NativeBridge.nativeGlfwWindowShouldClose()

    fun requestClose() {
        if (initialized) NativeBridge.nativeGlfwSetWindowShouldClose(true)
    }

    fun version(): String =
        if (initialized) NativeBridge.nativeGlfwVersion() else "Flint GLFW unavailable"

    fun destroy() {
        if (!initialized) return
        try {
            NativeBridge.nativeGlfwDestroyWindow()
        } finally {
            initialized = false
        }
    }
}
