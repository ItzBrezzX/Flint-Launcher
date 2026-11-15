package com.flintlauncher.android.runtime

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/**
 * Handle to a running Minecraft JVM process. Returned by GameLauncher.launch.
 *
 * State machine: STARTING → RUNNING → (EXITED | CRASHED | CANCELLED).
 * exitCode is only meaningful in EXITED state.
 */
class GameProcess internal constructor(
    val id: String,
    @Volatile var thread: Thread? = null,
) {
    enum class State { STARTING, RUNNING, EXITED, CRASHED, CANCELLED }

    private val stateRef = AtomicReference(State.STARTING)
    private val exitCodeRef = AtomicInteger(Int.MIN_VALUE)
    @Volatile var crashReport: String? = null
        internal set
    @Volatile var logSnippet: String? = null
        internal set

    val state: State get() = stateRef.get()
    val exitCode: Int? get() = if (stateRef.get() == State.EXITED) exitCodeRef.get() else null
    val isAlive: Boolean get() = stateRef.get() == State.STARTING || stateRef.get() == State.RUNNING

    internal fun transition(to: State) {
        stateRef.set(to)
    }
    internal fun setExitCode(code: Int) {
        exitCodeRef.set(code)
    }

    /**
     * Request cancellation. Spike 0: interrupts the JVM launch thread and marks
     * the process CANCELLED. A hard JVM termination hook (nativeDestroyJavaVM)
     * is not yet exposed by the native bridge; tracked in progress doc.
     */
    fun cancel() {
        if (!isAlive) return
        transition(State.CANCELLED)
        thread?.interrupt()
    }

    /** Block until the process exits (or timeout). Returns final state. */
    fun waitFor(timeoutMs: Long = 0): State {
        val t = thread ?: return state
        try {
            if (timeoutMs > 0) t.join(timeoutMs) else t.join()
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        return state
    }
}
