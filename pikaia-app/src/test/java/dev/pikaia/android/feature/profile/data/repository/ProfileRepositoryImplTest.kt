package dev.pikaia.android.feature.profile.data.repository

import dev.pikaia.android.feature.profile.data.Profile
import dev.pikaia.android.sdk.auth.api.AuthAPI
import dev.pikaia.android.sdk.auth.token.InMemoryTokenStore
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ProfileRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val tokenStore = InMemoryTokenStore()

    private var requests = 0

    private fun repository(body: String = """{"message":"ok"}"""): ProfileRepository {
        val engine = MockEngine {
            requests++
            respond(body, HttpStatusCode.OK, jsonHeaders)
        }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        return ProfileRepositoryImpl(AuthAPI(client), tokenStore)
    }

    @Test
    fun `emits null without touching the network when logged out`() = runTest {
        val profile = repository().loadUserProfile().first()

        assertNull(profile)
        assertEquals(0, requests)
    }

    @Test
    fun `maps the me response into the app profile`() = runTest {
        tokenStore.setSession(AuthSession("jwt-1", "st-1"))

        val profile = repository(
            body = """
                {
                  "user": {"id": 1, "email": "jane@example.com", "name": "Jane",
                           "phone_number": "+14155551234"},
                  "member": {"id": 2, "stytch_member_id": "member-x", "role": "member", "is_admin": false},
                  "organization": {"id": 3, "stytch_org_id": "org-x", "name": "Acme", "slug": "acme"}
                }
            """
        ).loadUserProfile().first()

        assertEquals(
            Profile(name = "Jane", phone = "+14155551234", email = "jane@example.com"),
            profile
        )
    }

    @Test
    fun `logout clears the stored session`() = runTest {
        tokenStore.setSession(AuthSession("jwt-1", "st-1"))

        repository(body = """{"message":"Logged out"}""").logout().first()

        assertNull(tokenStore.getSession())
    }
}
