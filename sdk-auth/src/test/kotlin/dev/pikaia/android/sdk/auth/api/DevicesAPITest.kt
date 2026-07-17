package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.devices.CompleteLinkRequest
import dev.pikaia.android.sdk.auth.data.devices.DeviceSessionRefreshRequest
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import dev.pikaia.android.sdk.core.networking.APIError
import dev.pikaia.android.sdk.core.networking.ApiResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
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
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class DevicesAPITest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private var captured: HttpRequestData? = null

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String = ""): DevicesAPI {
        val engine = MockEngine { request ->
            captured = request
            respond(body, status, jsonHeaders)
        }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        return DevicesAPI(client)
    }

    private fun requestBodyJson() =
        Json.parseToJsonElement((captured!!.body as TextContent).text).jsonObject

    @Test
    fun `initiateLink posts and decodes qr payload`() = runTest {
        val result = api(
            body = """
                {
                  "qr_url": "app://device/link?token=abc",
                  "expires_at": "2026-07-17T12:00:00Z",
                  "expires_in_seconds": 300
                }
            """
        ).initiateLink()

        assertEquals(HttpMethod.Post, captured!!.method)
        assertEquals(
            "https://api.example.com/api/v1/devices/link/initiate",
            captured!!.url.toString()
        )
        val response = result.getOrThrow()
        assertEquals("app://device/link?token=abc", response.qrUrl)
        assertEquals(Instant.parse("2026-07-17T12:00:00Z"), response.expiresAt)
        assertEquals(300, response.expiresInSeconds)
    }

    @Test
    fun `completeLink sends device identity and decodes session`() = runTest {
        val result = api(
            body = """
                {
                  "session_token": "st-1",
                  "session_jwt": "jwt-1",
                  "session_expires_at": "2027-07-17T12:00:00Z",
                  "device_id": 7,
                  "user_id": 1,
                  "member_id": "member-x",
                  "organization_id": "org-x"
                }
            """
        ).completeLink(
            CompleteLinkRequest(
                token = "link-token",
                deviceUuid = "uuid-1",
                name = "Pixel 9 Pro",
                platform = "android",
                osVersion = "15",
                appVersion = "1.0.0"
            )
        )

        assertEquals(
            "https://api.example.com/api/v1/devices/link/complete",
            captured!!.url.toString()
        )
        val body = requestBodyJson()
        assertEquals("link-token", body["token"]?.jsonPrimitive?.content)
        assertEquals("uuid-1", body["device_uuid"]?.jsonPrimitive?.content)
        assertEquals("Pixel 9 Pro", body["name"]?.jsonPrimitive?.content)
        assertEquals("android", body["platform"]?.jsonPrimitive?.content)
        assertEquals("15", body["os_version"]?.jsonPrimitive?.content)
        assertEquals("1.0.0", body["app_version"]?.jsonPrimitive?.content)

        val response = result.getOrThrow()
        assertEquals("jwt-1", response.sessionJwt)
        assertEquals(7L, response.deviceId)
        assertEquals("member-x", response.memberId)
        assertEquals(Instant.parse("2027-07-17T12:00:00Z"), response.sessionExpiresAt)
    }

    @Test
    fun `listDevices calls the collection path with trailing slash`() = runTest {
        val result = api(
            body = """
                {
                  "devices": [
                    {"id": 7, "name": "Pixel 9 Pro", "platform": "android",
                     "os_version": "15", "app_version": "1.0.0", "created_at": "2026-07-01T09:30:00Z"}
                  ],
                  "count": 1
                }
            """
        ).listDevices()

        assertEquals(HttpMethod.Get, captured!!.method)
        assertEquals("https://api.example.com/api/v1/devices/", captured!!.url.toString())
        val response = result.getOrThrow()
        assertEquals(1, response.count)
        assertEquals("Pixel 9 Pro", response.devices.single().name)
        assertEquals(Instant.parse("2026-07-01T09:30:00Z"), response.devices.single().createdAt)
    }

    @Test
    fun `revokeDevice deletes by id and maps 204 to Unit`() = runTest {
        val result = api(status = HttpStatusCode.NoContent).revokeDevice(42)

        assertEquals(HttpMethod.Delete, captured!!.method)
        assertEquals("https://api.example.com/api/v1/devices/42", captured!!.url.toString())
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun `refreshSession posts device uuid and decodes new tokens`() = runTest {
        val result = api(
            body = """
                {
                  "session_token": "st-2",
                  "session_jwt": "jwt-2",
                  "session_expires_at": "2027-07-17T12:00:00Z"
                }
            """
        ).refreshSession(DeviceSessionRefreshRequest(deviceUuid = "uuid-1"))

        assertEquals(
            "https://api.example.com/api/v1/devices/session/refresh",
            captured!!.url.toString()
        )
        assertEquals("uuid-1", requestBodyJson()["device_uuid"]?.jsonPrimitive?.content)
        assertEquals("jwt-2", result.getOrThrow().sessionJwt)
    }

    @Test
    fun `rate limited link completion surfaces retry-after`() = runTest {
        val engine = MockEngine {
            respond(
                """{"detail":"Too many link attempts. Maximum 20 per hour."}""",
                HttpStatusCode.TooManyRequests,
                headersOf(
                    HttpHeaders.ContentType to listOf("application/json"),
                    HttpHeaders.RetryAfter to listOf("3600")
                )
            )
        }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )

        val result = DevicesAPI(client).completeLink(
            CompleteLinkRequest(token = "t", deviceUuid = "u", name = "n", platform = "android")
        )

        val error = result.errorOrNull() as APIError.RateLimited
        assertEquals(3600.seconds, error.retryAfter)
        assertTrue(error.detail!!.startsWith("Too many link attempts"))
    }

    @Test
    fun `device not found surfaces as Http 404 with detail`() = runTest {
        val result = api(status = HttpStatusCode.NotFound, body = """{"detail":"Device not found"}""")
            .revokeDevice(999)

        val error = result.errorOrNull() as APIError.Http
        assertEquals(404, error.statusCode)
        assertEquals("Device not found", error.detail)
    }
}
