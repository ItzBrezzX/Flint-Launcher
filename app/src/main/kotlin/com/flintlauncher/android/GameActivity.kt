package com.flintlauncher.android

import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
import android.graphics.Typeface
import android.util.Log
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import com.flintlauncher.android.nativebridge.FlintGameSurfaceView
import com.flintlauncher.android.nativebridge.NativeBridge
import com.flintlauncher.android.runtime.LaunchResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the Android Surface for one Minecraft session.
 *
 * The Surface itself is passed to the runtime, but EGL and GLFW are created and
 * destroyed only by the dedicated JLI thread inside PojavStyleGameLauncher.
 */
class GameActivity : ComponentActivity(), SurfaceHolder.Callback {

    private val activityJob = SupervisorJob()
    private val activityScope = CoroutineScope(activityJob + Dispatchers.Main.immediate)

    private lateinit var surfaceView: FlintGameSurfaceView
    private lateinit var statusView: TextView
    private lateinit var stopButton: Button
    private lateinit var keyboardInputView: FlintKeyboardInputView
    private lateinit var statusPanel: LinearLayout

    private var launchRequested = false
    private var launchComplete = false
    private var gameThreadStarted = false
    private var surfaceAvailable = false
    private var finishingAfterStop = false
    private val startupLines = ArrayDeque<String>()

