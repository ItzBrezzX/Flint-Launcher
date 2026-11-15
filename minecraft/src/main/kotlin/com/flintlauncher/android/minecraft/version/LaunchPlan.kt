package com.flintlauncher.android.minecraft.version

import java.io.File

/**
 * The primitive launch parameters the runtime shard consumes to fork the JVM.
 *
 * Cross-shard contract (Spike 0): no shared data type is needed — this is exactly the
 * tuple `GameLauncher.launch` is built from. Every field is a JDK primitive/collection so
 * the runtime module does not depend on :minecraft. The app/runtime shards copy these
 * values into their own `LaunchRequest`.
 *
 * - [classpath] is in launch order: loader libraries first, then vanilla libraries, then
 *   the client jar (matching InstallPlanner.classpath).
 * - [nativesDir] contains ONLY the extracted non-LWJGL natives. LWJGL/GLFW/OpenAL desktop
 *   natives are filtered out and replaced by Flint's own natives, which live in
 *   [flintNativesDir] and are added to LD_LIBRARY_PATH in [env].
 * - [env] includes LD_LIBRARY_PATH and any java.library.path hints the JVM needs.
 * - [workingDir] is the profile's game directory (saves, config, mods, options.txt).
 */
data class LaunchParameters(
    val classpath: List<File>,
    val jvmArgs: List<String>,
    val gameArgs: List<String>,
    val mainClass: String,
    val nativesDir: File,
    val env: Map<String, String>,
    val workingDir: File,
) {
    /** Everything after the java executable, for logging / diagnostics. */
    fun argumentList(): List<String> = jvmArgs + mainClass + gameArgs
}

/**
 * Internal, richer plan produced before collapsing to [LaunchParameters]. Kept so the
 * installer can report *why* a launch looks the way it does (which natives were skipped,
 * which era we detected) and so tests can assert the intermediate decisions.
 */
data class LaunchPlan(
    val versionId: String,
    val vanillaId: String,
    val era: VersionEra,
    val classpath: List<File>,
    /** Mojang native jars we were given; their contents are extracted into [extractedNativesDir]. */
    val mojangNativeJars: List<File>,
    /** Native library entry names we skipped because Flint ships its own (LWJGL/GLFW/OpenAL). */
    val filteredNativeLibraries: List<String>,
    /** Directory of extracted, non-LWJGL Mojang natives. May be empty when all were filtered. */
    val extractedNativesDir: File,
    /** Flint's own natives directory (libGL.so, lwjgl Android ports), added to LD_LIBRARY_PATH. */
    val flintNativesDir: File,
    val mainClass: String,
    val jvmArgs: List<String>,
    val gameArgs: List<String>,
    val env: Map<String, String>,
    val workingDir: File,
    val requiredJavaMajor: Int,
    /** `-Dlog4j.configurationFile=...` already included in [jvmArgs] when non-null. */
    val loggingJvmArgument: String?,
) {
    fun toParameters(): LaunchParameters = LaunchParameters(
        classpath = classpath,
        jvmArgs = jvmArgs,
        gameArgs = gameArgs,
        mainClass = mainClass,
        nativesDir = extractedNativesDir,
        env = env,
        workingDir = workingDir,
    )
}
