package com.flintlauncher.android.audio

import java.io.File

/**
 * OpenAL Soft dynamic plugin contract.
 *
 * OpenAL Soft is distributed under the LGPL. To comply, Flint loads it as a DYNAMIC
 * plugin at runtime (dlopen / System.load from a plugin directory) — it is never
 * statically linked into the APK, and the user can replace/update the .so. This file
 * documents the loading contract; the actual JNI bridge that forwards al* calls is
 * owned by the native shard and is NOT implemented here.
 *
 * Contract:
 *  - Plugin path: <filesDir>/audio-plugins/libopenal.so (user-replaceable).
 *  - Loaded lazily on first audio init; if missing, fall back to AudioTrackBackend.
 *  - Version probe: plugin must export `alGetString` and `alcOpenDevice` symbols.
 *  - The plugin is never downloaded by Flint itself; the user provides it (or a
 *    future optional module ships it with a visible LGPL attribution + source offer).
 *  - Spike-0 status: CONTRACT ONLY. No dlopen/JNI bridge is implemented in this shard.
 */
object OpenAlDynamicPlugin {
    const val PLUGIN_SUBDIR = "audio-plugins"
    const val LIBRARY_NAME = "libopenal.so"

    fun pluginPath(filesDir: File): File = File(File(filesDir, PLUGIN_SUBDIR), LIBRARY_NAME)

    fun isInstalled(filesDir: File): Boolean = pluginPath(filesDir).isFile

    /**
     * Load the plugin. Returns true on success. Spike-0: always returns false because
     * the JNI bridge is not implemented; this documents the intended call site.
     */
    fun load(filesDir: File): Boolean {
        val plugin = pluginPath(filesDir)
        if (!plugin.isFile) return false
        // Native shard implements: System.load(plugin.absolutePath) + symbol probe.
        // Intentionally not called here: no bridge exists yet.
        return false
    }
}
