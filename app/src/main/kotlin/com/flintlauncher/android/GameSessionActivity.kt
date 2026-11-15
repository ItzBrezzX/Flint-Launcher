package com.flintlauncher.android

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.flintlauncher.android.nativebridge.GL4ESSurfaceView
import com.flintlauncher.android.ui.session.GameSessionScreen
import com.flintlauncher.android.ui.state.GameSessionActions
import com.flintlauncher.android.ui.theme.FlintTheme

/**
 * Fullscreen landscape host for a running Minecraft session. Applies immersive sticky
 * immersive mode, keeps the screen on, and allows display cutout. The real Android
 * Surface is bound via GL4ESSurfaceView (owned by :native — used here, not edited).
 *
 * BLOCKED (device only): actual Minecraft main menu rendering / world entry / gameplay
 * require the LWJGL/GLFW Android hand-off and are not claimed here.
 */
class GameSessionActivity : ComponentActivity() {

    private lateinit var controller: GameSessionController
    private var immersive = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersive(true)

        val versionId = intent.getStringExtra(EXTRA_VERSION_ID).orEmpty()
        val container = (application as FlintApplication).container
        controller = GameSessionController(container.playLaunchCoordinator)

        setContent {
            FlintTheme {
                val state by controller.state.collectAsStateWithLifecycle()
                val surfaceFactory = remember {
                    {
                        GL4ESSurfaceView(this).also { view ->
                            view.onStatusChanged = { status -> controller.setRendererStatus(status) }
                        }
                    }
                }
                val actions = remember {
                    GameSessionActions(
                        onStop = { controller.stop(); finish() },
                        onCopyLog = { copyToClipboard("Minecraft log", controller.currentLog()) },
                        onReturnToLauncher = { controller.stop(); finish() },
                        onRetry = { controller.start(versionId) },
                        onToggleFullscreen = {
                            immersive = !immersive
                            applyImmersive(immersive)
                        },
                        onOpenVerification = { /* button enables the in-screen checklist */ },
                    )
                }
                LaunchedEffect(versionId) { controller.start(versionId) }
                GameSessionScreen(state = state, actions = actions, surfaceFactory = surfaceFactory)
            }
        }
    }

    private fun applyImmersive(on: Boolean) {
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (on) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun copyToClipboard(label: String, text: String) {
        val clipboard = getSystemService(ClipboardManager::class.java)
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    override fun onDestroy() {
        controller.stop()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VERSION_ID = "version_id"
    }
}
