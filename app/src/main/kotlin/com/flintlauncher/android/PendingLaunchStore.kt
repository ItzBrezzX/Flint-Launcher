package com.flintlauncher.android

import com.flintlauncher.android.runtime.LaunchRequest
import java.util.concurrent.atomic.AtomicReference

/**
 * Cross-activity hand-off for a prepared Minecraft launch.
 * PlayLaunchCoordinator stages the request; GameActivity consumes it after the
 * Android Surface / EGL / GLFW chain is ready.
 */
object PendingLaunchStore {
    private val pending = AtomicReference<LaunchRequest?>(null)
    private val lastError = AtomicReference<String?>(null)

    fun set(request: LaunchRequest) {
        lastError.set(null)
        pending.set(request)
    }

    fun take(): LaunchRequest? = pending.getAndSet(null)

    fun peek(): LaunchRequest? = pending.get()

    fun reportError(message: String) {
        lastError.set(message)
    }

    fun consumeError(): String? = lastError.getAndSet(null)
}
