package dev.pikaia.android.sdk.core.auth

/**
 * Storage abstraction for the authenticated session.
 *
 * Implementations must persist the session securely (see sdk-auth's `DataStoreTokenStore`
 * for a Keystore-encrypted implementation) and be safe to call from concurrent coroutines.
 */
interface TokenStore {
    /**
     * Get the currently stored session.
     *
     * @return The session, or null when no user is logged in
     */
    suspend fun getSession(): AuthSession?

    /**
     * Replace the stored session.
     *
     * @param session The new session to persist
     */
    suspend fun setSession(session: AuthSession)

    /**
     * Remove any stored session.
     */
    suspend fun clear()
}
