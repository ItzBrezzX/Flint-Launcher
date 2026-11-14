package com.flintlauncher.android

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Small persistent diagnostic journal. It deliberately avoids account data and secrets.
 * Native process deaths cannot be caught by Thread.UncaughtExceptionHandler; breadcrumbs
 * and the unclean-session marker help identify the last completed launcher stage.
 */
object CrashDiagnostics {
    private const val LOG_FILE = "flint-diagnostics.log"
    private const val SESSION_FILE = "flint-session.active"
    private const val MAX_BYTES = 512 * 1024
    @Volatile private var initialized = false
    private lateinit var appContext: Context

    @Synchronized
    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        val session = File(appContext.filesDir, SESSION_FILE)
        if (session.exists()) {
            record("WARN", "Previous launcher session ended without a clean shutdown. A native crash, force-stop, or OS kill is possible.")
        }
        session.writeText("pid=${Process.myPid()} started=${now()}\n")
        record("INFO", "Launcher process started; Android=${Build.VERSION.RELEASE}; SDK=${Build.VERSION.SDK_INT}; model=${Build.MODEL}; ABIs=${Build.SUPPORTED_ABIS.joinToString()}")
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            recordThrowable("FATAL", "Uncaught exception on thread ${thread.name}", error)
            previous?.uncaughtException(thread, error) ?: Process.killProcess(Process.myPid())
        }
        initialized = true
    }

    @Synchronized
    fun record(level: String, message: String) {
        if (!::appContext.isInitialized) return
        runCatching {
            val file = File(appContext.filesDir, LOG_FILE)
            val line = "${now()} [${level.uppercase(Locale.ROOT)}] ${message.replace('\n', ' ')}\n"
            if (file.exists() && file.length() > MAX_BYTES) {
                val old = file.readLines().takeLast(250).joinToString("\n", postfix = "\n")
                file.writeText(old)
            }
            file.appendText(line)
        }
    }

    fun recordThrowable(level: String, message: String, error: Throwable) {
        val trace = StringWriter().also { writer -> error.printStackTrace(PrintWriter(writer)) }.toString()
        record(level, "${message}\n${trace}")
    }

    fun readReport(): String {
        if (!::appContext.isInitialized) return "Diagnostics are not initialized."
        val file = File(appContext.filesDir, LOG_FILE)
        return if (file.exists()) file.readText().takeLast(60_000) else "No diagnostic entries yet."
    }

    fun markCleanShutdown() {
        if (::appContext.isInitialized) File(appContext.filesDir, SESSION_FILE).delete()
    }

    private fun now(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).format(Date())
}
