package com.flintlauncher.android.minecraft.session

import com.flintlauncher.android.core.FlintError

/**
 * States of a single game session. The runtime shard drives transitions; [transition]
 * rejects illegal moves so a bug in the process wrapper cannot silently report "running"
 * after a crash. Terminal states ([EXITED_CLEAN], [EXITED_CRASH], [FAILED]) can only be
 * reached by [reset] back to [IDLE].
 */
enum class SessionState {
    IDLE,
    PREPARING,      // installing / verifying files before fork
    LAUNCHING,      // JVM forked, process alive, first frame not confirmed
    RUNNING,        // process alive and confirmed (first log line / window shown)
    STOPPING,       // stop() requested, waiting on the process
    EXITED_CLEAN,   // exit code 0
    EXITED_CRASH,   // nonzero exit, categorized as a crash
    FAILED,         // could not start (missing runtime, download failed, auth, config)
    ;

    val isTerminal: Boolean get() = this == EXITED_CLEAN || this == EXITED_CRASH || this == FAILED
    val isAlive: Boolean get() = this == LAUNCHING || this == RUNNING || this == STOPPING
}

/**
 * Pure state machine for one game session. No process handling — the runtime shard calls
 * [transition] and [reportExit] as the underlying process evolves. Categorization of a
 * nonzero exit is delegated to [ExitCodeClassifier] so it is unit-testable without a JVM.
 */
class GameSession(
    val versionId: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    var state: SessionState = SessionState.IDLE
        private set
    var exitCode: Int? = null
        private set
    var startedAt: Long? = null
        private set
    var endedAt: Long? = null
        private set
    /** Categorized error when [state] is [SessionState.FAILED] or [SessionState.EXITED_CRASH]. */
    var error: FlintError? = null
        private set

    fun transition(target: SessionState) {
        check(!state.isTerminal || target == SessionState.IDLE) {
            "Cannot leave terminal state $state except by reset(); got $target"
        }
        check(target != state) { "Already in state $state" }
        if (target == SessionState.LAUNCHING && startedAt == null) startedAt = clock()
        if (target == SessionState.RUNNING && startedAt == null) startedAt = clock()
        state = target
    }

    fun reset() {
        state = SessionState.IDLE
        exitCode = null
        startedAt = null
        endedAt = null
        error = null
    }

    /** Record a process exit and categorize it. Returns the categorized error (null for clean). */
    fun reportExit(exitCode: Int, logSnippet: String? = null): FlintError? {
        endedAt = clock()
        this.exitCode = exitCode
        val categorized = ExitCodeClassifier.classify(versionId, exitCode, logSnippet)
        error = categorized
        state = if (categorized == null) SessionState.EXITED_CLEAN else SessionState.EXITED_CRASH
        return categorized
    }

    /** Record a pre-launch failure (missing runtime, download failed, auth, config). */
    fun fail(error: FlintError) {
        endedAt = clock()
        this.error = error
        state = SessionState.FAILED
    }

    val durationMillis: Long?
        get() {
            val s = startedAt ?: return null
            val e = endedAt ?: clock()
            return (e - s).coerceAtLeast(0)
        }
}

/**
 * Maps a process exit code + log tail into a categorized [FlintError].
 *
 * Exit code 0 is always clean. Nonzero is a crash unless the log reveals a configuration
 * problem (bad memory, missing main class, native library failure) which we surface as
 * [FlintError.ConfigError] so the UI can offer "edit profile" instead of "report crash".
 * Signals (137 = SIGKILL, 143 = SIGTERM) are treated as clean user-initiated stops.
 */
object ExitCodeClassifier {
    private const val SIGTERM = 143
    private const val SIGKILL = 137

    fun classify(versionId: String, exitCode: Int, logSnippet: String?): FlintError? {
        if (exitCode == 0 || exitCode == SIGTERM || exitCode == SIGKILL) return null
        val log = logSnippet.orEmpty()
        val configReason = configReason(log)
        if (configReason != null) {
            return FlintError.ConfigError("Minecraft $versionId failed to start: $configReason")
        }
        val memoryHint = if (log.contains("OutOfMemoryError", ignoreCase = true)) " — try increasing allocated memory" else ""
        return FlintError.Crash(
            message = "Minecraft $versionId crashed with exit code $exitCode$memoryHint",
            exitCode = exitCode,
            logSnippet = log.take(4000),
        )
    }

    /** Returns a human-readable config cause when the log shows the failure was our setup. */
    private fun configReason(log: String): String? = when {
        log.contains("Could not find or load main class", ignoreCase = true) ||
            log.contains("ClassNotFoundException: net.minecraft", ignoreCase = true) ->
            "the main class is missing (version metadata or client jar is corrupt)"
        log.contains("Invalid maximum heap size", ignoreCase = true) ||
            log.contains("The specified size exceeds the maximum representable size", ignoreCase = true) ->
            "the allocated memory size is invalid"
        log.contains("UnsatisfiedLinkError", ignoreCase = true) ||
            log.contains("no lwjgl", ignoreCase = true) ||
            log.contains("Could not initialize class org.lwjgl", ignoreCase = true) ->
            "a native library failed to load"
        log.contains("UnsupportedClassVersionError", ignoreCase = true) ->
            "the Java runtime version is too old for this Minecraft version"
        else -> null
    }
}
