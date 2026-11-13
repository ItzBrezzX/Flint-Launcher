package com.flintlauncher.android.download

/**
 * Speed over a sliding window (current) and over the whole run excluding paused
 * time (average). Time is passed in, so the maths is deterministic and testable.
 * Not thread-safe: callers synchronize.
 */
class SpeedTracker(private val windowMillis: Long = 3_000) {
    private class Sample(val timeMillis: Long, val bytes: Long)

    private val samples = ArrayDeque<Sample>()
    private var startMillis = 0L
    private var startBytes = 0L
    private var started = false
    private var pausedTotalMillis = 0L
    private var pausedAtMillis: Long? = null

    fun reset() {
        samples.clear()
        started = false
        pausedTotalMillis = 0L
        pausedAtMillis = null
    }

    /** [networkBytes] counts only bytes actually received from the network. */
    fun record(nowMillis: Long, networkBytes: Long) {
        if (!started) {
            started = true
            startMillis = nowMillis
            startBytes = networkBytes
        }
        samples.addLast(Sample(nowMillis, networkBytes))
        while (samples.size > 1 && nowMillis - samples.first().timeMillis > windowMillis) {
            samples.removeFirst()
        }
    }

    fun currentSpeed(): Long {
        if (samples.size < 2) return 0L
        val first = samples.first()
        val last = samples.last()
        val elapsed = last.timeMillis - first.timeMillis
        if (elapsed <= 0) return 0L
        return (last.bytes - first.bytes).coerceAtLeast(0) * 1000 / elapsed
    }

    fun averageSpeed(nowMillis: Long, networkBytes: Long): Long {
        if (!started) return 0L
        val currentPause = pausedAtMillis?.let { nowMillis - it } ?: 0L
        val elapsed = nowMillis - startMillis - pausedTotalMillis - currentPause
        if (elapsed <= 0) return 0L
        return (networkBytes - startBytes).coerceAtLeast(0) * 1000 / elapsed
    }

    fun pause(nowMillis: Long) {
        if (pausedAtMillis == null) pausedAtMillis = nowMillis
    }

    fun resume(nowMillis: Long) {
        pausedAtMillis?.let { pausedTotalMillis += nowMillis - it }
        pausedAtMillis = null
        samples.clear() // do not let the idle gap drag the current speed down
    }

    companion object {
        /** Whole seconds remaining, rounded up; null when it cannot be known honestly. */
        fun etaSeconds(remainingBytes: Long?, speedBytesPerSecond: Long): Long? {
            if (remainingBytes == null || speedBytesPerSecond <= 0) return null
            return (remainingBytes + speedBytesPerSecond - 1) / speedBytesPerSecond
        }
    }
}
