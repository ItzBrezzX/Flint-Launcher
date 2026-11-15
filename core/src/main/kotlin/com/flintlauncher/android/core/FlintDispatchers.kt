package com.flintlauncher.android.core

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

/**
 * Injectable dispatchers so unit tests can run everything on a single thread.
 * The pure-JVM modules have no Android main looper; [main] falls back to [Default].
 */
interface FlintDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher

    companion object Default : FlintDispatchers {
        override val io: CoroutineDispatcher get() = Dispatchers.IO
        override val default: CoroutineDispatcher get() = Dispatchers.Default
        override val main: CoroutineDispatcher get() = Dispatchers.Default
    }
}
