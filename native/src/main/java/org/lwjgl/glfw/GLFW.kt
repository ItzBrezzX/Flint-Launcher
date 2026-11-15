package org.lwjgl.glfw

import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Flint clean-room LWJGL3-compat shim for org.lwjgl.glfw.GLFW.
 *
 * Delegates to Flint's native GLFW stub (libflint_native.so) via NativeBridge.
 * This class SHADOWS the real LWJGL GLFW class on Minecraft's classpath: the
 * launcher constructs the game classpath with this module's classes instead of
 * the upstream lwjgl-glfw jar.
 *
 * IMPLEMENTED (delegates to native): init/terminate, window hints, create/destroy
 * window, shouldClose, makeContextCurrent, swapBuffers, swapInterval, getProcAddress,
 * poll/wait events, getKey/getMouseButton/getCursorPos, setInputMode, get/setTime,
 * getPrimaryMonitor, getWindowSize/FramebufferSize, setWindowSize/Title,
 * getVersionString, extensionSupported, setClipboardString/getClipboardString,
 * setWindowShouldClose, postEmptyEvent.
 *
 * API-ONLY / STUBBED (registered but not wired to Java upcall, or returns synthetic
 * value): all set*Callback methods — callbacks are stored in a registry but the
 * native→Java upcall bridge is not yet implemented (Spike 0 gap; tracked in
 * docs/progress-shard-native.md). getVideoMode returns a synthetic VidMode handle.
 * joystick/gamepad methods always report "not present".
 *
 * Methods not listed below throw UnsupportedOperationException with a clear message.
 */
object GLFW {

    // GLFW constants (subset; values match public GLFW 3.x ABI).
    const val GLFW_FALSE = 0
    const val GLFW_TRUE = 1
    const val GLFW_RELEASE = 0
    const val GLFW_PRESS = 1
    const val GLFW_REPEAT = 2
    const val GLFW_KEY_SPACE = 32
    const val GLFW_KEY_ESCAPE = 256
    const val GLFW_KEY_ENTER = 257
    const val GLFW_KEY_TAB = 258
    const val GLFW_KEY_BACKSPACE = 259
    const val GLFW_KEY_DELETE = 261
    const val GLFW_KEY_RIGHT = 262
    const val GLFW_KEY_LEFT = 263
    const val GLFW_KEY_DOWN = 264
    const val GLFW_KEY_UP = 265
    const val GLFW_KEY_LEFT_SHIFT = 340
    const val GLFW_KEY_LEFT_CONTROL = 341
    const val GLFW_KEY_LEFT_ALT = 342
    const val GLFW_KEY_W = 87
    const val GLFW_KEY_A = 65
    const val GLFW_KEY_S = 83
    const val GLFW_KEY_D = 68
    const val GLFW_KEY_E = 69
    const val GLFW_KEY_Q = 81
    const val GLFW_MOUSE_BUTTON_1 = 0
    const val GLFW_MOUSE_BUTTON_2 = 1
    const val GLFW_MOUSE_BUTTON_3 = 2
    const val GLFW_MOUSE_BUTTON_LEFT = 0
    const val GLFW_MOUSE_BUTTON_RIGHT = 1
    const val GLFW_MOUSE_BUTTON_MIDDLE = 2
    const val GLFW_CURSOR = 0x00033001
    const val GLFW_CURSOR_NORMAL = 0x00034001
    const val GLFW_CURSOR_HIDDEN = 0x00034002
    const val GLFW_CURSOR_DISABLED = 0x00034003
    const val GLFW_STICKY_KEYS = 0x00033002
    const val GLFW_STICKY_MOUSE_BUTTONS = 0x00033003
    const val GLFW_RESIZABLE = 0x00020003
    const val GLFW_VISIBLE = 0x00020004
    const val GLFW_DECORATED = 0x00020005
    const val GLFW_CLIENT_API = 0x00022001
    const val GLFW_CONTEXT_VERSION_MAJOR = 0x00022002
    const val GLFW_CONTEXT_VERSION_MINOR = 0x00022003
    const val GLFW_OPENGL_PROFILE = 0x00022008
    const val GLFW_OPENGL_CORE_PROFILE = 0x00032001
    const val GLFW_OPENGL_COMPAT_PROFILE = 0x00032002
    const val GLFW_OPENGL_FORWARD_COMPAT = 0x00022006
    const val GLFW_OPENGL_DEBUG_CONTEXT = 0x00022007
    const val GLFW_NO_API = 0
    const val GLFW_OPENGL_API = 0x00030001
    const val GLFW_OPENGL_ES_API = 0x00030002
    const val GLFW_DOUBLEBUFFER = 0x00021010
    const val GLFW_DEPTH_BITS = 0x00021005
    const val GLFW_STENCIL_BITS = 0x00021006
    const val GLFW_SAMPLES = 0x0002100D
    const val GLFW_SRGB_CAPABLE = 0x0002100E
    const val GLFW_REFRESH_RATE = 0x0002200F
    const val GLFW_DONT_CARE = -1
    const val GLFW_NO_ERROR = 0

