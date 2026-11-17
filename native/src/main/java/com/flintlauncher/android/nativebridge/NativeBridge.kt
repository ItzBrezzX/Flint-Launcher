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
    /** Set one process-level environment variable before JLI_Launch starts. */
    external fun nativeSetEnv(key: String, value: String): Boolean

    /** APK nativeLibraryDir for resolving packaged .so files. */
    external fun nativeSetNativeLibraryDir(dir: String)

    /** Active window size and refresh rate. */
    external fun nativeSetDisplayMetrics(width: Int, height: Int, refreshHz: Float)

    /** Best-effort performance-core preference for the calling thread. */
    external fun nativePreferPerformanceCores(enable: Boolean): Boolean
    external fun nativeLoadJre(jreHomePath: String): Boolean
    external fun nativeLaunchJvm(args: Array<String>): Int
    external fun nativeShutdown()

    /** Must be called on the thread where the Android EGL context is current. */
    external fun nativeLoadGL4ES(libraryPath: String): Boolean
    external fun nativeGL4ESVersion(): String
    external fun nativeGL4ESClear(): Boolean

    /** Create an ANativeWindow-backed EGL context on the calling render thread. */
    external fun nativeCreateWindowSurface(surface: android.view.Surface): String

    /** Update the GLES viewport; call on the EGL owner thread. */
    external fun nativeResizeWindowSurface(width: Int, height: Int): Boolean

    /** Present the current GL4ES frame to the Android Surface. */
    external fun nativeSwapWindowBuffers(): Boolean

    /** Destroy EGL resources on the same thread that created them. */
    external fun nativeDestroyWindowSurface()

    /** Enqueue one Android input record for consumption by the game/window thread. */
    external fun nativePushInputEvent(
        type: Int, code: Int, action: Int, modifiers: Int, x: Double, y: Double,
    )

    /** Drain up to maximum events; each event is encoded as six longs. */
    external fun nativePollInputEvents(maximum: Int): LongArray

    /** Number of events rejected because the bounded native queue was full. */
    external fun nativeDroppedInputEventCount(): Long

    /** Attach the current EGL owner-thread window to Flint's GLFW platform backend. */
    external fun nativeGlfwAttachAndroidSurface(width: Int, height: Int): Boolean

    /** Initialize the GLFW C ABI after the Android EGL surface is attached. */
    external fun nativeGlfwInitialize(): Boolean

    /** Create the single logical GLFW window backed by the attached Android Surface. */
    external fun nativeGlfwCreateWindow(width: Int, height: Int, title: String): Boolean

    /** Dispatch the native FIFO into the registered GLFW callbacks on the caller thread. */
    external fun nativeGlfwPollEvents(): Int

    /** Swap the attached EGL window through the GLFW entry point. */
    external fun nativeGlfwSwapBuffers(): Boolean

    external fun nativeGlfwWindowShouldClose(): Boolean
    external fun nativeGlfwSetWindowShouldClose(shouldClose: Boolean)
    external fun nativeGlfwVersion(): String

    /** Number of frames successfully presented by the Minecraft GLFW window. */
    external fun nativeGlfwPresentedFrameCount(): Long
    external fun nativeGlfwDestroyWindow()

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
