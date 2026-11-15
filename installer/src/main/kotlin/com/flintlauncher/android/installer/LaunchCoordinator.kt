package com.flintlauncher.android.installer

import com.flintlauncher.android.core.FlintError
import com.flintlauncher.android.core.Outcome
import com.flintlauncher.android.core.models.PlayerIdentity
import com.flintlauncher.android.minecraft.manifest.VersionType
import com.flintlauncher.android.minecraft.session.GameSession
import com.flintlauncher.android.minecraft.session.SessionState
import com.flintlauncher.android.minecraft.version.Environment
import com.flintlauncher.android.minecraft.version.LaunchCommandBuilder
import com.flintlauncher.android.minecraft.version.LaunchConfigurationException
import com.flintlauncher.android.minecraft.version.LaunchContext
import com.flintlauncher.android.minecraft.version.LaunchPlan
import com.flintlauncher.android.minecraft.version.VersionEra
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Profile-derived values the launch pipeline needs, decoupled from the :profiles module
 * so :installer does not depend on it. The app shard maps a Profile into this struct.
 */
data class LaunchInput(
    val versionId: String,
    val memoryMb: Int,
    val extraJvmArgs: List<String> = emptyList(),
    val extraGameArgs: List<String> = emptyList(),
    /** Working directory for the game process (saves, config, options.txt). */
    val gameDir: File,
)

/**
 * End of the logic-shard launch pipeline: given an installed (or to-be-installed) version,
 * a player identity and the directory of Flint's own natives, produces a [LaunchPlan]
 * (and from it the primitive [com.flintlauncher.android.minecraft.version.LaunchParameters])
 * that the runtime shard forks the JVM from.
 *
 * Responsibilities:
 *  1. Ensure the version is installed (idempotent; resumes an interrupted install).
 *  2. Extract Mojang natives, filtering out LWJGL/GLFW/OpenAL desktop natives.
 *  3. Build the classpath (loader libs first, then vanilla, then client jar).
 *  4. Build JVM + game arguments (memory, java.library.path, tmpdir, user.home, log config).
 *  5. Build the environment (LD_LIBRARY_PATH pointing at Flint natives first).
 *  6. Drive a [GameSession] state machine and categorize every failure.
 *
 * It never forks a process — that is the runtime shard's job.
 */
