package com.flintlauncher.android.nativebridge

import android.util.Log
import java.io.File

/**
 * Renderer backend kinds supported by Flint's GL symbol loader.
 * Mirrors flint::RendererBackend in gl_symbol_loader.h.
 */
enum class RendererBackend(val nativeId: Int, val displayName: String) {
    NONE(0, "None"),
    SYSTEM_GLES(1, "System OpenGL ES"),
    GL4ES_PLUGIN(2, "GL4ES (desktop GL → GLES translation)"),
    ;

    companion object {
        fun fromNative(id: Int): RendererBackend =
            entries.firstOrNull { it.nativeId == id } ?: NONE
    }
}

/**
 * Result of probing a renderer backend on the current GPU.
 */
data class RendererProbeResult(
    val backend: RendererBackend,
    val available: Boolean,
    val renderer: String = "",
    val version: String = "",
    val glslVersion: String = "",
    val vendor: String = "",
    val maxTextureSize: Int = 0,
    val error: String? = null,
) {
    val displaySummary: String
        get() = if (available) "$displayName · $renderer · $version"
        else "$displayName (unavailable: ${error ?: "unknown"})"
}

/**
 * A renderable surface provider. The app shard owns the SurfaceView; this
 * interface lets the runtime attach the GLFW window to it without a direct
 * Android dependency.
 */
fun interface RenderSurfaceProvider {
    /** Called on the render thread when a valid Android Surface is available. */
    fun surface(): android.view.Surface?
}

/**
 * Renderer selection + fallback. Selection order (Spike 0):
 *   1. GL4ES plugin (libGL.so) if present in pluginDir/nativeLibraryDir and it
 *      initializes with a current EGL context. Best desktop-GL compat.
 *   2. System GLES direct dispatch (only works for GLES-aware games; Minecraft
 *      desktop needs GL4ES, so this is the fallback with limited compat).
 *   3. None → launch blocked.
 *
 * Context recovery: if the EGL context is lost (e.g. app backgrounded), call
 * [recoverContext] after the window layer re-attaches the Surface.
 */
object RendererSelector {
    private const val TAG = "FlintRenderer"

    /**
     * Probe all backends and return them in preference order.
     * Must be called on the render thread with a current EGL context (the
     * GLFW window must be attached first).
     */
    fun probeAll(pluginDir: File?): List<RendererProbeResult> {
        if (!NativeBridge.isNativeLoaded) {
            return listOf(RendererProbeResult(RendererBackend.NONE, false,
                error = "native library not loaded"))
        }
        val results = mutableListOf<RendererProbeResult>()
        // Try GL4ES first.
        val gl4esPath = findPlugin(pluginDir, "libGL.so")
        val gl4esBackend = runCatching {
            NativeBridge.nativeGlInit(RendererBackend.GL4ES_PLUGIN.nativeId, gl4esPath?.absolutePath)
        }.getOrDefault(0)
        val gl4esReport = parseCompat(NativeBridge.safeGlQueryCompat())
        results += gl4esReport.copy(
            backend = RendererBackend.GL4ES_PLUGIN,
            available = gl4esBackend == RendererBackend.GL4ES_PLUGIN.nativeId && gl4esReport.error.isNullOrEmpty(),
            error = if (gl4esBackend != RendererBackend.GL4ES_PLUGIN.nativeId)
                NativeBridge.nativeGlLastError().ifEmpty { "GL4ES init failed" } else gl4esReport.error,
        )
        // Then System GLES.
        val glesBackend = runCatching {
            NativeBridge.nativeGlInit(RendererBackend.SYSTEM_GLES.nativeId, null)
        }.getOrDefault(0)
        val glesReport = parseCompat(NativeBridge.safeGlQueryCompat())
        results += glesReport.copy(
            backend = RendererBackend.SYSTEM_GLES,
            available = glesBackend == RendererBackend.SYSTEM_GLES.nativeId,
            error = if (glesBackend != RendererBackend.SYSTEM_GLES.nativeId)
                NativeBridge.nativeGlLastError().ifEmpty { "GLES init failed" } else null,
        )
        return results.sortedByDescending { it.available }
    }

    /** Select the best available backend. Returns NONE if nothing works. */
    fun selectBest(pluginDir: File?): RendererProbeResult =
        probeAll(pluginDir).firstOrNull { it.available }
            ?: RendererProbeResult(RendererBackend.NONE, false, error = "no renderer available")

    /**
     * Attempt context recovery after an EGL context loss. Call on the render
     * thread after the GLFW window has re-attached a valid Surface.
     */
    fun recoverContext(): Boolean =
        NativeBridge.isNativeLoaded && runCatching { NativeBridge.nativeGlRecoverContext() }.getOrDefault(false)

    fun currentBackend(): RendererBackend =
        if (NativeBridge.isNativeLoaded) RendererBackend.fromNative(NativeBridge.nativeGlCurrentBackend())
        else RendererBackend.NONE

    internal fun findPlugin(pluginDir: File?, name: String): File? {
        val candidates = mutableListOf<File?>()
        if (pluginDir != null) candidates += File(pluginDir, name)
        // Also check the app's nativeLibraryDir (caller passes it as pluginDir usually).
        return candidates.firstOrNull { it != null && it.isFile }
    }

    internal fun parseCompat(report: String): RendererProbeResult {
        val map = report.split('|')
            .mapNotNull { segment ->
                val idx = segment.indexOf('=')
                if (idx > 0) segment.substring(0, idx) to segment.substring(idx + 1) else null
            }
            .toMap()
        return RendererProbeResult(
            backend = RendererBackend.fromNative(map["backend"]?.toIntOrNull() ?: 0),
            available = map["error"].isNullOrEmpty(),
            renderer = map["renderer"] ?: "",
            version = map["version"] ?: "",
            glslVersion = map["glsl"] ?: "",
            vendor = map["vendor"] ?: "",
            maxTextureSize = map["maxTex"]?.toIntOrNull() ?: 0,
            error = map["error"]?.takeIf { it.isNotEmpty() },
        )
    }
}
