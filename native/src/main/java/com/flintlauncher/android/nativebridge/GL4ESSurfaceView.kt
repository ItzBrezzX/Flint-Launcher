package com.flintlauncher.android.nativebridge

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.util.Log
import java.io.File
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * EGL/GLES2 surface that boots GL4ES on the render thread.
 * The GL4ES context is the same context that subsequent game rendering must use.
 */
class GL4ESSurfaceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : GLSurfaceView(context, attrs) {

    @Volatile
    var rendererStatus: String = "Waiting for EGL surface"
        private set

    init {
        setEGLContextClientVersion(2)
        preserveEGLContextOnPause = true
        setRenderer(object : Renderer {
            override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
                val library = File(context.applicationInfo.nativeLibraryDir, "libGL.so")
                val initialized = NativeBridge.nativeLoadGL4ES(library.absolutePath)
                rendererStatus = if (initialized) {
                    val version = NativeBridge.nativeGL4ESVersion()
                    if (NativeBridge.nativeGL4ESClear()) "GL4ES ready · $version"
                    else "GL4ES loaded; GLES operation failed · $version"
                } else {
                    "GL4ES initialization failed: ${library.absolutePath}"
                }
                Log.i(TAG, rendererStatus)
            }

            override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) = Unit

            override fun onDrawFrame(gl: GL10?) {
                if (rendererStatus.startsWith("GL4ES ready")) {
                    NativeBridge.nativeGL4ESClear()
                }
            }
        })
    }

    companion object {
        private const val TAG = "FlintGL4ES"
    }
}
