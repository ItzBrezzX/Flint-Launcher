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

    /** Create an ANativeWindow-backed EGL context on the calling render thread. */
    external fun nativeCreateWindowSurface(surface: android.view.Surface): String

    /** Update the GLES viewport; call on the EGL owner thread. */
    external fun nativeResizeWindowSurface(width: Int, height: Int): Boolean

    /** Present the current GL4ES frame to the Android Surface. */
    external fun nativeSwapWindowBuffers(): Boolean

    /** Destroy EGL resources on the same thread that created them. */
    external fun nativeDestroyWindowSurface()

    // --- GLFW stub lifecycle (clean-room, glfw_stub.cpp) ---
    external fun nativeGlfwInit(): Boolean
    external fun nativeGlfwTerminate()
    /** Attach an Android Surface as the GLFW window's backing surface (render thread). */
    external fun nativeGlfwAttachSurface(surface: android.view.Surface, width: Int, height: Int): String
    external fun nativeGlfwDetachSurface()
    external fun nativeGlfwPollEvents()
    external fun nativeGlfwWindowShouldClose(): Boolean
    external fun nativeGlfwSwapBuffers()
    external fun nativeGlfwMakeContextCurrent()
    external fun nativeGlfwGetTime(): Double
    external fun nativeGlfwPostResize(width: Int, height: Int)
    external fun nativeGlfwPostClose()
    external fun nativeGlfwVersionString(): String

    // --- GL symbol loader / renderer backend (gl_symbol_loader.cpp) ---
    /** requested: 1=SystemGles, 2=Gl4esPlugin. Returns backend actually selected (0=none). */
    external fun nativeGlInit(requested: Int, pluginPath: String?): Int
    external fun nativeGlGetProcAddress(name: String): Long
    external fun nativeGlQueryCompat(): String
    external fun nativeGlRecoverContext(): Boolean
    external fun nativeGlCurrentBackend(): Int
    external fun nativeGlLastError(): String
    external fun nativeAddNativeLibraryPath(path: String): Boolean
    external fun nativeSetPluginDir(path: String)

    // --- Input bridge (input_bridge.cpp) ---
    external fun nativeInjectKey(key: Int, scancode: Int, action: Int, mods: Int)
    external fun nativeInjectChar(codepoint: Int)
    external fun nativeInjectMouseButton(button: Int, action: Int, mods: Int)
    external fun nativeInjectCursorPos(x: Double, y: Double)
    external fun nativeInjectCursorDelta(dx: Double, dy: Double)
    external fun nativeInjectScroll(xoffset: Double, yoffset: Double)
    external fun nativeInjectJoystickAxis(stick: Int, axisX: Double, axisY: Double, sensitivity: Double, mode: Int)
    external fun nativeSetTouchKeyMapping(action: String, glfwKey: Int)
    external fun nativeGetTouchKeyMapping(action: String): Int
    external fun nativeInjectTouchAction(action: String, pressed: Boolean)
    external fun nativeSetMouseCapture(state: Int)
    external fun nativeGetMouseCapture(): Int
    external fun nativeResetInput()

    // --- Audio (OpenAL Soft dynamic load, audio_openal.cpp) ---
    external fun nativeAudioInit(libraryPath: String?): Boolean
    external fun nativeAudioIsAvailable(): Boolean
    external fun nativeAudioQuery(): String
    external fun nativeAudioGetProcAddress(name: String): Long

    // --- Crash handler (flint_jni.cpp) ---
    external fun nativeInstallCrashHandler(crashDir: String): Boolean
    external fun nativeGetLastCrashReport(): String
    external fun nativeClearCrashReport(): Boolean

    fun safeSmokeTest(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeSmokeTest() }.getOrElse { "smoke-exception: ${it.message}" }

    fun safeEglSmoke(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeEglSmoke() }.getOrElse { "egl-exception: ${it.message}" }

    fun safeEglCapabilities(): String =
        if (!loaded) "native-not-loaded: $loadError"
        else runCatching { nativeEglCapabilities() }.getOrElse { "gpu-probe-exception: ${it.message}" }

    fun safeGlfwVersionString(): String =
        if (!loaded) "native-not-loaded"
        else runCatching { nativeGlfwVersionString() }.getOrElse { "glfw-exception: ${it.message}" }

    fun safeGlQueryCompat(): String =
        if (!loaded) "native-not-loaded"
        else runCatching { nativeGlQueryCompat() }.getOrElse { "gl-exception: ${it.message}" }

    fun safeAudioQuery(): String =
        if (!loaded) "native-not-loaded"
        else runCatching { nativeAudioQuery() }.getOrElse { "audio-exception: ${it.message}" }

    fun safeGetLastCrashReport(): String =
        if (!loaded) "native-not-loaded"
        else runCatching { nativeGetLastCrashReport() }.getOrElse { "crash-exception: ${it.message}" }
}
