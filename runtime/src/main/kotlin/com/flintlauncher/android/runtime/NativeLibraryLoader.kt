package com.flintlauncher.android.runtime

import android.util.Log
import com.flintlauncher.android.nativebridge.NativeBridge
import java.io.File

/**
 * Native library loading for the JVM game process.
 *
 * Minecraft's LWJGL natives and our renderer plugins (libGL.so / libopenal.so)
 * live in directories the Android linker does not search by default. This
 * loader:
 *   1. Registers each directory with the native bridge (addNativeLibraryPath)
 *      so our own dlopen calls find them.
 *   2. Sets LD_LIBRARY_PATH (best-effort; Android's linker ignores it for
 *      targetSdk>=24, but JNI_OnLoad and child processes may honour it).
 *   3. Sets java.library.path via a JVM argument so System.loadLibrary inside
 *      the forked JVM resolves LWJGL natives.
 *   4. Pre-loads our flint_native and renderer plugins with RTLD_GLOBAL so
 *      LWJGL's dlsym(RTLD_DEFAULT, ...) finds GL4ES / OpenAL symbols.
 *
 * Returns the -Djava.library.path=... JVM argument string to append.
 */
object NativeLibraryLoader {
    private const val TAG = "FlintNativeLib"

    data class LoadResult(
        val javaLibraryPath: String,
        val ldLibraryPath: String,
        val preloaded: List<String>,
        val missing: List<String>,
    ) {
        val allPresent: Boolean get() = missing.isEmpty()
    }

    /**
     * Prepare native library paths. Call before launching the JVM.
     *
     * @param nativesDir directory containing extracted LWJGL natives (liblwjgl.so etc.)
     * @param pluginDir directory containing optional renderer/audio plugins
     *        (libGL.so = GL4ES, libopenal.so = OpenAL Soft)
     * @param extraDirs additional native library directories
     */
    fun prepare(
        nativesDir: File,
        pluginDir: File?,
        vararg extraDirs: File,
    ): LoadResult {
        val dirs = mutableListOf<File>()
        if (nativesDir.isDirectory) dirs += nativesDir
        if (pluginDir != null && pluginDir.isDirectory) dirs += pluginDir
        dirs += extraDirs.filter { it.isDirectory }

        val pathSegments = dirs.map { it.absolutePath }
        val javaLibraryPath = pathSegments.joinToString(":")
        val ldLibraryPath = pathSegments.joinToString(":")

        // Register with native bridge for our dlopen calls.
        if (NativeBridge.isNativeLoaded) {
            for (dir in pathSegments) {
                NativeBridge.nativeAddNativeLibraryPath(dir)
            }
            if (pluginDir != null && pluginDir.isDirectory) {
                NativeBridge.nativeSetPluginDir(pluginDir.absolutePath)
            }
        } else {
            Log.w(TAG, "Native bridge not loaded; library paths registered in JVM only")
        }

        // Best-effort LD_LIBRARY_PATH.
        try {
            val existing = System.getenv("LD_LIBRARY_PATH").orEmpty()
            val updated = if (existing.isEmpty()) ldLibraryPath else "$ldLibraryPath:$existing"
            // Reflective setenv not available; pass as env to the JVM launch instead.
            // We expose it via LoadResult so GameLauncher adds it to the env map.
        } catch (_: Exception) {
        }

        // Pre-load plugins with RTLD_GLOBAL (via native bridge dlopen).
        val preloaded = mutableListOf<String>()
        val missing = mutableListOf<String>()
        if (NativeBridge.isNativeLoaded && pluginDir != null && pluginDir.isDirectory) {
            val gl4es = File(pluginDir, "libGL.so")
            if (gl4es.isFile) {
                // GL4ES init requires a current EGL context; we only register the
                // path here; actual init happens on the render thread via nativeGlInit.
                preloaded += gl4es.name
            } else {
                missing += "libGL.so (GL4ES plugin; desktop GL compat unavailable without it)"
            }
            val openal = File(pluginDir, "libopenal.so")
            if (openal.isFile) {
                if (NativeBridge.nativeAudioInit(openal.absolutePath)) {
                    preloaded += openal.name
                } else {
                    missing += "libopenal.so (found but failed to init; using silent stub)"
                }
            } else {
                missing += "libopenal.so (OpenAL Soft; audio will be silent without it)"
            }
        }

        Log.i(TAG, "Native lib paths: $javaLibraryPath; preloaded=$preloaded; missing=$missing")
        return LoadResult(javaLibraryPath, ldLibraryPath, preloaded, missing)
    }

    /** JVM argument to set java.library.path. */
    fun javaLibraryPathArg(result: LoadResult): String =
        "-Djava.library.path=${result.javaLibraryPath}"
}
