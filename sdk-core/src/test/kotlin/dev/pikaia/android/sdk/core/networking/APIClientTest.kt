package dev.pikaia.android.sdk.core.networking

import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.auth.RefreshCoordinator
import dev.pikaia.android.sdk.core.auth.TokenStore
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.Serializable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Serializable
private data class TestRequest(val value: String)

@Serializable
private data class TestResponse(val message: String)

private class FakeTokenStore(private var session: AuthSession? = null) : TokenStore {
    override suspend fun getSession(): AuthSession? = session
    override suspend fun setSession(session: AuthSession) {
        this.session = session
    }

    override suspend fun clear() {
        session = null
    }
}

private class FakeAuthProvider(
    private val result: () -> AuthSession
) : AuthProvider {
    var calls = 0
    override suspend fun refreshSession(current: AuthSession): AuthSession {
        calls++
        return result()
    }
}

/** Passes straight through to the provider; coalescing is tested in sdk-auth. */
private object PassthroughCoordinator : RefreshCoordinator {
    override suspend fun refresh(current: AuthSession, authProvider: AuthProvider): AuthSession =
        authProvider.refreshSession(current)
}

class APIClientTest {

    private val config = APIClientConfig(baseUrl = "https://api.example.com/api/v1")

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `builds request from endpoint and decodes response`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("""{"message":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val client = APIClient(config = config, engine = engine)

        val response = client.send(
            endpoint<TestRequest, TestResponse>(
                method = HTTPMethod.POST,
                path = "auth/magic-link/send",
                body = TestRequest("x")
            )
        )

        assertEquals("ok", response.message)
        val request = captured!!
        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.example.com/api/v1/auth/magic-link/send", request.url.toString())
        val body = request.body as TextContent
        assertEquals(ContentType.Application.Json, body.contentType)
        assertEquals("""{"value":"x"}""", body.text)
    }

    @Test
    fun `merges default and endpoint headers, ignoring content type`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("""{"message":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val client = APIClient(
            config = config.copy(
                defaultHeaders = mapOf(
                    "X-Client" to "test",
                    "Content-Type" to "application/json"
                )
            ),
            engine = engine
        )

        client.send(
            endpoint<TestResponse>(
                method = HTTPMethod.GET,
                path = "auth/me",
                headers = mapOf("X-Mobile-API-Key" to "key-123")
            )
        )

        val request = captured!!
        assertEquals("test", request.headers["X-Client"])
        assertEquals("key-123", request.headers["X-Mobile-API-Key"])
    }

    @Test
    fun `appends query parameters`() = runTest {
        var captured: HttpRequestData? = null
        val engine = MockEngine { request ->
            captured = request
            respond("""{"message":"ok"}""", HttpStatusCode.OK, jsonHeaders)
        }
        val client = APIClient(config = config, engine = engine)

        client.send(
            endpoint<TestResponse>(
                method = HTTPMethod.GET,
                path = "sync/pull",
                query = mapOf("since" to "cursor-1", "limit" to "50")
            )
        )

        assertEquals("cursor-1", captured!!.url.parameters["since"])
        assertEquals("50", captured!!.url.parameters["limit"])
    }

    @Test
    fun `returns EmptyResponse for empty 204 body`() = runTest {
        val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
        val client = APIClient(config = config, engine = engine)

        val response = client.send(
            endpoint<EmptyResponse>(method = HTTPMethod.DELETE, path = "devices/42")
        )

        assertEquals(EmptyResponse, response)
    }

    @Test
    fun `maps error status to Http with parsed detail and request id`() = runTest {
        val engine = MockEngine {
            respond(
                """{"detail":"Invalid or expired token."}""",
                HttpStatusCode.BadRequest,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    "X-Request-ID" to listOf("req-42")
                )
            )
        }
        val client = APIClient(config = config, engine = engine)

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull() as APIError.Http

        assertEquals(400, error.statusCode)
        assertEquals("Invalid or expired token.", error.detail)
        assertEquals("req-42", error.metadata?.requestId)
    }

