package com.flintlauncher.android

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.flintlauncher.android.game.GameControlsView
import com.flintlauncher.android.game.GameInput
import com.flintlauncher.android.game.GameSurfaceView
import com.flintlauncher.android.nativebridge.GlfwBridge
import com.flintlauncher.android.nativebridge.GlfwKeys
import com.flintlauncher.android.runtime.LaunchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The game screen. Runs in its own process (":game", see the manifest) because the JVM lives inside
 * the app process and Minecraft ends with System.exit(): that must not take the launcher down, and a
 * fresh process is the only way to start a second game session.
 *
 * Order of events: the SurfaceView appears -> its Surface is published to Flint's GLFW shim ->
 * only then is the JVM started (Minecraft's glfwCreateWindow waits for that Surface).
 */
class FlintGameActivity : Activity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val handler = Handler(Looper.getMainLooper())

    private lateinit var surfaceView: GameSurfaceView
    private lateinit var statusPanel: LinearLayout
    private lateinit var statusText: TextView
    private lateinit var shareButton: Button
    private lateinit var closeButton: Button

    private var surfacePublished = false
    private var launchStarted = false
    private var gameStarted = false
    private var keyboardVisible = false
    private var startedAtMillis = 0L
    private var logFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= 28) {
            window.attributes = window.attributes.also {
                it.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        enterImmersive()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        surfaceView = GameSurfaceView(this)
        root.addView(surfaceView, FrameLayout.LayoutParams(-1, -1))
        root.addView(
            GameControlsView(this, onToggleKeyboard = ::toggleKeyboard, onQuitRequested = ::confirmQuit),
            FrameLayout.LayoutParams(-1, -1),
        )
        buildStatusPanel(root)
        setContentView(root)

        if (!GlfwBridge.isLoaded) {
            showProblem("Flint's game GLFW library (libflint_game_glfw.so) did not load: ${GlfwBridge.error ?: "unknown error"}")
            return
        }
        surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) = Unit

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                if (!surfacePublished) {
                    GlfwBridge.nativeSurfaceCreated(holder.surface, width, height)
                    surfacePublished = true
                    startLaunchOnce()
                } else {
                    GlfwBridge.nativeSurfaceChanged(width, height)
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                surfacePublished = false
                GlfwBridge.nativeSurfaceDestroyed()
            }
        })
    }

    // ------------------------------------------------------------------ launch

    private fun startLaunchOnce() {
        if (launchStarted) return
        launchStarted = true
        val versionId = intent.getStringExtra(EXTRA_VERSION_ID)
        if (versionId.isNullOrBlank()) {
            showProblem("No Minecraft version was given to the game screen.")
            return
        }
        val container = (application as FlintApplication).container
        logFile = container.gameLogFile
        setStatus("Preparing Minecraft $versionId…\nThe first launch downloads Java and can take a few minutes.", showButtons = false)

        scope.launch {
            val result = try {
                withContext(Dispatchers.IO) { container.playLaunchCoordinator.launch(versionId) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                LaunchResult.Failed("Launch preparation crashed: ${e.javaClass.simpleName}: ${e.message}")
            }
            when (result) {
                is LaunchResult.Started -> onJvmStarted()
                is LaunchResult.Failed -> showProblem(result.reason + (result.logSnippet?.takeIf { it.isNotBlank() }?.let { "\n\n$it" } ?: ""))
                LaunchResult.BlockedMissingRuntime -> showProblem("A compatible, verified Java runtime is required before Minecraft can start.")
                LaunchResult.BlockedMissingRenderer -> showProblem("The GL4ES renderer library (libGL.so) is not in the app's native library directory.")
                LaunchResult.BlockedNotReady -> showProblem("Install the selected version and choose an account first.")
            }
        }
    }

    private fun onJvmStarted() {
        gameStarted = true
        startedAtMillis = System.currentTimeMillis()
        setStatus("Starting Minecraft…", showButtons = false)
        handler.post(object : Runnable {
            override fun run() {
                if (isFinishing) return
                if (GlfwBridge.nativeFrameCount() > 0) {
                    statusPanel.visibility = View.GONE // the game is drawing: get out of the way
                    return
                }
                val waited = (System.currentTimeMillis() - startedAtMillis) / 1000
                val tail = logFile?.let(::tail).orEmpty()
                val note = if (waited > 120) "\n\nStill nothing after ${waited}s. Share the log if it seems stuck." else ""
                setStatus("Starting Minecraft… ${waited}s\n\n$tail$note", showButtons = waited > 120)
                handler.postDelayed(this, 1000)
            }
        })
    }

    // ---------------------------------------------------------------- status UI

    private fun buildStatusPanel(root: FrameLayout) {
        statusText = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(32, 24, 32, 12)
        }
        shareButton = Button(this).apply {
            text = "Share log"
            setOnClickListener { shareLog() }
        }
        closeButton = Button(this).apply {
            text = "Close game"
            setOnClickListener { quitProcess() }
        }
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            addView(shareButton)
            addView(closeButton)
        }
        statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.argb(215, 12, 16, 14))
            addView(statusText)
            addView(buttons)
        }
        root.addView(statusPanel, FrameLayout.LayoutParams(-1, -2, Gravity.CENTER))
    }

    private fun setStatus(message: String, showButtons: Boolean) {
        statusPanel.visibility = View.VISIBLE
        statusText.text = message
        shareButton.visibility = if (showButtons) View.VISIBLE else View.GONE
        closeButton.visibility = if (showButtons) View.VISIBLE else View.GONE
    }

    private fun showProblem(message: String) = setStatus("Minecraft could not start.\n\n$message", showButtons = true)

    private fun tail(file: File, maxChars: Int = 1500): String = try {
        if (!file.isFile) "" else file.readText().takeLast(maxChars).lines().takeLast(12).joinToString("\n")
    } catch (e: Exception) {
        ""
    }

    private fun shareLog() {
        val body = try {
            logFile?.takeIf { it.isFile }?.readText()?.takeLast(60_000) ?: "(no game log was written)"
        } catch (e: Exception) {
            "(could not read the log: ${e.message})"
        }
        val text = "Flint game log\n\n${statusText.text}\n\n----\n$body"
        startActivity(
            Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text),
                "Share log",
            ),
        )
    }

    // ------------------------------------------------------------------- input

    private fun toggleKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        if (keyboardVisible) {
            imm.hideSoftInputFromWindow(surfaceView.windowToken, 0)
        } else {
            surfaceView.requestFocus()
            imm.showSoftInput(surfaceView, 0)
        }
        keyboardVisible = !keyboardVisible
    }

    private fun confirmQuit() {
        AlertDialog.Builder(this)
            .setTitle("Close the game?")
            .setMessage("Unsaved progress is lost. Prefer \"Save and Quit\" in the Minecraft menu.")
            .setPositiveButton("Close game") { _, _ -> quitProcess() }
            .setNegativeButton("Keep playing", null)
            .show()
    }

    /** There is no JVM stop hook yet: ending the :game process is the reliable way to stop Minecraft. */
    private fun quitProcess() {
        finish()
        Process.killProcess(Process.myPid())
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (!gameStarted) {
                if (event.action == KeyEvent.ACTION_UP) quitProcess()
                return true
            }
            when (event.action) {
                KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) GameInput.key(GlfwKeys.KEY_ESCAPE, GlfwKeys.PRESS)
                KeyEvent.ACTION_UP -> GameInput.key(GlfwKeys.KEY_ESCAPE, GlfwKeys.RELEASE)
            }
            return true
        }
        if (gameStarted && GlfwBridge.isLoaded && GameInput.forwardKeyEvent(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: MotionEvent): Boolean {
        if (GlfwBridge.isLoaded && event.actionMasked == MotionEvent.ACTION_SCROLL) {
            GlfwBridge.nativeSendScroll(
                event.getAxisValue(MotionEvent.AXIS_HSCROLL).toDouble(),
                event.getAxisValue(MotionEvent.AXIS_VSCROLL).toDouble(),
            )
            return true
        }
        return super.dispatchGenericMotionEvent(event)
    }

    // --------------------------------------------------------------- lifecycle

    private fun enterImmersive() {
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterImmersive()
        if (GlfwBridge.isLoaded) GlfwBridge.nativeSendFocus(hasFocus)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        scope.cancel()
        super.onDestroy()
        // The JVM cannot be stopped cleanly from here. If the person leaves the game screen for good,
        // end the :game process so the next PLAY starts from a clean state.
        if (isFinishing) Process.killProcess(Process.myPid())
    }

    companion object {
        const val EXTRA_VERSION_ID = "flint.version_id"
    }
}
