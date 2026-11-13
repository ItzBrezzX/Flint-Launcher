package com.flintlauncher.android.runtime.testsupport

import com.flintlauncher.android.nativebridge.NativeBridge
import com.flintlauncher.android.runtime.JavaRuntimeInfo
import com.flintlauncher.android.runtime.MobileJreManager
import java.io.File

/**
 * Host-side driver for Spike 0.3: load a mobile JRE and run a minimal Java main
 * through JNI → JLI_Launch.
 *
 * This class does not mock success. Every outcome is derived from native return codes
 * and filesystem probes.
 */
data class JvmProbeResult(
    val nativeLoaded: Boolean,
    val nativeLoadError: String?,
    val jreLoadOk: Boolean,
    val launchExitCode: Int?,
    val notes: List<String>,
) {
    val passed: Boolean
        get() = nativeLoaded && jreLoadOk && launchExitCode == 0
}

object MinimalJvmProbe {

    /**
     * Writes a tiny standalone .java source that prints FLINT_JVM_OK and exits 0.
     * The caller (or a device-side script) must compile it with the mobile javac
     * or ship a prebuilt classes.jar built against the same major.
     */
    fun writeProbeSource(dir: File): File {
        dir.mkdirs()
        val src = File(dir, "FlintJvmProbe.java")
        src.writeText(
            """
            public class FlintJvmProbe {
                public static void main(String[] args) {
                    System.out.println("FLINT_JVM_OK");
                    System.out.println("java.version=" + System.getProperty("java.version"));
                    System.out.println("os.arch=" + System.getProperty("os.arch"));
                    System.out.println("user.dir=" + System.getProperty("user.dir"));
                    System.out.flush();
                }
            }
            """.trimIndent() + "\n",
        )
        return src
    }

    /**
     * Attempt: nativeLoadJre(runtime) then nativeLaunchJvm(argv for FlintJvmProbe).
     *
     * [classpathDir] must contain FlintJvmProbe.class (or a jar listed in classpath).
     */
    fun run(
        runtime: JavaRuntimeInfo,
        classpathDir: File,
        workingDir: File = classpathDir,
    ): JvmProbeResult {
        val notes = mutableListOf<String>()
        if (!NativeBridge.isNativeLoaded) {
            return JvmProbeResult(
                nativeLoaded = false,
                nativeLoadError = NativeBridge.nativeLoadError,
                jreLoadOk = false,
                launchExitCode = null,
                notes = listOf("libflint_native.so not loaded — build :native for arm64-v8a and install the APK"),
            )
        }
        notes += "native library loaded"

        if (!runtime.verified) {
            notes += "runtime.verified=false — refuse to launch"
            return JvmProbeResult(true, null, false, null, notes)
        }

        val jreOk = NativeBridge.nativeLoadJre(runtime.homeDir.absolutePath)
        notes += if (jreOk) "nativeLoadJre=true" else "nativeLoadJre=false"
        if (!jreOk) {
            return JvmProbeResult(true, null, false, null, notes)
        }

        val cp = classpathDir.absolutePath
        val argv = arrayOf(
            "java",
            "-Duser.dir=${workingDir.absolutePath}",
            "-cp",
            cp,
            "FlintJvmProbe",
        )
        notes += "argv=" + argv.joinToString(" ")

        val code = try {
            NativeBridge.nativeLaunchJvm(argv)
        } catch (t: Throwable) {
            notes += "exception: ${t.message}"
            NativeBridge.nativeShutdown()
            return JvmProbeResult(true, null, true, null, notes)
        }
        notes += "JLI_Launch exit code=$code"
        NativeBridge.nativeShutdown()
        return JvmProbeResult(true, null, true, code, notes)
    }

    /**
     * Import helper used by device instrumentation / debug screens.
     */
    suspend fun importAndProbe(
        manager: MobileJreManager,
        archive: File,
        id: String,
        major: Int,
        sha256: String?,
        probeClasspath: File,
    ): JvmProbeResult {
        val imported = manager.importLocalArchive(archive, id, major, sha256)
        if (imported.isFailure) {
            return JvmProbeResult(
                nativeLoaded = NativeBridge.isNativeLoaded,
                nativeLoadError = NativeBridge.nativeLoadError,
                jreLoadOk = false,
                launchExitCode = null,
                notes = listOf("import failed: ${imported.exceptionOrNull()?.message}"),
            )
        }
        return run(imported.getOrThrow(), probeClasspath)
    }
}