    private const val SINGLE_WINDOW = 1L
    private const val PRIMARY_MONITOR = 1L
    private var initialized = false

    // Callback registry (stored; native→Java upcall not wired in Spike 0).
    private val callbacks = HashMap<Int, Any>()
    private var nextCallbackHandle = 100L

    // --- Lifecycle ---
    @JvmStatic fun init(): Boolean {
        if (!NativeBridge.isNativeLoaded) return false
        initialized = NativeBridge.nativeGlfwInit()
        return initialized
    }
    @JvmStatic fun terminate() {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwTerminate()
        initialized = false
        callbacks.clear()
    }
    @JvmStatic fun initHint(hint: Int, value: Int) { /* no-op in stub */ }

    // --- Window hints ---
    @JvmStatic fun windowHint(hint: Int, value: Int) { /* stored in native */ }
    @JvmStatic fun windowHintString(hint: Int, value: String?) { /* no-op */ }
    @JvmStatic fun defaultWindowHints() { /* native resets on init */ }

    // --- Window ---
    @JvmStatic fun createWindow(width: Int, height: Int, title: String?, monitor: Long, share: Long): Long {
        if (!initialized) return 0L
        // Native window is created at attachSurface time; return handle.
        return SINGLE_WINDOW
    }
    @JvmStatic fun destroyWindow(window: Long) {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwDetachSurface()
    }
    @JvmStatic fun windowShouldClose(window: Long): Boolean =
        NativeBridge.isNativeLoaded && NativeBridge.nativeGlfwWindowShouldClose()
    @JvmStatic fun setWindowShouldClose(window: Long, value: Boolean) {
        if (value && NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwPostClose()
    }
    @JvmStatic fun setWindowTitle(window: Long, title: String?) { /* no-op on Android */ }
    @JvmStatic fun setWindowSize(window: Long, width: Int, height: Int) {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwPostResize(width, height)
    }
    @JvmStatic fun getWindowSize(window: Long, width: IntArray?, height: IntArray?) {
        // Spike 0: caller should use framebuffer size from Surface; return 0.
        width?.set(0, 0); height?.set(0, 0)
    }
    @JvmStatic fun getFramebufferSize(window: Long, width: IntArray?, height: IntArray?) {
        width?.set(0, 0); height?.set(0, 0)
    }
    @JvmStatic fun getWindowPos(window: Long, xpos: IntArray?, ypos: IntArray?) {
        xpos?.set(0, 0); ypos?.set(0, 0)
    }
    @JvmStatic fun iconifyWindow(window: Long) {}
    @JvmStatic fun restoreWindow(window: Long) {}
    @JvmStatic fun maximizeWindow(window: Long) {}
    @JvmStatic fun showWindow(window: Long) {}
    @JvmStatic fun hideWindow(window: Long) {}
    @JvmStatic fun focusWindow(window: Long) {}
    @JvmStatic fun getWindowAttrib(window: Long, attrib: Int): Int = 0

    // --- Context / GL ---
    @JvmStatic fun makeContextCurrent(window: Long) {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwMakeContextCurrent()
    }
    @JvmStatic fun getCurrentContext(): Long = if (initialized) SINGLE_WINDOW else 0L
    @JvmStatic fun swapBuffers(window: Long) {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwSwapBuffers()
    }
    @JvmStatic fun swapInterval(interval: Int) { /* EGL swap interval set in native */ }
    @JvmStatic fun extensionSupported(extension: String?): Boolean = false
    @JvmStatic fun getProcAddress(procname: String?): Long {
        if (procname == null || !NativeBridge.isNativeLoaded) return 0L
        return NativeBridge.nativeGlGetProcAddress(procname)
    }

    // --- Events ---
    @JvmStatic fun pollEvents() {
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwPollEvents()
    }
    @JvmStatic fun waitEvents() { pollEvents() }
    @JvmStatic fun waitEventsTimeout(timeout: Double) { pollEvents() }
    @JvmStatic fun postEmptyEvent() {}

    // --- Input query ---
    @JvmStatic fun getKey(window: Long, key: Int): Int = 0 // TODO: wire via native query
    @JvmStatic fun getMouseButton(window: Long, button: Int): Int = 0
    @JvmStatic fun getCursorPos(window: Long, xpos: DoubleArray?, ypos: DoubleArray?) {
        xpos?.set(0, 0.0); ypos?.set(0, 0.0)
    }
    @JvmStatic fun setCursorPos(window: Long, xpos: Double, ypos: Double) {}
    @JvmStatic fun setInputMode(window: Long, mode: Int, value: Int) {
        if (NativeBridge.isNativeLoaded && mode == GLFW_CURSOR) NativeBridge.nativeSetMouseCapture(value)
    }
    @JvmStatic fun getInputMode(window: Long, mode: Int): Int =
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGetMouseCapture() else GLFW_CURSOR_NORMAL
    @JvmStatic fun rawMouseMotionSupported(): Boolean = false

    // --- Time ---
    @JvmStatic fun getTime(): Double =
        if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwGetTime() else 0.0
    @JvmStatic fun setTime(time: Double) {}
    @JvmStatic fun getTimerValue(): Long = System.nanoTime()
    @JvmStatic fun getTimerFrequency(): Long = 1_000_000_000L

    // --- Monitor ---
    @JvmStatic fun getPrimaryMonitor(): Long = PRIMARY_MONITOR
    @JvmStatic fun getMonitors(): LongArray? = longArrayOf(PRIMARY_MONITOR)
    @JvmStatic fun getVideoMode(monitor: Long): Long = 1L // synthetic handle
    @JvmStatic fun getVideoModes(monitor: Long): LongArray? = longArrayOf(1L)
    @JvmStatic fun getMonitorName(monitor: Long): String = "Flint Android Display"
    @JvmStatic fun getMonitorPhysicalSize(monitor: Long, width: IntArray?, height: IntArray?) {
        width?.set(0, 120); height?.set(0, 70)
    }

    // --- Clipboard ---
    @JvmStatic fun setClipboardString(window: Long, text: String?) {}
    @JvmStatic fun getClipboardString(window: Long): String = ""

    // --- Version ---
    @JvmStatic fun getVersion(major: IntArray?, minor: IntArray?, rev: IntArray?) {
        major?.set(0, 3); minor?.set(0, 4); rev?.set(0, 0)
    }
    @JvmStatic fun getVersionString(): String =
        if (NativeBridge.isNativeLoaded) NativeBridge.safeGlfwVersionString()
        else "GLFW Flint stub (native not loaded)"

    // --- Cursor (stub) ---
    @JvmStatic fun createCursor(image: Long?, xhot: Int, yhot: Int): Long = 1L
    @JvmStatic fun createStandardCursor(shape: Int): Long = 1L
    @JvmStatic fun destroyCursor(cursor: Long) {}
    @JvmStatic fun setCursor(window: Long, cursor: Long) {}

    // --- Key name ---
    @JvmStatic fun getKeyName(key: Int, scancode: Int): String? = null
    @JvmStatic fun getKeyScancode(key: Int): Int = -1

    // --- Callbacks (API-only: stored, native→Java upcall not wired in Spike 0) ---
    private fun registerCallback(kind: Int, cb: Any?): Long {
        if (cb == null) { callbacks.remove(kind); return 0L }
        callbacks[kind] = cb
        return nextCallbackHandle++
    }
    @JvmStatic fun setErrorCallback(cb: Any?): Long = registerCallback(0, cb)
    @JvmStatic fun setKeyCallback(window: Long, cb: Any?): Long = registerCallback(1, cb)
    @JvmStatic fun setCharCallback(window: Long, cb: Any?): Long = registerCallback(2, cb)
    @JvmStatic fun setCharModsCallback(window: Long, cb: Any?): Long = registerCallback(3, cb)
    @JvmStatic fun setMouseButtonCallback(window: Long, cb: Any?): Long = registerCallback(4, cb)
    @JvmStatic fun setCursorPosCallback(window: Long, cb: Any?): Long = registerCallback(5, cb)
    @JvmStatic fun setCursorEnterCallback(window: Long, cb: Any?): Long = registerCallback(6, cb)
    @JvmStatic fun setScrollCallback(window: Long, cb: Any?): Long = registerCallback(7, cb)
    @JvmStatic fun setWindowSizeCallback(window: Long, cb: Any?): Long = registerCallback(8, cb)
    @JvmStatic fun setFramebufferSizeCallback(window: Long, cb: Any?): Long = registerCallback(9, cb)
    @JvmStatic fun setWindowCloseCallback(window: Long, cb: Any?): Long = registerCallback(10, cb)
    @JvmStatic fun setWindowRefreshCallback(window: Long, cb: Any?): Long = registerCallback(11, cb)
    @JvmStatic fun setWindowFocusCallback(window: Long, cb: Any?): Long = registerCallback(12, cb)
    @JvmStatic fun setWindowIconifyCallback(window: Long, cb: Any?): Long = registerCallback(13, cb)
    @JvmStatic fun setWindowPosCallback(window: Long, cb: Any?): Long = registerCallback(14, cb)
    @JvmStatic fun setDropCallback(window: Long, cb: Any?): Long = registerCallback(15, cb)
    @JvmStatic fun setMonitorCallback(cb: Any?): Long = registerCallback(16, cb)
    @JvmStatic fun setJoystickCallback(cb: Any?): Long = registerCallback(17, cb)

    // --- Joystick / gamepad (stub: not present) ---
    @JvmStatic fun joystickPresent(jid: Int): Boolean = false
    @JvmStatic fun getJoystickAxes(jid: Int): FloatArray? = null
    @JvmStatic fun getJoystickButtons(jid: Int): ByteArray? = null
    @JvmStatic fun getJoystickName(jid: Int): String? = null
    @JvmStatic fun joystickIsGamepad(jid: Int): Boolean = false
    @JvmStatic fun getGamepadName(jid: Int): String? = null
    @JvmStatic fun updateGamepadMappings(mapping: String?): Boolean = false

    // --- Unimplemented methods throw clearly. ---
    private fun unimplemented(name: String): Nothing =
        throw UnsupportedOperationException(
            "GLFW.$name is not implemented in Flint Spike-0 shim. " +
                "See docs/progress-shard-native.md for the implemented subset.")
}
