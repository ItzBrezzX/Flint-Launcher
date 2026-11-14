package com.flintlauncher.android

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.WindowManager
import com.flintlauncher.android.nativebridge.NativeBridge

/**
 * Internal diagnostic surface host. Runs in :game, separate from the launcher UI process.
 * It validates the Android Surface -> ANativeWindow -> EGL window-surface lifecycle only;
 * it is not a Minecraft Activity and does not start a JVM or renderer.
 */
class GameSurfaceActivity : Activity(), SurfaceHolder.Callback {
    private var hasWindowSurface = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val view = SurfaceView(this)
        view.holder.addCallback(this)
        setContentView(view)
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        val result = NativeBridge.safeCreateEglWindow(holder.surface)
        hasWindowSurface = result.startsWith("egl-window-ok:") && result.contains("|gl4es-window-ok:")
        Log.i(TAG, "create=$result")

        if (hasWindowSurface) {
            val frame = NativeBridge.safeRenderEglWindowFrame()
            Log.i(TAG, "frame=$frame")
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        if (!hasWindowSurface) return
        val frame = NativeBridge.safeRenderEglWindowFrame()
        Log.i(TAG, "resize-frame=$frame size=${width}x${height}")
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        val result = NativeBridge.safeDestroyEglWindow()
        hasWindowSurface = false
        Log.i(TAG, "destroy=$result")
    }

    override fun onDestroy() {
        if (hasWindowSurface) {
            Log.i(TAG, "activity-destroy=${NativeBridge.safeDestroyEglWindow()}")
            hasWindowSurface = false
        }
        super.onDestroy()
    }

    private companion object {
        const val TAG = "FlintGameSurface"
    }
}
