package dev.pikaia.android.sdk.auth.token

import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * In-memory token storage for testing purposes.
 *
 * This implementation stores tokens in memory and does not persist them.
 * All tokens are lost when the process terminates.
 *
 * **Warning**: This is not secure for production use. Use [DataStoreTokenStore]
 * for production applications.
 *
 * Thread-safe using Mutex for concurrent access.
 */
class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()

    private var accessToken: String? = null
    private var refreshToken: String? = null
    private var deviceToken: String? = null
    private var deviceRefreshToken: String? = null

    override suspend fun getAccessToken(): String? = mutex.withLock {
        accessToken
    }

    override suspend fun getRefreshToken(): String? = mutex.withLock {
        refreshToken
    }

    override suspend fun getDeviceToken(): String? = mutex.withLock {
        deviceToken
    }

    override suspend fun getDeviceRefreshToken(): String? = mutex.withLock {
        deviceRefreshToken
    }

    override suspend fun setTokens(access: String, refresh: String) = mutex.withLock {
        accessToken = access
        refreshToken = refresh
    }

    override suspend fun setDeviceTokens(device: String, refresh: String) = mutex.withLock {
        deviceToken = device
        deviceRefreshToken = refresh
    }

    override suspend fun clear() = mutex.withLock {
        accessToken = null
        refreshToken = null
        deviceToken = null
        deviceRefreshToken = null
    }
}
