package com.flintlauncher.android.runtime

import android.util.Log
import com.flintlauncher.android.nativebridge.NativeBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Primitive-parameter GameLauncher contract. Deliberately uses only JDK types
 * (File, String, Map) so the logic shard can build args and the app shard can
 * wire UI without a cross-shard type dependency.
 *
 * Returns a [GameProcess] handle with state/exitCode/cancel.
 */
interface PrimitiveGameLauncher {
    suspend fun launch(
        classpath: List<File>,
        jvmArgs: List<String>,
        gameArgs: List<String>,
        mainClass: String,
        nativesDir: File,
        env: Map<String, String>,
        workingDir: File,
    ): GameProcess
}

/**
 * Flint implementation of the primitive GameLauncher contract.
 *
 * Wires together: NativeLibraryLoader (native paths + plugin preload),
 * JvmController (non-blocking JVM start + crash recovery), and NativeBridge
 * (JLI_Launch). The existing PojavStyleGameLauncher (LaunchRequest-based)
 * remains available for the higher-level flow.
 */
class FlintGameLauncher(
    private val runtime: JavaRuntimeInfo,
    private val pluginDir: File?,
    private val crashLogDir: File,
) : PrimitiveGameLauncher {
    private val TAG = "FlintGameLauncher"
    private val jvmController by lazy { JvmController(crashLogDir) }

    override suspend fun launch(
        classpath: List<File>,
        jvmArgs: List<String>,
        gameArgs: List<String>,
        mainClass: String,
        nativesDir: File,
        env: Map<String, String>,
        workingDir: File,
    ): GameProcess = withContext(Dispatchers.IO) {
        // 1. Validate.
        require(mainClass.isNotBlank()) { "mainClass must not be blank" }
        require(classpath.isNotEmpty() && classpath.all { it.isFile }) {
            "classpath is empty or contains missing files: " +
                classpath.filter { !it.isFile }.joinToString { it.name }
        }
        require(nativesDir.isDirectory) { "nativesDir must be a directory: $nativesDir" }
        require(workingDir.isDirectory || workingDir.mkdirs()) { "workingDir not usable: $workingDir" }
        check(NativeBridge.isNativeLoaded) {
            "flint_native not loaded: ${NativeBridge.nativeLoadError}"
        }

        // 2. Load JRE libjli.
        if (!NativeBridge.nativeLoadJre(runtime.homeDir.absolutePath)) {
            throw IllegalStateException("Failed to load libjli.so from ${runtime.homeDir}")
        }

        // 3. Prepare native library paths + plugins.
        val libResult = NativeLibraryLoader.prepare(nativesDir, pluginDir)
        val fullEnv = env.toMutableMap()
        if (libResult.ldLibraryPath.isNotEmpty()) {
            fullEnv["LD_LIBRARY_PATH"] = libResult.ldLibraryPath +
                (env["LD_LIBRARY_PATH"]?.let { ":$it" } ?: "")
        }

        // 4. Build argv.
        val args = mutableListOf<String>()
        args += "java"
        args += jvmArgs
        // Ensure java.library.path includes our natives (append if caller didn't set).
        if (!jvmArgs.any { it.startsWith("-Djava.library.path=") }) {
            args += NativeLibraryLoader.javaLibraryPathArg(libResult)
        }
        // user.dir = workingDir (Minecraft expects this).
        if (!jvmArgs.any { it.startsWith("-Duser.dir=") }) {
            args += "-Duser.dir=${workingDir.absolutePath}"
        }
        val hasCp = jvmArgs.any { it == "-cp" || it == "-classpath" || it.startsWith("-cp=") || it.startsWith("-classpath=") }
        if (!hasCp) {
            args += "-cp"
            args += classpath.joinToString(File.pathSeparator) { it.absolutePath }
        }
        args += mainClass
        args += gameArgs

        Log.i(TAG, "Launching $mainClass with ${args.size} args; classpath=${classpath.size} entries")
        Log.i(TAG, "Native libs: preloaded=${libResult.preloaded}; missing=${libResult.missing}")

        // 5. Start JVM non-blocking.
        jvmController.start(args.toTypedArray(), fullEnv)
    }

    fun runningProcess(): GameProcess? = jvmController.runningProcess
    suspend fun shutdown() = jvmController.shutdown()
    fun hasPendingCrash(): Boolean = jvmController.hasPendingCrash()
    fun lastCrashReport(): String? = jvmController.lastCrashReport()
    fun clearCrashReport() = jvmController.clearCrashReport()
}
