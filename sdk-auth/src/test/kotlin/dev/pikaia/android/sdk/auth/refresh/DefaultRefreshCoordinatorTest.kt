package dev.pikaia.android.sdk.auth.refresh

import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.AuthSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class DefaultRefreshCoordinatorTest {

    private val session = AuthSession("old-jwt", "old-token", deviceUuid = "uuid-1")

    private class GatedProvider : AuthProvider {
        val gate = CompletableDeferred<Unit>()
        val calls = AtomicInteger(0)
        var failure: Exception? = null

        override suspend fun refreshSession(current: AuthSession): AuthSession {
            calls.incrementAndGet()
            gate.await()
            failure?.let { throw it }
            return AuthSession("new-jwt", "new-token", deviceUuid = current.deviceUuid)
        }
    }

    @Test
    fun `refresh completes without deadlock`() = runTest {
        val coordinator = DefaultRefreshCoordinator(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val provider = GatedProvider().apply { gate.complete(Unit) }

        val refreshed = coordinator.refresh(session, provider)

        assertEquals("new-jwt", refreshed.sessionJwt)
        assertEquals("uuid-1", refreshed.deviceUuid)
    }

    @Test
    fun `coalesces concurrent refreshes into a single provider call`() = runTest {
        val coordinator = DefaultRefreshCoordinator(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val provider = GatedProvider()

        val waiters = (1..20).map {
            async { coordinator.refresh(session, provider) }
        }
        advanceUntilIdle() // let every waiter reach the in-flight task
        provider.gate.complete(Unit)

        val results = waiters.awaitAll()

        assertEquals(1, provider.calls.get())
        assertTrue(results.all { it.sessionJwt == "new-jwt" })
    }

    @Test
    fun `a later refresh starts a new provider call`() = runTest {
        val coordinator = DefaultRefreshCoordinator(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val provider = GatedProvider().apply { gate.complete(Unit) }

        coordinator.refresh(session, provider)
        advanceUntilIdle() // let the in-flight slot clear
        coordinator.refresh(session, provider)

        assertEquals(2, provider.calls.get())
    }

    @Test
    fun `failure is delivered to every waiter and does not stick`() = runTest {
        val coordinator = DefaultRefreshCoordinator(
            CoroutineScope(SupervisorJob() + StandardTestDispatcher(testScheduler))
        )
        val provider = GatedProvider().apply { failure = IllegalStateException("revoked") }

        val waiters = (1..5).map {
            async { runCatching { coordinator.refresh(session, provider) } }
        }
        advanceUntilIdle()
        provider.gate.complete(Unit)

        val results = waiters.awaitAll()

        assertEquals(1, provider.calls.get())
        assertTrue(results.all { it.exceptionOrNull()?.message == "revoked" })

        // The failed task must not be reused: the next refresh tries again
        val retryProvider = GatedProvider().apply { gate.complete(Unit) }
        advanceUntilIdle()
        val refreshed = coordinator.refresh(session, retryProvider)
        assertEquals("new-jwt", refreshed.sessionJwt)
    }
}
