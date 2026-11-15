package com.flintlauncher.android.installer

/**
 * Which Mojang-shipped native libraries Flint replaces with its own Android ports.
 *
 * Desktop LWJGL/GLFW/OpenAL natives are built for desktop Linux and cannot load on
 * Android (they link against libX11/libGL/etc.). Flint ships its own `liblwjgl.so`,
 * `libglfw.so`, `libopenal.so` and `libGL.so` in the APK's native library dir. We skip
 * the desktop entries during extraction and add Flint's natives directory to
 * LD_LIBRARY_PATH (and java.library.path) instead. The skipped names are recorded in the
 * LaunchPlan so the UI can show "replaced X desktop natives with Flint builds".
 */
object FlintNativePolicy {
    /** Entry-name substrings that identify a native library Flint ships its own build of. */
    private val replacedNameHints = listOf(
        "lwjgl",     // liblwjgl.so, liblwjgl_opengl.so, liblwjgl_stb.so, liblwjgl_tinyfd.so, ...
        "glfw",      // libglfw.so
        "openal",    // libopenal.so, libopenal64.so
        "jemalloc",  // libjemalloc.so (LWJGL 3 bundles it)
    )

    /** True when a zip entry name (e.g. "liblwjgl.so" or "linux/x86_64/libglfw.so") is replaced by Flint. */
    fun isReplacedByFlint(entryName: String): Boolean {
        val lower = entryName.lowercase()
        if (!lower.endsWith(".so")) return false
        return replacedNameHints.any { lower.contains(it) }
    }

    /** The directory name component inside a native jar we keep (Android is linux-style). */
    const val ANDROID_OS_FAMILY = "linux"
}

data class NativeExtractionResult(
    val extracted: List<File>,
    /** Entry names skipped because [FlintNativePolicy] replaces them. */
    val skippedFlintReplaced: List<String>,
)
