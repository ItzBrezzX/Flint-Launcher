package com.flintlauncher.android

import android.app.Activity
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.system.Os
import android.util.Log
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.flintlauncher.android.nativebridge.FlintGlfwInputMapper
import com.flintlauncher.android.nativebridge.FlintInputEvent
import com.flintlauncher.android.nativebridge.NativeBridge
import com.flintlauncher.android.runtime.LaunchRequest
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Full-screen Minecraft host.
 *
 * Order of work (inspired by Android desktop-port launchers, Flint-owned code):
 * 1. Publish nativeLibraryDir + display metrics to the host layer
 * 2. Create EGL window from the Android Surface
 * 3. Initialize GL4ES and the Flint GLFW backend
 * 4. Apply process environment and start JLI on a worker thread
 * 5. Forward touch/key events into the native input queue
 */
class GameActivity : Activity(), SurfaceHolder.Callback {

    private lateinit var surfaceView: SurfaceView
    private lateinit var statusView: TextView
    private var setupThread: Thread? = null
    private val launchStarted = AtomicBoolean(false)
    private var surfaceWidth = 0
    private var surfaceHeight = 0
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        if (PendingLaunchStore.peek() == null) {
            finishWithError("No prepared Minecraft launch is waiting.")
            return
        }
        if (!NativeBridge.isNativeLoaded) {
            finishWithError("Native library not loaded: " + (NativeBridge.nativeLoadError ?: "unknown"))
            return
        }

        // Host registration happens before the surface thread runs.
        NativeBridge.nativeSetNativeLibraryDir(applicationInfo.nativeLibraryDir)
        NativeBridge.nativePreferPerformanceCores(true)

        surfaceView = SurfaceView(this).apply {
            holder.setFormat(PixelFormat.RGBA_8888)
            holder.addCallback(this@GameActivity)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(16, 12, 16, 12)
            setBackgroundColor(0xB9000000.toInt())
            text = "Preparing Android surface for Minecraft…"
        }

