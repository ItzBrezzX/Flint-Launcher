package com.flintlauncher.android

import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.SurfaceHolder
import android.view.View
import android.view.WindowManager
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
            text = "Preparing Minecraft $versionId…"
            textSize = 13f
            setTextColor(0xffffffff.toInt())
            setPadding(18, 12, 18, 12)
            setBackgroundColor(0xcc101820.toInt())
        }
        stopButton = Button(this).apply {
            text = "STOP"
            isEnabled = false
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
        statusView.text = "Preparing Android EGL + GL4ES + GLFW for Minecraft $versionId…"

        activityScope.launch {
            val result = try {
                coordinator.launch(versionId, holder.surface, width, height)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                LaunchResult.Failed(
                    "Game setup failed: " + (error.message ?: error.javaClass.simpleName),
                )
            }

            launchComplete = true
            when (result) {
                is LaunchResult.Started -> {
                    gameThreadStarted = true
                    stopButton.isEnabled = true
                    statusPanel.visibility = View.GONE
                    statusView.text =
                        "JLI entered on the Surface/EGL owner thread. Minecraft title screen is not yet confirmed."
                    watchGameThread()
                }
                is LaunchResult.Failed -> {
                    stopButton.isEnabled = false
                    statusView.text = result.reason
                }
                LaunchResult.BlockedMissingRuntime -> {
                    statusView.text = "A compatible verified Java runtime is required."
                }
                LaunchResult.BlockedMissingRenderer -> {
                    statusView.text = "The packaged GL4ES renderer is missing."
                }
                LaunchResult.BlockedNotReady -> {
                    statusView.text = "Minecraft installation or account is not ready."
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

    private suspend fun watchGameThread() {
        while (activityScope.isActive && coordinator.isGameRunning()) {
            delay(750)
        }
        if (!activityScope.isActive || isFinishing) return

        gameThreadStarted = false
        stopButton.isEnabled = false
        statusPanel.visibility = View.VISIBLE
        val logs = coordinator.gameLogs().lineSequence().filter { it.isNotBlank() }.toList().takeLast(4).joinToString("\n")
        statusView.text = if (logs.isBlank()) {
            "Minecraft JVM exited. The game did not report a confirmed title-screen state."
        } else {
            "Minecraft JVM exited.\n$logs"
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
        const val EXTRA_VERSION_ID = "com.flintlauncher.android.extra.VERSION_ID"
    }
}
