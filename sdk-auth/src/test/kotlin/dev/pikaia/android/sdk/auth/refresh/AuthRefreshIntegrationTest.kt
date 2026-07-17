package dev.pikaia.android.sdk.auth.refresh

import dev.pikaia.android.sdk.auth.api.AuthAPI
import dev.pikaia.android.sdk.auth.api.DevicesAPI
import dev.pikaia.android.sdk.auth.interceptor.AuthTokenInterceptor
import dev.pikaia.android.sdk.auth.token.InMemoryTokenStore
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import dev.pikaia.android.sdk.core.networking.APIError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * End-to-end assembly of the documented consumption pattern: bearer injection from the
 * token store, 401 detection, coalesced refresh via the devices endpoint, persisted
 * session, and a single retry.
 */
class AuthRefreshIntegrationTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val meJson = """
        {
          "user": {"id":1,"email":"jane@example.com","name":"Jane"},
          "member": {"id":2,"stytch_member_id":"member-x","role":"member","is_admin":false},
          "organization": {"id":3,"stytch_org_id":"org-x","name":"Acme","slug":"acme"}
        }
    """

    private val refreshJson = """
        {
          "session_token": "new-token",
          "session_jwt": "new-jwt",
          "session_expires_at": "2027-07-17T12:00:00Z"
        }
    """

    private fun assembledAuthAPI(engine: MockEngine, store: InMemoryTokenStore): AuthAPI {
        val config = APIClientConfig(baseUrl = "https://api.example.com/api/v1")

        // Refresh path: same token store for bearer injection, but no auth provider —
        // a failing refresh must never trigger another refresh.
        val refreshClient = APIClient(
            config = config,
            requestInterceptors = listOf(AuthTokenInterceptor(store)),
            engine = engine
        )
        val authProvider = DeviceSessionAuthProvider(DevicesAPI(refreshClient))

        val apiClient = APIClient(
            config = config,
            requestInterceptors = listOf(AuthTokenInterceptor(store)),
            tokenStore = store,
            authProvider = authProvider,
            refreshCoordinator = DefaultRefreshCoordinator(),
            engine = engine
        )
        return AuthAPI(apiClient)
    }

    @Test
    fun `expired session refreshes through the devices endpoint and retries`() = runTest {
        val store = InMemoryTokenStore()
        store.setSession(AuthSession("expired-jwt", "old-token", deviceUuid = "uuid-1"))

        var refreshCalls = 0
        var meCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/devices/session/refresh") -> {
                    refreshCalls++
                    // The refresh call itself authenticates with the current (stale) JWT
                    assertEquals("Bearer expired-jwt", request.headers[HttpHeaders.Authorization])
                    respond(refreshJson, HttpStatusCode.OK, jsonHeaders)
                }
                else -> {
                    meCalls++
                    if (request.headers[HttpHeaders.Authorization] == "Bearer new-jwt") {
                        respond(meJson, HttpStatusCode.OK, jsonHeaders)
                    } else {
                        respond("""{"detail":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
                    }
                }
            }
        }

        val result = assembledAuthAPI(engine, store).getMe()

        assertEquals("Jane", result.getOrThrow().user.name)
        assertEquals(1, refreshCalls)
        assertEquals(2, meCalls)

        val persisted = store.getSession()!!
        assertEquals("new-jwt", persisted.sessionJwt)
        assertEquals("new-token", persisted.sessionToken)
        assertEquals("uuid-1", persisted.deviceUuid)
    }

    @Test
    fun `failed refresh surfaces RefreshFailed without retry loops`() = runTest {
        val store = InMemoryTokenStore()
        store.setSession(AuthSession("expired-jwt", "old-token", deviceUuid = "uuid-1"))

        var refreshCalls = 0
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/devices/session/refresh") -> {
                    refreshCalls++
                    respond("""{"detail":"Device not found"}""", HttpStatusCode.NotFound, jsonHeaders)
                }
                else -> respond("""{"detail":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            }
        }

        val result = assembledAuthAPI(engine, store).getMe()

        assertTrue(result.errorOrNull() is APIError.RefreshFailed)
        assertEquals(1, refreshCalls)
    }
}
