package dev.pikaia.android.sdk.auth.refresh

import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.auth.RefreshCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Default implementation of [RefreshCoordinator] that coalesces concurrent refresh requests.
 *
 * When multiple API requests fail with 401 simultaneously, only a single refresh call is
 * made to the server; every waiter receives the same result (or the same failure).
 *
 * The mutex only guards the in-flight task reference — waiters await *outside* the
 * critical section. The refresh itself runs on [scope], so it is not cancelled when an
 * individual awaiting request is; other waiters still get their session.
 *
 * @param scope Scope the refresh call runs in; defaults to an IO supervisor scope
 */
class DefaultRefreshCoordinator(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) : RefreshCoordinator {
    private val mutex = Mutex()
    private var inFlight: Deferred<AuthSession>? = null

    override suspend fun refresh(current: AuthSession, authProvider: AuthProvider): AuthSession {
        val task = mutex.withLock {
            inFlight ?: scope.async {
                try {
                    authProvider.refreshSession(current)
                } finally {
                    mutex.withLock { inFlight = null }
                }
            }.also { inFlight = it }
        }
        return task.await()
    }
}
