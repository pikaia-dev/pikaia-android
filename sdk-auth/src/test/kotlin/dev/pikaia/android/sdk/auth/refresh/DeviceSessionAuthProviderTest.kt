package dev.pikaia.android.sdk.auth.refresh

import dev.pikaia.android.sdk.auth.api.DevicesAPI
import dev.pikaia.android.sdk.core.auth.AuthSession
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Instant

class DeviceSessionAuthProviderTest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `refreshes via the devices endpoint and preserves the device uuid`() = runTest {
        var capturedBody: String? = null
        val engine = MockEngine { request ->
            assertEquals(
                "https://api.example.com/api/v1/devices/session/refresh",
                request.url.toString()
            )
            capturedBody = (request.body as TextContent).text
            respond(
                """
                    {
                      "session_token": "st-2",
                      "session_jwt": "jwt-2",
                      "session_expires_at": "2027-07-17T12:00:00Z"
                    }
                """,
                HttpStatusCode.OK,
                jsonHeaders
            )
        }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        val provider = DeviceSessionAuthProvider(DevicesAPI(client))

        val refreshed = provider.refreshSession(
            AuthSession("old-jwt", "old-token", deviceUuid = "uuid-1")
        )

        val body = Json.parseToJsonElement(capturedBody!!).jsonObject
        assertEquals("uuid-1", body["device_uuid"]?.jsonPrimitive?.content)
        assertEquals("jwt-2", refreshed.sessionJwt)
        assertEquals("st-2", refreshed.sessionToken)
        assertEquals("uuid-1", refreshed.deviceUuid)
        assertEquals(Instant.parse("2027-07-17T12:00:00Z"), refreshed.sessionExpiresAt)
    }

    @Test
    fun `fails when the stored session has no device uuid`() = runTest {
        val engine = MockEngine { error("must not be called") }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        val provider = DeviceSessionAuthProvider(DevicesAPI(client))

        val error = runCatching {
            provider.refreshSession(AuthSession("jwt", "token", deviceUuid = null))
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
    }
}
