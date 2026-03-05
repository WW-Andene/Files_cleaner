package com.filecleaner.app.utils

import com.jcraft.jsch.JSchException
import kotlinx.coroutines.delay
import java.io.IOException
import java.util.concurrent.CancellationException

/**
 * Retries a block up to [maxRetries] times with exponential backoff
 * on transient network errors. Non-retryable exceptions (e.g. auth
 * failures) are thrown immediately.
 *
 * Must be called from a coroutine context. Uses [delay] instead of
 * Thread.sleep to cooperate with coroutine cancellation.
 */
@Suppress("MagicNumber")
suspend inline fun <T> retryOnNetworkError(
    maxRetries: Int = 3,
    initialDelayMs: Long = 1000L,
    block: () -> T
): T {
    var lastException: Exception? = null
    repeat(maxRetries) { attempt ->
        try {
            return block()
        } catch (e: Exception) {
            // Always rethrow cancellation to preserve coroutine cooperative cancellation
            if (e is CancellationException) throw e
            if (!isRetryable(e)) throw e
            lastException = e
            if (attempt < maxRetries - 1) {
                val delayMs = (initialDelayMs * (1L shl attempt)).coerceAtMost(30_000L)
                delay(delayMs)
            }
        }
    }
    throw lastException!!
}

/**
 * Returns true if the exception represents a transient network error
 * that is worth retrying.
 */
fun isRetryable(e: Exception): Boolean {
    // JSch auth failures should not be retried
    if (e is JSchException && e.message?.contains("Auth fail", ignoreCase = true) == true) {
        return false
    }
    // Retry on known network/IO exceptions (SocketTimeoutException, ConnectException are subclasses of IOException)
    if (e is IOException) {
        return true
    }
    // JSch wraps network errors in JSchException
    if (e is JSchException) {
        val cause = e.cause
        if (cause is IOException) {
            return true
        }
        // Connection-related JSch errors (e.g. "timeout" in connect, "connection is closed")
        val msg = e.message?.lowercase() ?: ""
        if ("timeout" in msg || "connection" in msg || "socket" in msg) {
            return true
        }
    }
    return false
}
