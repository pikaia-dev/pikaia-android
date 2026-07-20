package dev.pikaia.android.feature.devices.data.repository

import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.sdk.auth.api.DevicesAPI
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
import org.junit.Assert.assertTrue
import org.junit.Test

class DevicesRepositoryImplTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private val tokenStore = InMemoryTokenStore()

    private fun repository(body: String = "", status: HttpStatusCode = HttpStatusCode.OK): DevicesRepository {
        val engine = MockEngine { respond(body, status, jsonHeaders) }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        return DevicesRepositoryImpl(DevicesAPI(client), tokenStore)
    }

    @Test
    fun `loadDevices maps the SDK response`() = runTest {
        val devices = repository(
            body = """
                {
                  "devices": [
                    {"id": 7, "name": "Pixel 9 Pro", "platform": "android",
                     "os_version": "15", "app_version": "1.0.0", "created_at": "2026-07-01T09:30:00Z"}
                  ],
                  "count": 1
                }
            """
        ).loadDevices().first()

        assertEquals(
            listOf(
                Device(id = 7, name = "Pixel 9 Pro", platform = "android", createdAt = "2026-07-01T09:30:00Z")
            ),
            devices
        )
    }

    @Test
    fun `refreshSession persists new tokens and keeps the device uuid`() = runTest {
        tokenStore.setSession(AuthSession("old-jwt", "old-token", deviceUuid = "uuid-1"))

        repository(
            body = """
                {"session_token": "new-token", "session_jwt": "new-jwt",
                 "session_expires_at": "2027-07-17T12:00:00Z"}
            """
        ).refreshSession().first()

        val session = tokenStore.getSession()!!
        assertEquals("new-jwt", session.sessionJwt)
        assertEquals("new-token", session.sessionToken)
        assertEquals("uuid-1", session.deviceUuid)
    }

    @Test
    fun `refreshSession fails when the session is not device-linked`() = runTest {
        tokenStore.setSession(AuthSession("jwt", "token"))

        val error = runCatching { repository().refreshSession().first() }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }
}
