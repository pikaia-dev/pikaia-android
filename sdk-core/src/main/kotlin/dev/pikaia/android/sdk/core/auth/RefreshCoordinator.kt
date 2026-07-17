package dev.pikaia.android.sdk.core.auth

/**
 * Coordinator for session refresh operations.
 *
 * The refresh coordinator ensures that concurrent refresh requests are coalesced into a
 * single call: when multiple requests fail with 401 simultaneously, only one refresh hits
 * the server and every waiter receives the same result.
 */
interface RefreshCoordinator {
    /**
     * Refresh the session, coalescing with any refresh already in flight.
     *
     * @param current The session that failed authentication
     * @param authProvider The provider that performs the actual refresh
     * @return The refreshed session
     * @throws Exception if the refresh fails; the same failure is delivered to every waiter
     */
    suspend fun refresh(current: AuthSession, authProvider: AuthProvider): AuthSession
}
