package dev.pikaia.android.sdk.sync.retry

import kotlinx.datetime.Clock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class SyncRetryPolicyTest {

    @Test
    fun `calculateNextRetry returns exponential backoff`() {
        val policy = SyncRetryPolicy(maxRetryAttempts = 5, maxDelaySeconds = 300)
        val now = Clock.System.now()

        // Retry 0: 2^0 = 1 second
        val retry0 = policy.calculateNextRetry(0)
        assertTrue(retry0 > now)
        assertTrue(retry0 <= now + 2.seconds)

        // Retry 1: 2^1 = 2 seconds
        val retry1 = policy.calculateNextRetry(1)
        assertTrue(retry1 > now + 1.seconds)
        assertTrue(retry1 <= now + 3.seconds)

        // Retry 2: 2^2 = 4 seconds
        val retry2 = policy.calculateNextRetry(2)
        assertTrue(retry2 > now + 3.seconds)
        assertTrue(retry2 <= now + 5.seconds)

        // Retry 3: 2^3 = 8 seconds
        val retry3 = policy.calculateNextRetry(3)
        assertTrue(retry3 > now + 7.seconds)
        assertTrue(retry3 <= now + 9.seconds)
    }

    @Test
    fun `calculateNextRetry caps at maxDelaySeconds`() {
        val policy = SyncRetryPolicy(maxRetryAttempts = 10, maxDelaySeconds = 60)
        val now = Clock.System.now()

        // Retry 10: 2^10 = 1024 seconds, should cap at 60
        val retry10 = policy.calculateNextRetry(10)
        assertTrue(retry10 > now + 59.seconds)
        assertTrue(retry10 <= now + 61.seconds)
    }

    @Test
    fun `shouldRetry returns true when under max attempts`() {
        val policy = SyncRetryPolicy(maxRetryAttempts = 5)

        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(1))
        assertTrue(policy.shouldRetry(2))
        assertTrue(policy.shouldRetry(3))
        assertTrue(policy.shouldRetry(4))
    }

    @Test
    fun `shouldRetry returns false when at or over max attempts`() {
        val policy = SyncRetryPolicy(maxRetryAttempts = 5)

        assertFalse(policy.shouldRetry(5))
        assertFalse(policy.shouldRetry(6))
        assertFalse(policy.shouldRetry(10))
    }

    @Test
    fun `default policy has 5 max attempts`() {
        val policy = SyncRetryPolicy()

        assertTrue(policy.shouldRetry(0))
        assertTrue(policy.shouldRetry(4))
        assertFalse(policy.shouldRetry(5))
    }

    @Test
    fun `default policy has 5 minute max delay`() {
        val policy = SyncRetryPolicy()
        val now = Clock.System.now()

        // Large retry count should cap at 5 minutes (300 seconds)
        val retry = policy.calculateNextRetry(100)
        assertTrue(retry > now + 299.seconds)
        assertTrue(retry <= now + 301.seconds)
    }
}
