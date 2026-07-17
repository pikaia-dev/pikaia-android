package dev.pikaia.android.sdk.auth.token

import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.auth.TokenStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Non-persistent [TokenStore] holding the session in memory.
 *
 * Intended as the test double; production code should use [DataStoreTokenStore].
 */
class InMemoryTokenStore : TokenStore {
    private val mutex = Mutex()
    private var session: AuthSession? = null

    override suspend fun getSession(): AuthSession? = mutex.withLock { session }

    override suspend fun setSession(session: AuthSession) = mutex.withLock {
        this.session = session
    }

    override suspend fun clear() = mutex.withLock {
        session = null
    }
}
