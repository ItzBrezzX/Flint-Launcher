package com.flintlauncher.android.runtime

import java.io.File

/**
 * Swaps Mojang's desktop LWJGL for the Android build.
 *
 * Mojang's version JSONs list LWJGL jars plus Linux natives built against glibc, which cannot be
 * loaded by Android's bionic linker. Flint therefore removes every LWJGL entry from the classpath
 * and supplies LWJGL jars built for Android (see .github/workflows/build-lwjgl-android.yml), with
 * the GLFW and OpenGL libraries pointed at Flint's own libglfw.so and GL4ES.
 */
object LwjglAndroid {
    sealed interface Substitution {
        data class Ready(val classpath: List<File>) : Substitution
        data class Unsupported(val reason: String) : Substitution
    }

    private val lwjgl3Entry = Regex("/org/lwjgl/[^/]+/3\\.[^/]+/")
    private val lwjgl2Entry = Regex("/org/lwjgl/lwjgl/(lwjgl|lwjgl_util|lwjgl-platform)/2\\.")
    private val jinputEntry = Regex("/net/java/(jinput|jutils)/")

    fun isLwjgl3(file: File): Boolean = lwjgl3Entry.containsMatchIn(slashed(file))

    /** LWJGL 2 (Minecraft 1.12.2 and older) and its input companion libraries. */
    fun isLwjgl2(file: File): Boolean = slashed(file).let { lwjgl2Entry.containsMatchIn(it) || jinputEntry.containsMatchIn(it) }

    private fun slashed(file: File) = file.invariantSeparatorsPath.let { if (it.startsWith("/")) it else "/$it" }

    /**
     * @param classpath Mojang's resolved classpath (desktop LWJGL included).
     * @param androidJars jars shipped with Flint for Android; an `lwjglx*.jar` among them is the
     *   LWJGL 2 compatibility layer used for old versions.
     */
    fun substitute(classpath: List<File>, androidJars: List<File>): Substitution {
        val usesLwjgl2 = classpath.any(::isLwjgl2)
        val usesLwjgl3 = classpath.any(::isLwjgl3)
        if (!usesLwjgl2 && !usesLwjgl3) {
            // Nothing to replace (for example a non-LWJGL test main class): leave the classpath alone.
            return Substitution.Ready(classpath)
        }
        if (androidJars.isEmpty()) {
            return Substitution.Unsupported(
                "This APK has no LWJGL-for-Android components. Build it with the 'Build LWJGL for Android' " +
                    "workflow (see docs/GAME_PIPELINE.md); without them Minecraft cannot start.",
            )
        }
        val compat = androidJars.filter { it.name.startsWith("lwjglx", ignoreCase = true) }
        val core = androidJars.filterNot { it in compat }.sortedBy { it.name }
        if (usesLwjgl2 && compat.isEmpty()) {
            return Substitution.Unsupported(
                "This version uses LWJGL 2 (Minecraft 1.12.2 and older) and the LWJGL 2 compatibility layer " +
                    "is not part of this build yet. Try a 1.13 or newer version.",
            )
        }
        val kept = classpath.filterNot { isLwjgl2(it) || isLwjgl3(it) }
        // Android LWJGL first so its classes win over anything left on the classpath.
        return Substitution.Ready(core + (if (usesLwjgl2) compat else emptyList()) + kept)
    }

    /** JVM system properties that make LWJGL use Flint's libraries instead of bundled desktop ones. */
    fun jvmArguments(nativeLibraryDir: File): List<String> {
        val dir = nativeLibraryDir.absolutePath
        return listOf(
            "-Dorg.lwjgl.librarypath=$dir",
            "-Dorg.lwjgl.glfw.libname=$dir/libglfw.so",
            "-Dorg.lwjgl.opengl.libname=$dir/libGL.so",
            "-Dorg.lwjgl.openal.libname=$dir/libopenal.so",
            // The jemalloc binding is not built for Android; use the C allocator.
            "-Dorg.lwjgl.system.allocator=system",
        )
    }
}
