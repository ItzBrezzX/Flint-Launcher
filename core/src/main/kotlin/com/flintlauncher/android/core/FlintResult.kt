package com.flintlauncher.android.core

/**
 * Railway-style result used across the logic shard. Errors are typed so callers can
 * categorize failures (missing runtime, download failed, crash, auth) without parsing
 * strings. [E] is covariant so a more specific error type can be returned where useful.
 */
sealed interface FlintResult<out T, out E> {
    data class Success<T, E>(val value: T) : FlintResult<T, E>
    data class Error<T, E>(val error: E) : FlintResult<T, E>

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error

    fun getOrNull(): T? = (this as? Success)?.value
    fun errorOrNull(): E? = (this as? Error)?.error

    inline fun <R> map(transform: (T) -> R): FlintResult<R, E> = when (this) {
        is Success -> Success(transform(value))
        is Error -> Error(error)
    }

    inline fun <R> flatMap(transform: (T) -> FlintResult<R, @UnsafeVariance E>): FlintResult<R, E> = when (this) {
        is Success -> transform(value)
        is Error -> Error(error)
    }

    inline fun onSuccess(action: (T) -> Unit): FlintResult<T, E> {
        if (this is Success) action(value)
        return this
    }

    inline fun onError(action: (E) -> Unit): FlintResult<T, E> {
        if (this is Error) action(error)
        return this
    }

    companion object {
        fun <T> success(value: T): FlintResult<T, Nothing> = Success(value)
        fun <E> error(error: E): FlintResult<Nothing, E> = Error(error)
    }
}

/** Convenience for the common case where the error is a [FlintError]. */
typealias Outcome<T> = FlintResult<T, FlintError>
