package dev.pikaia.android.sdk.core.auth

/**
 * Coordinator for token refresh operations.
 *
 * The refresh coordinator ensures that concurrent token refresh requests
 * are coalesced into a single request. This prevents race conditions where
 * multiple failing requests trigger multiple refresh attempts simultaneously.
 *
 * This interface will be fully implemented in sdk-auth module with a
 * DefaultRefreshCoordinator that uses Mutex for thread-safe coordination.
 */
interface RefreshCoordinator {
    /**
     * Refresh authentication tokens.
     *
     * If a refresh is already in progress, this method will wait for that
     * refresh to complete and return the same result rather than starting
     * a new refresh.
     *
     * @param refreshToken The refresh token to use
     * @param authProvider The auth provider to use for refreshing
     * @return A pair of (access token, new refresh token)
     * @throws Exception if token refresh fails
     */
    suspend fun refresh(
        refreshToken: String,
        authProvider: AuthProvider
    ): Pair<String, String?>
}
