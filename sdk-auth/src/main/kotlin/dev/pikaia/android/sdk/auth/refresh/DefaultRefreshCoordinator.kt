package dev.pikaia.android.sdk.auth.refresh

import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.RefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default implementation of [RefreshCoordinator] that coalesces concurrent refresh requests.
 *
 * This coordinator ensures that when multiple API requests fail with 401 simultaneously,
 * only a single token refresh call is made to the server. All waiting requests receive
 * the same refreshed tokens.
 *
 * ## How it works:
 *
 * 1. When the first request needs a refresh, it starts the refresh operation
 * 2. While the refresh is in progress, subsequent requests wait for the same operation
 * 3. All requests receive the same refreshed tokens
 * 4. After completion, the state is cleared for the next refresh
 *
 * ## Example scenario:
 *
 * ```
 * // 3 requests fail with 401 simultaneously
 * Request A: refresh() → Starts network call
 * Request B: refresh() → Waits for A's result
 * Request C: refresh() → Waits for A's result
 *
 * // Network call completes
 * All 3 requests: Receive the same new tokens
 *
 * Result: Only 1 network call instead of 3
 * ```
 *
 * Thread-safe using [Mutex] for critical section protection.
 */
class DefaultRefreshCoordinator : RefreshCoordinator {
    private val mutex = Mutex()
    private var currentRefreshTask: Deferred<Pair<String, String?>>? = null

    override suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?> {
        // Enter critical section - only one coroutine at a time
        mutex.withLock {
            // Check if a refresh is already in progress
            currentRefreshTask?.let { existingTask ->
                // Refresh already in progress - wait for it and return the same result
                // This is the key optimization: we don't start a new refresh,
                // we just await the existing one
                return existingTask.await()
            }

            // No refresh in progress - start a new one
            val refreshTask = CoroutineScope(Dispatchers.IO).async {
                try {
                    // Call the auth provider to perform the actual token refresh
                    authProvider.refreshTokens(refreshToken)
                } finally {
                    // Always clean up the task reference when complete
                    // This ensures the next refresh attempt will start fresh
                    mutex.withLock {
                        currentRefreshTask = null
                    }
                }
            }

            // Store the task so concurrent requests can await it
            currentRefreshTask = refreshTask

            // Wait for our own refresh to complete and return the result
            return refreshTask.await()
        }
    }
}
