package dev.pikaia.android.sdk.sync.retry

import kotlin.time.Clock
import kotlin.time.Instant
import kotlin.math.min
import kotlin.math.pow

/**
 * Retry policy for failed sync operations.
 *
 * Implements exponential backoff: 2^retryCount seconds, max 5 minutes.
 */
class SyncRetryPolicy(
    private val maxRetryAttempts: Int = 5,
    private val maxDelaySeconds: Int = 300  // 5 minutes
) {

    /**
     * Calculate the next retry time based on retry count.
     *
     * Uses exponential backoff: 2^retryCount seconds, capped at maxDelaySeconds.
     *
     * Examples:
     * - Retry 0: 1 second
     * - Retry 1: 2 seconds
     * - Retry 2: 4 seconds
     * - Retry 3: 8 seconds
     * - Retry 4: 16 seconds
     * - Retry 5+: 300 seconds (max)
     *
     * @param retryCount Current retry count (0-based)
     * @return Instant when next retry should occur
     */
    fun calculateNextRetry(retryCount: Int): Instant {
        val delaySeconds = min(
            2.0.pow(retryCount).toInt(),
            maxDelaySeconds
        )
        return Clock.System.now() + kotlin.time.Duration.parse("${delaySeconds}s")
    }

    /**
     * Check if operation should be retried.
     *
     * @param retryCount Current retry count
     * @return true if should retry, false if max attempts exceeded
     */
    fun shouldRetry(retryCount: Int): Boolean {
        return retryCount < maxRetryAttempts
    }

    /**
     * Get maximum retry attempts.
     */
    fun getMaxRetryAttempts(): Int = maxRetryAttempts
}
