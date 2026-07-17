package dev.pikaia.android.sdk.core.auth

/**
 * Abstraction for session refresh logic.
 *
 * Implementations exchange the current (possibly expired) session for a fresh one so the
 * API client can automatically recover from 401 responses. See sdk-auth's
 * `DeviceSessionAuthProvider` for the device-linked session implementation.
 */
interface AuthProvider {
    /**
     * Refresh the given session and return its replacement.
     *
     * @param current The session that failed authentication
     * @return The refreshed session
     * @throws Exception if the session cannot be refreshed (e.g. it was revoked or fully expired)
     */
    suspend fun refreshSession(current: AuthSession): AuthSession
}
