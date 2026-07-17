package dev.pikaia.android.sdk.core.auth

/**
 * Abstraction for authentication token refresh logic.
 *
 * Implementations should provide the logic to refresh authentication tokens
 * when they expire. This allows the API client to automatically refresh
 * tokens on 401 responses.
 *
 * This interface will be fully implemented in sdk-auth module.
 */
interface AuthProvider {
    /**
     * Refresh authentication tokens using the refresh token.
     *
     * @param refreshToken The refresh token to use
     * @return A pair of (access token, new refresh token). The new refresh token
     *         may be null if the server doesn't issue a new one.
     * @throws Exception if token refresh fails
     */
    suspend fun refreshTokens(refreshToken: String): Pair<String, String?>
}
