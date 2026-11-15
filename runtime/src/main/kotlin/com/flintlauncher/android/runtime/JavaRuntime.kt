package com.flintlauncher.android.runtime

import com.flintlauncher.android.minecraft.version.LwjglRuntimeSpec
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
 * Describes an on-device Java runtime that can host Minecraft.
 *
 * Real implementations will download / extract / verify ARM64 JREs
 * (e.g. Pojav-style or vendor builds). This module only defines the contract.
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
    /** App-private directory containing Flint's Android ABI native libraries. */
    val androidNativeDir: File,
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
    /** LWJGL generation and exact version resolved from the installed Minecraft profile. */
    val lwjglRuntimeSpec: LwjglRuntimeSpec? = null,
)

/**
 * Validates that the version metadata was expanded into an actual JVM command.
 * In particular, the classpath carried for diagnostics must be the same classpath
 * passed after -cp/-classpath; the JNI bridge must not silently invent another one.
 */
object LaunchRequestValidator {
    fun validate(request: LaunchRequest): String? {
        if (request.mainClass.isBlank()) return "Minecraft version metadata has no main class"
        if (request.classpath.isEmpty()) return "Minecraft classpath is empty"
        val missing = request.classpath.filterNot { it.isFile }
        if (missing.isNotEmpty()) {
            return "Minecraft classpath contains missing files: ${missing.take(4).joinToString { it.name }}"
        }
        if (!request.nativeDir.isDirectory) return "Minecraft native directory is missing"
        if (!request.gameDir.isDirectory) return "Minecraft game directory is missing"
        val expectedNativePath = listOf(
            request.nativeDir.absolutePath,
            request.androidNativeDir.absolutePath,
        ).joinToString(File.pathSeparator)
        val nativePathArgument = request.jvmArgs.lastOrNull { it.startsWith("-Djava.library.path=") }
        if (nativePathArgument != "-Djava.library.path=$expectedNativePath") {
            return "Minecraft JVM native library path does not include both game and Android native directories"
        }
        val lwjglLibraryPath = request.jvmArgs.lastOrNull { it.startsWith("-Dorg.lwjgl.librarypath=") }
        if (lwjglLibraryPath != "-Dorg.lwjgl.librarypath=${request.androidNativeDir.absolutePath}") {
            return "LWJGL library path does not point to Flint's Android native library directory"
        }

        val optionIndex = request.jvmArgs.indexOfFirst { it == "-cp" || it == "-classpath" }
        if (optionIndex < 0 || optionIndex + 1 >= request.jvmArgs.size) {
            return "Minecraft JVM arguments do not contain a complete -cp classpath option"
        }
        val expected = request.classpath.joinToString(File.pathSeparator) { it.absolutePath }
        if (request.jvmArgs[optionIndex + 1] != expected) {
            return "Minecraft JVM classpath argument does not match the installed libraries"
        }
        return null
    }
}

/** Converts the validated request into the exact argv layout expected by JLI_Launch. */
internal fun buildJliArguments(request: LaunchRequest): List<String> = buildList {
    add("java")
    addAll(request.jvmArgs)
    add(request.mainClass)
    addAll(request.gameArgs)
}

/**
 * Result of attempting to start the game process.
 * Started means a live game process/session has been attached and is believed
 * to be running — not that the user reached the main menu. Implementations must
 * not return Started merely because a synchronous JVM entry point returned 0.
 */
sealed class LaunchResult {
    data class Started(val pid: Int?, val logFile: File?) : LaunchResult()
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
