package com.flintlauncher.android.runtime.testsupport

import com.flintlauncher.android.nativebridge.NativeBridge
import com.flintlauncher.android.runtime.JavaRuntimeInfo
import com.flintlauncher.android.runtime.MobileJreManager
import java.io.File
import java.util.Base64

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
     * Writes a tiny Java 8 class file directly. Runtime-only Android JRE packages
     * do not necessarily ship javac, so the device probe must not depend on it.
     */
    fun writeProbeClass(dir: File): File {
        dir.mkdirs()
        val target = File(dir, "FlintJvmProbe.class")
        val bytes = Base64.getDecoder().decode("yv66vgAAADQAHAEADUZsaW50SnZtUHJvYmUHAAEBABBqYXZhL2xhbmcvT2JqZWN0BwADAQAGPGluaXQ+AQADKClWAQAEQ29kZQwABQAGCgAEAAgBAARtYWluAQAWKFtMamF2YS9sYW5nL1N0cmluZzspVgEAEGphdmEvbGFuZy9TeXN0ZW0HAAwBAANvdXQBABVMamF2YS9pby9QcmludFN0cmVhbTsMAA4ADwkADQAQAQATamF2YS9pby9QcmludFN0cmVhbQcAEgEAB3ByaW50bG4BABUoTGphdmEvbGFuZy9TdHJpbmc7KVYMABQAFQoAEwAWAQAMRkxJTlRfSlZNX09LCAAYAQAKU291cmNlRmlsZQEAEkZsaW50SnZtUHJvYmUuamF2YQAhAAIABAAAAAAAAgABAAUABgABAAcAAAARAAEAAQAAAAUqtwAJsQAAAAAACQAKAAsAAQAHAAAAFQACAAEAAAAJsgAREhm2ABexAAAAAAABABoAAAACABs=")
        require(bytes.size >= 8 &&
            bytes[0] == 0xCA.toByte() &&
            bytes[1] == 0xFE.toByte() &&
            bytes[2] == 0xBA.toByte() &&
            bytes[3] == 0xBE.toByte()
        ) { "Embedded Flint JVM probe class is invalid" }
        target.writeBytes(bytes)
        return target
    }

    /**
     * Writes the equivalent Java source for inspection or manual debugging.
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
     * The probe class is generated into [classpathDir], so javac is not needed.
     *
     * This starts a process-global HotSpot VM. Run it in a disposable process;
     * after it returns, restart Flint before attempting a real game launch.
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
        notes += "native JRE status: ${NativeBridge.safeJreStatus()}"
        if (!jreOk) {
            return JvmProbeResult(true, null, false, null, notes)
        }

        val probeClass = try {
            writeProbeClass(classpathDir)
        } catch (error: Exception) {
            notes += "could not write probe class: ${error.message}"
            return JvmProbeResult(true, null, false, null, notes)
        }
        notes += "probe class written: ${probeClass.absolutePath}"
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
