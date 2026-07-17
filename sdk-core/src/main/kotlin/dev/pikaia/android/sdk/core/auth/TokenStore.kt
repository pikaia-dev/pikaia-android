package dev.pikaia.android.sdk.core.auth

/**
 * Storage abstraction for authentication tokens.
 *
 * Implementations should provide secure storage for tokens, such as
 * EncryptedSharedPreferences or other encrypted storage mechanisms.
 *
 * This interface will be fully implemented in sdk-auth module.
 */
interface TokenStore {
    /**
     * Get the current access token.
     *
     * @return Access token, or null if not set
     */
    suspend fun getAccessToken(): String?

    /**
     * Get the current refresh token.
     *
     * @return Refresh token, or null if not set
     */
    suspend fun getRefreshToken(): String?

    /**
     * Get the current device token.
     *
     * @return Device token, or null if not set
     */
    suspend fun getDeviceToken(): String?

    /**
     * Get the current device refresh token.
     *
     * @return Device refresh token, or null if not set
     */
    suspend fun getDeviceRefreshToken(): String?

    /**
     * Set access and refresh tokens.
     *
     * @param access Access token
     * @param refresh Refresh token
     */
    suspend fun setTokens(access: String, refresh: String)

    /**
     * Set device and device refresh tokens.
     *
     * @param device Device token
     * @param refresh Device refresh token
     */
    suspend fun setDeviceTokens(device: String, refresh: String)

    /**
     * Clear all stored tokens.
     */
    suspend fun clear()
}
