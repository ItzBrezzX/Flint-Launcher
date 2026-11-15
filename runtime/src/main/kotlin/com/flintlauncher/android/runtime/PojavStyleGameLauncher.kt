package com.flintlauncher.android.runtime

import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Game launcher following the PojavLauncher in-process JVM model:
 * load mobile OpenJDK via dlopen → set env → call JLI_Launch through JNI.
 *
 * Until a real ARM64 JRE is installed and the native library is built,
 * every launch attempt returns a precise failure instead of pretending success.
 */
class PojavStyleGameLauncher(
    private val runtimeManager: JavaRuntimeManager,
) : GameLauncher {

    // JLI_Launch is synchronous. Keep this true for the entire native call so a
    // second launch cannot replace the JRE or race nativeShutdown().
    private val launchActive = AtomicBoolean(false)
    private val logBuffer = StringBuilder()

    override suspend fun launch(request: LaunchRequest): LaunchResult = withContext(Dispatchers.IO) {
        val runtime = request.runtime
        if (!runtime.verified) {
            return@withContext LaunchResult.BlockedMissingRuntime
        }

        LaunchRequestValidator.validate(request)?.let { reason ->
            return@withContext LaunchResult.Failed(reason)
        }
        if (!request.renderer.available) {
            return@withContext LaunchResult.BlockedMissingRenderer
        }
        if (!NativeBridge.isNativeLoaded) {
            return@withContext LaunchResult.Failed(
                "Native library flint_native not loaded: ${NativeBridge.nativeLoadError}. " +
                    "Build the :native module with NDK/CMake first.",
            )
        }
        val lwjglSpec = request.lwjglRuntimeSpec
            ?: return@withContext LaunchResult.Failed(
                "LWJGL runtime metadata was not resolved for ${request.versionId}.",
            )
        if (lwjglSpec.major != 3 || lwjglSpec.version != "3.3.3") {
            return@withContext LaunchResult.Failed(
                "This Android native build currently supports LWJGL 3.3.3 only; " +
                    "the selected profile requires LWJGL ${lwjglSpec.version} (major ${lwjglSpec.major}).",
            )
        }
        val modules = lwjglSpec.artifacts.map { it.artifact }.toSet()
        val requiredModules = setOf("lwjgl", "lwjgl-glfw", "lwjgl-opengl")
        if (!modules.containsAll(requiredModules)) {
            val missing = (requiredModules - modules).sorted().joinToString()
            return@withContext LaunchResult.Failed(
                "The selected LWJGL profile is missing required modules: $missing.",
            )
        }
        val unsupportedModules = (modules - requiredModules).sorted()
        if (unsupportedModules.isNotEmpty()) {
            return@withContext LaunchResult.Failed(
                "Android native modules are not built yet: ${unsupportedModules.joinToString()}.",
            )
        }
        val lwjglLoad = NativeBridge.safeLwjglAbiSmoke()
        if (lwjglLoad != "flint-lwjgl-native-ok") {
            return@withContext LaunchResult.Failed(
                "Android LWJGL native libraries could not be loaded: $lwjglLoad",
            )
        }

        if (!launchActive.compareAndSet(false, true)) {
            return@withContext LaunchResult.Failed("A Minecraft JVM launch is already active.")
        }

        try {
            val loaded = NativeBridge.nativeLoadJre(runtime.homeDir.absolutePath)
            if (!loaded) {
                return@withContext LaunchResult.Failed(
                    "Failed to dlopen libjli.so under ${runtime.homeDir}. " +
                        "Is this a mobile OpenJDK layout with aarch64 libs?",
                )
            }

            // LaunchCommandBuilder already expanded -cp and its value from version metadata.
            // The request-to-JLI adapter preserves that classpath exactly once.
            val args = buildJliArguments(request)

            logBuffer.appendLine("Launch argv (${args.size} entries):")
            args.take(12).forEach { logBuffer.appendLine("  $it") }
            if (args.size > 12) logBuffer.appendLine("  ...")

            val code = NativeBridge.nativeLaunchJvm(args.toTypedArray())

            // JLI_Launch returns only after the Java application exits. A zero
            // return code is not evidence of a live game session.
            mapJvmExit(code, logBuffer.toString())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (t: Throwable) {
            LaunchResult.Failed("JNI launch threw: ${t.message}", logBuffer.toString())
        } finally {
            // The JVM has returned (or launch failed), so unloading is now safe.
            runCatching { NativeBridge.nativeShutdown() }
            launchActive.set(false)
        }
    }

    override suspend fun isRunning(): Boolean = launchActive.get()

    override suspend fun stop() {
        // The current JNI contract has no JVM termination primitive. In particular,
        // unloading libjli here can race a new launch or invalidate its JLI entry point.
        // Keep the active state truthful; process termination needs a dedicated native API.
    }

    override fun stdout(): String = logBuffer.toString()
    override fun stderr(): String = logBuffer.toString()
}


/**
 * Maps the return from synchronous JLI_Launch. This API only returns after the
 * Java main application has terminated, so no return code can prove a live session.
 */
internal fun mapJvmExit(exitCode: Int, log: String): LaunchResult =
    LaunchResult.Failed(
        reason = if (exitCode == 0) {
            "JVM exited normally. A live game session cannot be reported by the synchronous JLI_Launch bridge."
        } else {
            "JVM exited with code $exitCode"
        },
        logSnippet = log,
    )
