package com.flintlauncher.android

import android.os.Process
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.exitProcess

/**
 * Stores a bounded, redacted crash report before delegating to Android's original
 * uncaught-exception handler. It never suppresses the process crash.
 */
object FlintCrashHandler {
    private const val TAG = "FlintCrash"
    private const val MAX_REPORTS = 5
    private const val MAX_REPORT_CHARS = 128 * 1024
    private val installed = AtomicBoolean(false)

    fun install(filesDir: File) {
        if (!installed.compareAndSet(false, true)) return
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            try {
                val saved = saveReport(filesDir, thread, error)
                Log.e(TAG, "Crash report saved: ${saved.absolutePath}; forwarding original crash.")
            } catch (writeError: Throwable) {
                Log.e(TAG, "Could not save crash report; forwarding original crash", writeError)
                Log.e(TAG, "Original uncaught exception", error)
            }

            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun latestReport(filesDir: File): File? =
        File(filesDir, CRASH_DIRECTORY)
            .listFiles { file -> file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".log") }
            .orEmpty()
            .maxByOrNull { it.lastModified() }

    internal fun redactSecrets(value: String): String {
        val assignments = Regex(
            """(?i)((?:access|refresh|xsts|client)[_-]?(?:token|secret)|authorization)["']?\s*([:=])\s*("[^"]*"|'[^']*'|[^\s,;&]+)""",
        )
        val bearer = Regex("""(?i)\bBearer\s+[^\s,;"']+""")
        val withoutBearerSecrets = bearer.replace(value, "Bearer <redacted>")
        return assignments.replace(withoutBearerSecrets) { match ->
            match.groupValues[1] + match.groupValues[2] + "<redacted>"
        }
    }

    private fun saveReport(filesDir: File, thread: Thread, error: Throwable): File {
        val directory = File(filesDir, CRASH_DIRECTORY)
        check(directory.isDirectory || directory.mkdirs()) {
            "Could not create crash report directory"
        }

        val stamp = System.currentTimeMillis()
        val destination = File(directory, "crash-$stamp-${thread.id}.log")
        val temporary = File(directory, ".crash-$stamp-${thread.id}.tmp")
        val raw = buildString {
            append("FLINT CRASH REPORT\n")
            append("Time: ").append(java.time.Instant.ofEpochMilli(stamp)).append('\n')
            append("Thread: ").append(thread.name).append(" (").append(thread.id).append(")\n")
            append("Android: ").append(android.os.Build.VERSION.RELEASE).append('\n')
            append("Device: ").append(android.os.Build.MODEL).append("\n\n")
            append(error.stackTraceToString())
        }
        val report = redactSecrets(raw).take(MAX_REPORT_CHARS)

        try {
            FileOutputStream(temporary).use { stream ->
                stream.write(report.toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            try {
                Files.move(
                    temporary.toPath(),
                    destination.toPath(),
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: Exception) {
                if (!temporary.renameTo(destination)) {
                    Files.move(temporary.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            if (temporary.exists()) temporary.delete()
        }

        directory.listFiles { file ->
            file.isFile && file.name.startsWith("crash-") && file.name.endsWith(".log")
        }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .drop(MAX_REPORTS)
            .forEach { it.delete() }

        return destination
    }

    private const val CRASH_DIRECTORY = "crash-reports"
}
