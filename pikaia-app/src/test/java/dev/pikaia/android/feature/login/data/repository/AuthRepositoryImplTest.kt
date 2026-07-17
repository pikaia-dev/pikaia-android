package dev.pikaia.android.feature.login.data.repository

import dev.pikaia.android.feature.login.data.LoginOrganization
import dev.pikaia.android.sdk.auth.api.AuthAPI
import dev.pikaia.android.sdk.auth.token.InMemoryTokenStore
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import dev.pikaia.android.sdk.core.networking.APIError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val tokenStore = InMemoryTokenStore()

    private fun repository(status: HttpStatusCode = HttpStatusCode.OK, body: String): AuthRepository {
        val engine = MockEngine { respond(body, status, jsonHeaders) }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        return AuthRepositoryImpl(AuthAPI(client), tokenStore)
    }

    @Test
    fun `authenticateMagicLink maps discovered organizations`() = runTest {
        val result = repository(
            body = """
                {
                  "intermediate_session_token": "ist-1",
                  "email": "jane@example.com",
                  "discovered_organizations": [
                    {"organization_id": "org-1", "organization_name": "Acme", "organization_slug": "acme"}
                  ]
                }
            """
        ).authenticateMagicLink("token-1").first()

        assertEquals("ist-1", result.intermediateSessionToken)
        assertEquals(
            listOf(LoginOrganization(id = "org-1", name = "Acme", slug = "acme")),
            result.organizations
        )
    }

    @Test
    fun `enterOrganization persists the session`() = runTest {
        repository(
            body = """
                {"session_token": "st-1", "session_jwt": "jwt-1",
                 "member_id": "member-x", "organization_id": "org-1"}
            """
        ).enterOrganization("ist-1", "org-1").first()

        val session = tokenStore.getSession()!!
        assertEquals("jwt-1", session.sessionJwt)
        assertEquals("st-1", session.sessionToken)
        assertNull(session.deviceUuid)
    }

    @Test
    fun `backend errors surface as APIError`() = runTest {
        val error = runCatching {
            repository(
                status = HttpStatusCode.BadRequest,
                body = """{"detail": "Invalid magic link token"}"""
            ).authenticateMagicLink("bad").first()
        }.exceptionOrNull()

        assertTrue(error is APIError.Http)
        assertEquals("Invalid magic link token", (error as APIError.Http).detail)
        assertNull(tokenStore.getSession())
    }
}
