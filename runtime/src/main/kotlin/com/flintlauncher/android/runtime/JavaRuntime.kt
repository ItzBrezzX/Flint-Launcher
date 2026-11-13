package com.flintlauncher.android.runtime

import java.io.File

/**
 * ABI names normalized to the Android runtime artifact identifiers.
 * This describes the current app process ABI, not just every ABI the CPU supports.
 */
object RuntimeArchitecture {
    fun normalize(value: String): String? = when (value.trim().lowercase().replace('-', '_')) {
        "arm64", "arm64_v8a", "aarch64" -> "arm64"
        "arm", "armeabi_v7a", "armv7", "armv7l", "aarch32" -> "arm"
        "x86", "i386", "i486", "i586", "i686" -> "x86"
        "x86_64", "amd64" -> "x86_64"
        else -> null
    }

    fun fromSupportedAbis(abis: List<String>, is64BitProcess: Boolean): String? =
        abis.asSequence()
            .mapNotNull(::normalize)
            .firstOrNull { arch ->
                if (is64BitProcess) arch == "arm64" || arch == "x86_64"
                else arch == "arm" || arch == "x86"
            }
}

/**
 * Describes an on-device Java runtime candidate.
 *
 * [verified] means the release metadata and required library files were found.
 * Native loading is a separate process-global verification step for the selected runtime.
 */
data class JavaRuntimeInfo(
    val id: String,
    val majorVersion: Int,
    val vendor: String,
    val homeDir: File,
    val javaExecutable: File?,
    val arch: String,
    val verified: Boolean,
)

enum class RendererKind {
    GL4ES,
    MOBILE_GLUES,
    LTW,
    ZINK,
    SYSTEM_GLES,
    UNKNOWN,
}

data class RendererInfo(
    val id: String,
    val kind: RendererKind,
    val displayName: String,
    val available: Boolean,
    val notes: String? = null,
)

/**
 * Contract for locating and installing Java runtimes.
 * A concrete implementation must ship or download real ARM64 binaries
 * and must never claim success without verification.
 */
interface JavaRuntimeManager {
    suspend fun listInstalled(): List<JavaRuntimeInfo>
    suspend fun findCompatible(requiredMajor: Int, preferArch: String = "arm64"): JavaRuntimeInfo?
    suspend fun install(major: Int): Result<JavaRuntimeInfo>
    suspend fun verify(runtime: JavaRuntimeInfo): Boolean
}

/**
 * Contract for renderer discovery. Never mark a renderer available
 * unless the native library actually loads on the current GPU.
 */
interface RendererManager {
    suspend fun detectAvailable(): List<RendererInfo>
    suspend fun select(id: String): RendererInfo?
}

/**
 * Launch request assembled from profile + account + runtime + renderer.
 */
data class LaunchRequest(
    val versionId: String,
    val gameDir: File,
    val assetsDir: File,
    val classpath: List<File>,
    val nativeDir: File,
    val mainClass: String,
    val jvmArgs: List<String>,
    val gameArgs: List<String>,
    val accountUsername: String,
    val accountUuid: String,
    val accessToken: String,
    /** "msa" or "legacy" / offline placeholder. */
    val userType: String,
    val runtime: JavaRuntimeInfo,
    val renderer: RendererInfo,
)

/**
 * Result of attempting to start the game process.
 * SUCCESS means the process was started and is believed to be running —
 * not that the user reached the main menu.
 */
sealed class LaunchResult {
    data class Started(val pid: Int?, val logFile: File?) : LaunchResult()
    /** JLI_Launch returned; this is an exit result, not proof the game is still running. */
    data class Exited(val exitCode: Int) : LaunchResult()
    data class Failed(val reason: String, val logSnippet: String? = null) : LaunchResult()
    data object BlockedMissingRuntime : LaunchResult()
    data object BlockedMissingRenderer : LaunchResult()
    data object BlockedNotReady : LaunchResult()
}

/**
 * Game process controller.
 *
 * On modern Android, arbitrary downloaded ELF execution is heavily restricted.
 * A real implementation typically uses:
 * - in-process JVM via JNI (Pojav-style), or
 * - a carefully prepared isolated process with native loader.
 *
 * This interface exists so the rest of the launcher can be written against
 * a stable API. The default [UnimplementedGameLauncher] always returns a clear failure.
 */
interface GameLauncher {
    suspend fun launch(request: LaunchRequest): LaunchResult
    suspend fun isRunning(): Boolean
    suspend fun stop()
    fun stdout(): String
    fun stderr(): String
}

/**
 * Honest placeholder. Prevents accidental "it launched" claims.
 * Replace with a real JNI / native implementation when the runtime spike is done.
 */
/**
 * Prefer [PojavStyleGameLauncher] once a mobile JRE and the :native library are available.
 * This class remains as a safe default that never pretends to launch.
 */
class UnimplementedGameLauncher : GameLauncher {
    override suspend fun launch(request: LaunchRequest): LaunchResult =
        LaunchResult.Failed(
            reason = "Java runtime / native game bridge is not implemented yet. " +
                "See runtime/README.md and docs/ARCHITECTURE.md (Spike 0).",
        )

    override suspend fun isRunning(): Boolean = false
    override suspend fun stop() = Unit
    override fun stdout(): String = ""
    override fun stderr(): String = ""
}