        setContentView(
            FrameLayout(this).apply {
                addView(
                    surfaceView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT,
                    ),
                )
                addView(
                    statusView,
                    FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        Gravity.TOP or Gravity.START,
                    ),
                )
            },
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        if (setupThread?.isAlive == true) return
        val thread = Thread({ prepareAndLaunch(holder) }, "Flint-Game-Setup")
        setupThread = thread
        thread.start()
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        val hz = displayRefreshHz()
        NativeBridge.nativeSetDisplayMetrics(width, height, hz)
        if (width > 0 && height > 0) {
            runCatching { NativeBridge.nativeResizeWindowSurface(width, height) }
            enqueue(FlintInputEvent.WINDOW_SIZE, x = width.toDouble(), y = height.toDouble())
            enqueue(FlintInputEvent.FRAMEBUFFER_SIZE, x = width.toDouble(), y = height.toDouble())
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        runCatching { NativeBridge.nativeGlfwSetWindowShouldClose(true) }
        runCatching { NativeBridge.nativeDestroyWindowSurface() }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                activePointerId = event.getPointerId(event.actionIndex)
                val i = event.findPointerIndex(activePointerId)
                if (i >= 0) {
                    enqueue(FlintInputEvent.CURSOR, x = event.getX(i).toDouble(), y = event.getY(i).toDouble())
                    enqueue(FlintInputEvent.MOUSE_BUTTON, code = 0, action = 1)
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val i = event.findPointerIndex(activePointerId)
                if (i >= 0) {
                    enqueue(FlintInputEvent.CURSOR, x = event.getX(i).toDouble(), y = event.getY(i).toDouble())
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP, MotionEvent.ACTION_CANCEL -> {
                val i = event.findPointerIndex(activePointerId)
                if (i >= 0) {
                    enqueue(FlintInputEvent.CURSOR, x = event.getX(i).toDouble(), y = event.getY(i).toDouble())
                    enqueue(FlintInputEvent.MOUSE_BUTTON, code = 0, action = 0)
                }
                activePointerId = MotionEvent.INVALID_POINTER_ID
            }
        }
        return true
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val glfwKey = FlintGlfwInputMapper.key(keyCode)
        if (glfwKey != 0) {
            enqueue(FlintInputEvent.KEY, code = glfwKey, action = 1)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        val glfwKey = FlintGlfwInputMapper.key(keyCode)
        if (glfwKey != 0) {
            enqueue(FlintInputEvent.KEY, code = glfwKey, action = 0)
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun prepareAndLaunch(holder: SurfaceHolder) {
        val request = PendingLaunchStore.take()
        if (request == null) {
            finishWithError("Launch request disappeared before the surface was ready.")
            return
        }
        try {
            val width = surfaceWidth.takeIf { it > 0 } ?: surfaceView.width.takeIf { it > 0 } ?: 1280
            val height = surfaceHeight.takeIf { it > 0 } ?: surfaceView.height.takeIf { it > 0 } ?: 720
            val hz = displayRefreshHz()
            NativeBridge.nativeSetDisplayMetrics(width, height, hz)

            setStatus("Creating EGL window surface…")
            val eglStatus = NativeBridge.nativeCreateWindowSurface(holder.surface)
            if (eglStatus != "egl-window-ready") {
                throw IllegalStateException("EGL window surface failed: $eglStatus")
            }
            NativeBridge.nativeResizeWindowSurface(width, height)

            setStatus("Loading GL4ES…")
            val glPath = File(applicationInfo.nativeLibraryDir, "libGL.so").absolutePath
            if (!NativeBridge.nativeLoadGL4ES(glPath)) {
                throw IllegalStateException("GL4ES failed to initialize ($glPath)")
            }

            setStatus("Attaching Flint GLFW backend…")
            if (!NativeBridge.nativeGlfwAttachAndroidSurface(width, height)) {
                throw IllegalStateException("GLFW could not attach the Android EGL surface")
            }
            if (!NativeBridge.nativeGlfwInitialize()) {
                throw IllegalStateException("glfwInit failed after Android surface attach")
            }
            if (!NativeBridge.nativeGlfwCreateWindow(width, height, "Minecraft")) {
                throw IllegalStateException("glfwCreateWindow failed for the Android surface")
            }

            applyProcessEnvironment(request)
            setStatus("Starting Minecraft JVM…")
            if (!launchStarted.compareAndSet(false, true)) return
            startJvm(request)
        } catch (error: Throwable) {
            val message = error.message ?: error.javaClass.simpleName
            Log.e(TAG, "Game surface setup failed", error)
            PendingLaunchStore.reportError(message)
            finishWithError(message)
        }
    }

    private fun applyProcessEnvironment(request: LaunchRequest) {
        val nativeLibDir = applicationInfo.nativeLibraryDir
        val jreLibCandidates = listOf(
            File(request.runtime.homeDir, "lib"),
            File(request.runtime.homeDir, "lib/aarch64"),
            File(request.runtime.homeDir, "lib/arm"),
            File(request.runtime.homeDir, "lib/x86_64"),
            File(request.runtime.homeDir, "lib/i386"),
            File(request.runtime.homeDir, "lib/server"),
            File(request.runtime.homeDir, "lib/jli"),
        ).filter { it.isDirectory }.map { it.absolutePath }

        val ldPath = buildList {
            add(request.nativeDir.absolutePath)
            add(nativeLibDir)
            addAll(jreLibCandidates)
            System.getenv("LD_LIBRARY_PATH")?.let { add(it) }
        }.distinct().joinToString(":")

        fun put(key: String, value: String) {
            try {
                Os.setenv(key, value, true)
            } catch (error: Throwable) {
                Log.w(TAG, "Os.setenv($key) failed: ${error.message}")
            }
        }
        put("LD_LIBRARY_PATH", ldPath)
        put("JAVA_HOME", request.runtime.homeDir.absolutePath)
        put("LIBGL_ES", "2")
        put("LIBGL_MIPMAP", "3")
        put("LIBGL_NOERROR", "1")
    }

    private fun startJvm(request: LaunchRequest) {
        Thread({
            try {
                NativeBridge.nativePreferPerformanceCores(true)
                if (!NativeBridge.nativeLoadJre(request.runtime.homeDir.absolutePath)) {
                    throw IllegalStateException("Failed to load libjli from ${request.runtime.homeDir.absolutePath}")
                }
                val args = mutableListOf<String>()
                args += "java"
                args += request.jvmArgs
                if (request.jvmArgs.none { it == "-cp" || it == "-classpath" }) {
                    args += "-cp"
                    args += request.classpath.joinToString(File.pathSeparator) { it.absolutePath }
                }
                args += request.mainClass
                args += request.gameArgs
                setStatus("JLI_Launch ${request.versionId} as ${request.accountUsername}…")
                val code = NativeBridge.nativeLaunchJvm(args.toTypedArray())
                setStatus("Minecraft JVM returned exit code $code")
                if (code != 0) {
                    PendingLaunchStore.reportError("Minecraft JVM exited with code $code")
                }
            } catch (error: Throwable) {
                val message = error.message ?: error.javaClass.simpleName
                Log.e(TAG, "JVM launch failed", error)
                PendingLaunchStore.reportError(message)
                setStatus("Launch failed: $message")
            }
        }, "Flint-Minecraft-JVM").start()
    }

    private fun enqueue(
        type: Int,
        code: Int = 0,
        action: Int = 0,
        modifiers: Int = 0,
        x: Double = 0.0,
        y: Double = 0.0,
    ) {
        if (!NativeBridge.isNativeLoaded) return
        runCatching {
            NativeBridge.nativePushInputEvent(type, code, action, modifiers, x, y)
        }
    }

    private fun displayRefreshHz(): Float {
        return try {
            if (Build.VERSION.SDK_INT >= 30) {
                display?.mode?.refreshRate ?: 60f
            } else {
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.refreshRate
            }
        } catch (_: Throwable) {
            60f
        }
    }

    private fun setStatus(text: String) {
        Log.i(TAG, text)
        runOnUiThread { statusView.text = text }
    }

    private fun finishWithError(message: String) {
        setStatus(message)
        runOnUiThread {
            statusView.text = message
            statusView.postDelayed({ finish() }, 2500)
        }
    }

    override fun onDestroy() {
        runCatching { NativeBridge.nativeGlfwSetWindowShouldClose(true) }
        runCatching { NativeBridge.nativeShutdown() }
        super.onDestroy()
    }

    companion object {
        private const val TAG = "FlintGameActivity"
    }
}