    private val coordinator
        get() = (application as FlintApplication).container.playLaunchCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                stopGame(finishWhenStopped = true)
            }
        })

        val versionId = intent.getStringExtra(EXTRA_VERSION_ID)?.takeIf { it.isNotBlank() }
        if (versionId == null) {
            finish()
            return
        }

        if (NativeBridge.isNativeLoaded) {
            NativeBridge.nativeSetNativeLibraryDir(applicationInfo.nativeLibraryDir)
            NativeBridge.nativePreferPerformanceCores(true)
        }

        surfaceView = FlintGameSurfaceView(this)
        surfaceView.holder.addCallback(this)
        surfaceView.keepScreenOn = true
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)

        statusView = TextView(this).apply {
            text = "FLINT GAME STARTUP\n> Waiting for Android game Surface…"
            textSize = 12f
            typeface = Typeface.MONOSPACE
            maxLines = 10
            setTextIsSelectable(true)
            setTextColor(0xffe7eef2.toInt())
            setPadding(14, 10, 14, 10)
            setBackgroundColor(0xe6101820.toInt())
            setOnLongClickListener {
                val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Flint startup log", text))
                android.widget.Toast.makeText(this@GameActivity, "Startup log copied", android.widget.Toast.LENGTH_SHORT).show()
                true
            }
        }
        stopButton = Button(this).apply {
            text = "STOP"
            isEnabled = false
            visibility = View.GONE
            setOnClickListener { stopGame(finishWhenStopped = false) }
        }

        keyboardInputView = FlintKeyboardInputView(this)
        val touchControls = FlintTouchControlsOverlay(this).apply {
            onKeyboardRequested = { keyboardInputView.showKeyboard() }
        }
        statusPanel = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(statusView)
            addView(stopButton)
            alpha = 1f
        }
        val root = FrameLayout(this)
        root.addView(
            surfaceView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            touchControls,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
            ),
        )
        root.addView(
            keyboardInputView,
            FrameLayout.LayoutParams(
                dp(1),
                dp(1),
                Gravity.BOTTOM or Gravity.END,
            ),
        )
        root.addView(
            statusPanel,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.START,
            ).apply {
                marginStart = dp(12)
                topMargin = dp(12)
            },
        )
        setContentView(root)
        surfaceView.requestFocus()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        surfaceAvailable = holder.surface.isValid
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (NativeBridge.isNativeLoaded && width > 0 && height > 0) {
            val hz = runCatching { displayRefreshRate() }.getOrDefault(60f)
            NativeBridge.nativeSetDisplayMetrics(width, height, hz)
        }
        surfaceAvailable = holder.surface.isValid && width > 0 && height > 0
        if (!surfaceAvailable) return
        surfaceView.notifySurfaceSize(width, height)
        if (launchRequested) return

        val versionId = intent.getStringExtra(EXTRA_VERSION_ID) ?: return
        launchRequested = true
        appendStartupLine("Android Surface ready: ${width}x${height}")
        appendStartupLine("Starting Minecraft ${versionId}…")

        activityScope.launch {
            val logObserver = launch {
                var consumed = ""
                while (isActive && (!launchComplete || coordinator.isGameRunning())) {
                    val output = runCatching { coordinator.gameLogs() }.getOrDefault("")
                    if (output.length > consumed.length && output.startsWith(consumed)) {
                        output.substring(consumed.length).lineSequence()
                            .filter { it.isNotBlank() }
                            .toList()
                            .takeLast(8)
                            .forEach { appendStartupLine(it) }
                    } else if (output.length < consumed.length) {
                        output.lineSequence().filter { it.isNotBlank() }.toList().takeLast(4)
                            .forEach { appendStartupLine(it) }
                    }
                    consumed = output
                    delay(180)
                }
            }

            val result = try {
                coordinator.launch(versionId, holder.surface, width, height) { stage ->
                    runOnUiThread { appendStartupLine(stage) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                Log.e(TAG, "Game startup failed", error)
                val details = formatLaunchFailure(error)
                saveLaunchFailure(details)
                LaunchResult.Failed(details)
            } finally {
                launchComplete = true
                logObserver.cancel()
            }

            when (result) {
                is LaunchResult.Started -> {
                    gameThreadStarted = true
                    stopButton.isEnabled = true
                    stopButton.visibility = View.VISIBLE
                    appendStartupLine("JLI started on the EGL owner thread.")
                    appendStartupLine("Waiting for the first Minecraft frame…")
                    activityScope.launch { hideStartupPanelAfterFirstFrame() }
                    watchGameThread()
                }
                is LaunchResult.Failed -> {
                    stopButton.isEnabled = false
                    stopButton.visibility = View.GONE
                    statusPanel.visibility = View.VISIBLE
                    val failureReport = buildString {
                        appendLine(result.reason)
                        result.logSnippet?.takeIf { it.isNotBlank() }?.let(::append)
                    }
                    saveLaunchFailure(failureReport)
                    appendStartupLine("SETUP FAILED")
                    val usefulLines = failureReport.lineSequence()
                        .map { it.trim() }
                        .filter { line ->
                            line.isNotBlank() &&
                                (line.contains("Exception", ignoreCase = true) ||
                                    line.contains("Error", ignoreCase = true) ||
                                    line.startsWith("Caused by:") ||
                                    line.startsWith("at "))
                        }
                        .distinct()
                        .take(8)
                    usefulLines.forEach { appendStartupLine(it) }
                    if (startupLines.size <= 2) appendStartupLine(result.reason.take(180))
                }
                LaunchResult.BlockedMissingRuntime -> {
                    stopButton.visibility = View.GONE
                    appendStartupLine("BLOCKED: a compatible verified Java runtime is required.")
                }
                LaunchResult.BlockedMissingRenderer -> {
                    stopButton.visibility = View.GONE
                    appendStartupLine("BLOCKED: packaged GL4ES renderer is missing.")
                }
                LaunchResult.BlockedNotReady -> {
                    stopButton.visibility = View.GONE
                    appendStartupLine("BLOCKED: Minecraft installation or account is not ready.")
                }
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        surfaceAvailable = false
        if (launchRequested) {
            // Do not depend on the UI having received LaunchResult.Started yet:
            // JLI may already own the Surface while the setup callback is pending.
            statusView.post { statusView.text = "Surface destroyed; requesting graceful Minecraft close…" }
            activityScope.launch {
                coordinator.stopGame()
                if (!isFinishing) {
                    statusView.text = if (coordinator.isGameRunning()) {
                        "Minecraft did not close after the Surface was destroyed. Restart the launcher if it remains stuck."
                    } else {
                        "Minecraft session stopped. Return to PLAY to start a new session."
                    }
                    stopButton.isEnabled = false
                    gameThreadStarted = coordinator.isGameRunning()
                }
            }
        }
    }

    private fun appendStartupLine(line: String) {
        if (line.isBlank()) return
        startupLines.addLast("> " + line.trim())
        while (startupLines.size > 10) startupLines.removeFirst()
        if (::statusView.isInitialized) {
            statusView.text = "FLINT GAME STARTUP\n" + startupLines.joinToString("\n")
        }
    }

    private fun formatLaunchFailure(error: Throwable): String {
        val causes = generateSequence(error) { it.cause }.take(8).toList()
        val summary = causes.joinToString("\n") { cause ->
            cause.javaClass.name + cause.message?.let { ": $it" }.orEmpty()
        }
        val trace = error.stackTraceToString().lineSequence().take(18).joinToString("\n")
        return "Game startup failed\n$summary\n\nStack trace:\n$trace"
    }

    private fun saveLaunchFailure(details: String) {
        runCatching {
            val directory = java.io.File(filesDir, "launch-reports")
            check(directory.isDirectory || directory.mkdirs())
            java.io.File(directory, "latest-launch-error.txt").writeText(details)
        }.onFailure { Log.e(TAG, "Could not save startup error report", it) }
    }

    private suspend fun hideStartupPanelAfterFirstFrame() {
        val start = SystemClock.elapsedRealtime()
        var waitingNoticeShown = false
        while (activityScope.isActive && coordinator.isGameRunning()) {
            val frameCount = runCatching {
                if (NativeBridge.isNativeLoaded) NativeBridge.nativeGlfwPresentedFrameCount() else 0L
            }.getOrDefault(0L)
            if (frameCount > 0L) {
                statusPanel.animate()
                    .alpha(0f)
                    .setDuration(180)
                    .withEndAction {
                        statusPanel.visibility = View.GONE
                        statusPanel.alpha = 1f
                    }
                    .start()
                return
            }
            if (!waitingNoticeShown && SystemClock.elapsedRealtime() - start > 12_000L) {
                appendStartupLine("Still waiting for Minecraft to render its first frame; native/JVM logs are being collected.")
                waitingNoticeShown = true
            }
            delay(250)
        }
    }

    private suspend fun watchGameThread() {
        while (activityScope.isActive && coordinator.isGameRunning()) {
            delay(750)
        }
        if (!activityScope.isActive || isFinishing) return

        gameThreadStarted = false
        stopButton.isEnabled = false
        stopButton.visibility = View.GONE
        statusPanel.visibility = View.VISIBLE
        val logs = coordinator.gameLogs()
        appendStartupLine(if (logs.isBlank()) {
            "Minecraft JVM exited before a confirmed game session."
        } else {
            "Minecraft JVM exited. Last runtime output follows:"
        })
        if (logs.isNotBlank()) {
            logs.lineSequence().filter { it.isNotBlank() }.toList().takeLast(8)
                .forEach { appendStartupLine(it) }
        }
    }

    private fun stopGame(finishWhenStopped: Boolean) {
        if (!gameThreadStarted) {
            if (launchRequested && !launchComplete) {
                statusView.text = "Minecraft is still preparing its runtime and game Surface. Wait for setup to finish before closing."
            } else if (finishWhenStopped) {
                finish()
            } else {
                statusView.text = "No active Minecraft session."
            }
            return
        }
        finishingAfterStop = finishWhenStopped
        stopButton.isEnabled = false
        statusView.text = "Requesting GLFW close on the Minecraft game thread…"

        activityScope.launch {
            try {
                coordinator.stopGame()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                statusView.text = "Could not request graceful close: ${error.message}"
            }
            val stillRunning = coordinator.isGameRunning()
            gameThreadStarted = stillRunning
            if (stillRunning) {
                statusView.text =
                    "Minecraft has not exited yet. EGL was kept on its owner thread; wait for the game to respond or restart the app."
                stopButton.isEnabled = true
            } else {
                statusView.text = "Minecraft stopped. EGL and GLFW were released by the game thread."
                if (finishingAfterStop) finish()
            }
        }
    }

    override fun onDestroy() {
        if (gameThreadStarted) {
            // Queue only. Never destroy a Surface/EGL context from Activity's UI thread.
            runCatching {
                com.flintlauncher.android.nativebridge.FlintNativeWindowBridge.requestWindowClose()
            }
        }
        if (::surfaceView.isInitialized) surfaceView.keepScreenOn = false
        activityScope.cancel()
        super.onDestroy()
    }

    @Suppress("DEPRECATION")
    private fun displayRefreshRate(): Float =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.mode?.refreshRate ?: 60f
        } else {
            windowManager.defaultDisplay.refreshRate
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "FlintGameActivity"
        const val EXTRA_VERSION_ID = "com.flintlauncher.android.extra.VERSION_ID"
    }
}