class LaunchCoordinator(
    private val installer: MinecraftInstaller,
    private val layout: GameLayout,
    private val environment: Environment = Environment(),
    private val launcherVersion: String = "dev",
) {
    /**
     * Prepare everything needed to launch. On success returns a [LaunchPlan]; on any
     * failure returns a categorized [FlintError] and marks [session] as FAILED.
     */
    suspend fun prepareLaunch(
        input: LaunchInput,
        identity: PlayerIdentity,
        flintNativesDir: File,
        cacheDir: File,
        session: GameSession? = null,
    ): Outcome<LaunchPlan> {
        session?.transition(SessionState.PREPARING)
        try {
            val installed = ensureInstalled(input.versionId)
                ?: return fail(session, FlintError.ConfigError("Version ${input.versionId} could not be installed"))

            val vanillaId = installed.clientJar.parentFile?.name ?: input.versionId
            val era = VersionEra.classify(input.versionId, installed.version.type)

            // Extract natives, dropping desktop LWJGL/GLFW/OpenAL (Flint replaces them).
            val extractedDir = File(File(cacheDir, "minecraft-natives"), safeDirName(input.versionId))
            val extraction = extractNatives(installed.nativeJars, extractedDir)
                .onFailure { return fail(session, FlintError.ConfigError("Native extraction failed: ${it.message}")) }
                .getOrThrow()

            val classpath = installed.classpath
            val libraryDir = layout.librariesDir
            val nativesPath = listOf(extractedDir.absolutePath, flintNativesDir.absolutePath)
                .distinct().joinToString(File.pathSeparator)

            val extraJvm = buildList {
                add("-Xmx${input.memoryMb}M")
                add("-Djava.library.path=$nativesPath")
                add("-Dorg.lwjgl.librarypath=${flintNativesDir.absolutePath}")
                add("-Djava.io.tmpdir=${File(cacheDir, "minecraft-tmp").absolutePath}")
                add("-Duser.home=${input.gameDir.absolutePath}")
                installed.loggingJvmArgument?.let { add(it) }
                addAll(input.extraJvmArgs)
            }

            val context = LaunchContext(
                playerName = identity.username,
                uuid = identity.uuidNoDashes,
                accessToken = identity.accessToken,
                userType = identity.userType,
                versionName = installed.version.id,
                versionType = versionTypeString(installed.version.type),
                gameDirectory = input.gameDir.absolutePath,
                assetsRoot = installed.assetsRoot.absolutePath,
                assetsIndexName = installed.assetIndexId,
                nativesDirectory = extractedDir.absolutePath,
                libraryDirectory = libraryDir.absolutePath,
                classpath = classpath.joinToString(File.pathSeparator) { it.absolutePath },
                launcherVersion = launcherVersion,
                clientId = identity.clientId,
                xuid = identity.xuid,
            )

            val command = try {
                LaunchCommandBuilder.build(
                    version = installed.version,
                    context = context,
                    environment = environment,
                    extraJvmArguments = extraJvm,
                    extraGameArguments = input.extraGameArgs,
                )
            } catch (e: LaunchConfigurationException) {
                return fail(session, FlintError.ConfigError(e.message ?: "Invalid launch configuration"))
            }

            val env = buildMap {
                // Flint natives first so libGL.so / liblwjgl.so resolve to our ports.
                put("LD_LIBRARY_PATH", listOf(flintNativesDir.absolutePath, extractedDir.absolutePath)
                    .distinct().joinToString(File.pathSeparator))
            }

            session?.transition(SessionState.LAUNCHING)
            return Outcome.success(
                LaunchPlan(
                    versionId = input.versionId,
                    vanillaId = vanillaId,
                    era = era,
                    classpath = classpath,
                    mojangNativeJars = installed.nativeJars,
                    filteredNativeLibraries = extraction.skippedFlintReplaced,
                    extractedNativesDir = extractedDir,
                    flintNativesDir = flintNativesDir,
                    mainClass = command.mainClass,
                    jvmArgs = command.jvmArguments,
                    gameArgs = command.gameArguments,
                    env = env,
                    workingDir = input.gameDir,
                    requiredJavaMajor = installed.requiredJavaMajor,
                    loggingJvmArgument = installed.loggingJvmArgument,
                ),
            )
        } catch (e: CancellationException) {
            session?.fail(FlintError.Cancelled)
            throw e
        } catch (e: InstallPlanException) {
            return fail(session, FlintError.ConfigError(e.message ?: "Invalid installation plan"))
        } catch (e: Exception) {
            return fail(session, FlintError.Unknown(e.message ?: "Unknown launch preparation failure", e.javaClass.simpleName))
        }
    }

    private suspend fun ensureInstalled(versionId: String): InstalledVersion? {
        installer.loadInstalledVersion(versionId)?.let { return it }
        return when (val result = installer.install(versionId)) {
            is InstallResult.Success -> result.installed
            is InstallResult.Cancelled -> null
            is InstallResult.Failed -> throw InstallPlanException(result.message)
        }
    }

    private fun extractNatives(jars: List<File>, target: File): Result<NativeExtractionResult> =
        runCatching {
            if (target.exists()) target.deleteRecursively()
            target.mkdirs()
            NativesExtractor.extractFiltered(jars, target) { entry -> FlintNativePolicy.isReplacedByFlint(entry) }
        }

    private fun fail(session: GameSession?, error: FlintError): Outcome<LaunchPlan> {
        session?.fail(error)
        return Outcome.error(error)
    }

    private fun versionTypeString(type: VersionType): String = when (type) {
        VersionType.RELEASE -> "release"
        VersionType.SNAPSHOT -> "snapshot"
        VersionType.OLD_BETA -> "old_beta"
        VersionType.OLD_ALPHA -> "old_alpha"
        VersionType.UNKNOWN -> "unknown"
    }

    private fun safeDirName(value: String): String =
        value.replace(Regex("[^A-Za-z0-9._-]"), "_").take(80).ifEmpty { "version" }
}