    @Test
    fun `maps 429 to RateLimited with Retry-After`() = runTest {
        val engine = MockEngine {
            respond(
                """{"detail":"Too many requests"}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("30")
                )
            )
        }
        val client = APIClient(config = config, engine = engine)

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull() as APIError.RateLimited

        assertEquals(30.seconds, error.retryAfter)
        assertEquals("Too many requests", error.detail)
    }

    @Test
    fun `maps 429 without Retry-After to null duration`() = runTest {
        val engine = MockEngine {
            respond("""{"detail":"slow down"}""", HttpStatusCode.TooManyRequests, jsonHeaders)
        }
        val client = APIClient(config = config, engine = engine)

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull() as APIError.RateLimited

        assertNull(error.retryAfter)
    }

    @Test
    fun `maps malformed success body to Decoding`() = runTest {
        val engine = MockEngine { respond("not json", HttpStatusCode.OK, jsonHeaders) }
        val client = APIClient(config = config, engine = engine)

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull()

        assertTrue(error is APIError.Decoding)
    }

    @Test
    fun `maps engine failure to Transport`() = runTest {
        val engine = MockEngine { throw IOException("connection reset") }
        val client = APIClient(config = config, engine = engine)

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull()

        assertTrue(error is APIError.Transport)
    }

    // ------------------------------------------------------------------------
    // 401 → refresh → retry-once
    // ------------------------------------------------------------------------

    @Test
    fun `refreshes session on 401 and retries once`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            if (requests == 1) {
                respond("""{"detail":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
            } else {
                respond("""{"message":"ok"}""", HttpStatusCode.OK, jsonHeaders)
            }
        }
        val store = FakeTokenStore(AuthSession("old-jwt", "old-token", deviceUuid = "uuid-1"))
        val provider = FakeAuthProvider { AuthSession("new-jwt", "new-token", deviceUuid = "uuid-1") }
        val client = APIClient(
            config = config,
            tokenStore = store,
            authProvider = provider,
            refreshCoordinator = PassthroughCoordinator,
            engine = engine
        )

        val response = client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))

        assertEquals("ok", response.message)
        assertEquals(2, requests)
        assertEquals(1, provider.calls)
        assertEquals("new-jwt", store.getSession()?.sessionJwt)
    }

    @Test
    fun `does not retry more than once on repeated 401`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("""{"detail":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val store = FakeTokenStore(AuthSession("old-jwt", "old-token"))
        val provider = FakeAuthProvider { AuthSession("new-jwt", "new-token") }
        val client = APIClient(
            config = config,
            tokenStore = store,
            authProvider = provider,
            refreshCoordinator = PassthroughCoordinator,
            engine = engine
        )

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull() as APIError.Http

        assertEquals(401, error.statusCode)
        assertEquals(2, requests)
        assertEquals(1, provider.calls)
    }

    @Test
    fun `wraps refresh failure as RefreshFailed`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("""{"detail":"expired"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val store = FakeTokenStore(AuthSession("old-jwt", "old-token"))
        val provider = object : AuthProvider {
            override suspend fun refreshSession(current: AuthSession): AuthSession =
                throw IllegalStateException("session revoked")
        }
        val client = APIClient(
            config = config,
            tokenStore = store,
            authProvider = provider,
            refreshCoordinator = PassthroughCoordinator,
            engine = engine
        )

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull()

        assertTrue(error is APIError.RefreshFailed)
        assertEquals(1, requests)
    }

    @Test
    fun `401 without auth wiring surfaces as Http`() = runTest {
        var requests = 0
        val engine = MockEngine {
            requests++
            respond("""{"detail":"nope"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val client = APIClient(config = config, engine = engine)

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull() as APIError.Http

        assertEquals(401, error.statusCode)
        assertEquals("nope", error.detail)
        assertEquals(1, requests)
    }

    @Test
    fun `401 with no stored session does not call provider`() = runTest {
        val engine = MockEngine {
            respond("""{"detail":"nope"}""", HttpStatusCode.Unauthorized, jsonHeaders)
        }
        val store = FakeTokenStore(session = null)
        val provider = FakeAuthProvider { AuthSession("new-jwt", "new-token") }
        val client = APIClient(
            config = config,
            tokenStore = store,
            authProvider = provider,
            refreshCoordinator = PassthroughCoordinator,
            engine = engine
        )

        val error = runCatching {
            client.send(endpoint<TestResponse>(HTTPMethod.GET, "auth/me"))
        }.exceptionOrNull() as APIError.Http

        assertEquals(401, error.statusCode)
        assertEquals(0, provider.calls)
    }
}
