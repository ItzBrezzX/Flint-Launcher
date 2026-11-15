package com.flintlauncher.android

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import com.flintlauncher.android.nativebridge.GL4ESSurfaceView

/**
 * Device-side smoke screen for the real Android EGL window + GL4ES path.
 * It intentionally does not claim to launch Minecraft or provide an LWJGL window.
 */
class RendererTestActivity : Activity() {

    private lateinit var rendererView: GL4ESSurfaceView
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        rendererView = GL4ESSurfaceView(this)
        statusView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 13f
            setPadding(16, 10, 16, 10)
            setBackgroundColor(0xB9000000.toInt())
            text = rendererView.rendererStatus
        }
        rendererView.onStatusChanged = { status ->
            runOnUiThread { statusView.text = status }
        }

        val root = FrameLayout(this).apply {
            addView(
                rendererView,
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
        }
        setContentView(root)
    }

    override fun onDestroy() {
        rendererView.onStatusChanged = null
        super.onDestroy()
    }
}
