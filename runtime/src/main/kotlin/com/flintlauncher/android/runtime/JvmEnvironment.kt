package com.flintlauncher.android.runtime

import java.io.File

/**
 * Process environment the in-process JVM needs before `JLI_Launch` runs.
 *
 * The JVM shares this process, so it sees the C environment of the Android app. Without
 * `JAVA_HOME` and a `LD_LIBRARY_PATH` that covers the JRE and the extracted game natives,
 * `libjvm.so` or its sibling libraries (`libjava.so`, `libnet.so`, ...) can fail to resolve.
 * Pure function over [File]s so it can be unit-tested on a host JVM.
 */
object JvmEnvironment {
    /** Library sub-directories used by the different mobile OpenJDK layouts. */
    private val jreLibraryDirectories = listOf(
        "lib", "lib/jli", "lib/server",
        "lib/aarch64", "lib/aarch64/jli", "lib/aarch64/server",
        "lib/arm64", "lib/arm64/jli", "lib/arm64/server",
        "lib/aarch32", "lib/aarch32/jli", "lib/aarch32/server",
        "lib/arm", "lib/arm/jli", "lib/arm/server",
        "lib/amd64", "lib/amd64/jli", "lib/amd64/server",
        "lib/x86_64", "lib/x86_64/jli", "lib/x86_64/server",
        "lib/i386", "lib/i386/jli", "lib/i386/server",
    )

    fun build(
        runtimeHome: File,
        gameNativeDirectory: File,
        appNativeLibraryDirectory: File,
        gameDirectory: File,
        temporaryDirectory: File,
        inheritedLdLibraryPath: String? = null,
    ): Map<String, String> {
        val libraryPath = LinkedHashSet<String>()
        jreLibraryDirectories
            .map { File(runtimeHome, it) }
            .filter { it.isDirectory }
            .forEach { libraryPath += it.absolutePath }
        libraryPath += gameNativeDirectory.absolutePath
        libraryPath += appNativeLibraryDirectory.absolutePath
        inheritedLdLibraryPath
            ?.split(File.pathSeparatorChar)
            ?.filter { it.isNotBlank() }
            ?.forEach { libraryPath += it }

        return linkedMapOf(
            "JAVA_HOME" to runtimeHome.absolutePath,
            "LD_LIBRARY_PATH" to libraryPath.joinToString(File.pathSeparator),
            "HOME" to gameDirectory.absolutePath,
            "TMPDIR" to temporaryDirectory.absolutePath,
            // GL4ES: translate desktop GL to OpenGL ES 2, build mipmaps itself, normalise normals.
            "LIBGL_ES" to "2",
            "LIBGL_MIPMAP" to "3",
            "LIBGL_NORMALIZE" to "1",
        )
    }
}
