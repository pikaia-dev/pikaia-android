package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.auth.DiscoveryCreateOrgRequest
import dev.pikaia.android.sdk.auth.data.auth.DiscoveryExchangeRequest
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkAuthenticateRequest
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkSendRequest
import dev.pikaia.android.sdk.auth.data.profile.SendPhoneOtpRequest
import dev.pikaia.android.sdk.auth.data.profile.UpdateProfileRequest
import dev.pikaia.android.sdk.auth.data.profile.VerifyPhoneOtpRequest
import dev.pikaia.android.sdk.auth.data.session.MobileProvisionRequest
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.APIClientConfig
import dev.pikaia.android.sdk.core.networking.APIError
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

private const val USER_JSON = """
    {"id":1,"email":"jane@example.com","name":"Jane","avatar_url":"","phone_number":"+14155551234","sync_warning":null}
"""

private const val SESSION_JSON = """
    {"session_token":"st-1","session_jwt":"jwt-1","member_id":"member-x","organization_id":"org-x"}
"""

class AuthAPITest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private var captured: HttpRequestData? = null

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String): AuthAPI {
        val engine = MockEngine { request ->
            captured = request
            respond(body, status, jsonHeaders)
        }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        return AuthAPI(client)
    }

    private fun requestBodyJson() =
        Json.parseToJsonElement((captured!!.body as TextContent).text).jsonObject

    private fun assertRequest(method: HttpMethod, path: String) {
        assertEquals(method, captured!!.method)
        assertEquals("https://api.example.com/api/v1$path", captured!!.url.toString())
    }

    @Test
    fun `sendMagicLink posts email and decodes message`() = runTest {
        val result = api(body = """{"message":"Magic link sent"}""")
            .sendMagicLink(MagicLinkSendRequest(email = "jane@example.com"))

        assertRequest(HttpMethod.Post, "/auth/magic-link/send")
        assertEquals("jane@example.com", requestBodyJson()["email"]?.jsonPrimitive?.content)
        assertEquals("Magic link sent", result.getOrThrow().message)
    }

    @Test
    fun `authenticateMagicLink decodes intermediate session and organizations`() = runTest {
        val result = api(
            body = """
                {
                  "intermediate_session_token": "ist-1",
                  "email": "jane@example.com",
                  "discovered_organizations": [
                    {"organization_id": "org-x", "organization_name": "Acme", "organization_slug": "acme"}
                  ]
                }
            """
        ).authenticateMagicLink(MagicLinkAuthenticateRequest(token = "tok-1"))

        assertRequest(HttpMethod.Post, "/auth/magic-link/authenticate")
        assertEquals("tok-1", requestBodyJson()["token"]?.jsonPrimitive?.content)
        val response = result.getOrThrow()
        assertEquals("ist-1", response.intermediateSessionToken)
        assertEquals("jane@example.com", response.email)
        assertEquals("acme", response.discoveredOrganizations.single().organizationSlug)
    }

    @Test
    fun `createOrganization sends intermediate token with name and slug`() = runTest {
        val result = api(body = SESSION_JSON).createOrganization(
            DiscoveryCreateOrgRequest(
                intermediateSessionToken = "ist-1",
                organizationName = "Acme Corp",
                organizationSlug = "acme-corp"
            )
        )

        assertRequest(HttpMethod.Post, "/auth/discovery/create-org")
        val body = requestBodyJson()
        assertEquals("ist-1", body["intermediate_session_token"]?.jsonPrimitive?.content)
        assertEquals("Acme Corp", body["organization_name"]?.jsonPrimitive?.content)
        assertEquals("acme-corp", body["organization_slug"]?.jsonPrimitive?.content)
        assertEquals("jwt-1", result.getOrThrow().sessionJwt)
    }

    @Test
    fun `exchangeSession sends intermediate token and organization id`() = runTest {
        val result = api(body = SESSION_JSON).exchangeSession(
            DiscoveryExchangeRequest(intermediateSessionToken = "ist-1", organizationId = "org-x")
        )

        assertRequest(HttpMethod.Post, "/auth/discovery/exchange")
        val body = requestBodyJson()
        assertEquals("ist-1", body["intermediate_session_token"]?.jsonPrimitive?.content)
        assertEquals("org-x", body["organization_id"]?.jsonPrimitive?.content)
        assertEquals("st-1", result.getOrThrow().sessionToken)
    }

    @Test
    fun `provisionMobile sends api key header`() = runTest {
        val result = api(body = SESSION_JSON).provisionMobile(
            MobileProvisionRequest(email = "jane@example.com", organizationId = "org-x"),
            mobileApiKey = "mobile-key-1"
        )

        assertRequest(HttpMethod.Post, "/auth/mobile/provision")
        assertEquals("mobile-key-1", captured!!.headers["X-Mobile-API-Key"])
        val body = requestBodyJson()
        assertEquals("jane@example.com", body["email"]?.jsonPrimitive?.content)
        assertEquals("org-x", body["organization_id"]?.jsonPrimitive?.content)
        assertEquals("member-x", result.getOrThrow().memberId)
    }

    @Test
    fun `logout posts without body`() = runTest {
        val result = api(body = """{"message":"Logged out"}""").logout()

        assertRequest(HttpMethod.Post, "/auth/logout")
        assertTrue(captured!!.body !is TextContent)
        assertEquals("Logged out", result.getOrThrow().message)
    }

    @Test
    fun `getMe decodes user, member, and organization`() = runTest {
        val result = api(
            body = """
                {
                  "user": $USER_JSON,
                  "member": {"id": 2, "stytch_member_id": "member-x", "role": "admin", "is_admin": true},
                  "organization": {"id": 3, "stytch_org_id": "org-x", "name": "Acme", "slug": "acme", "logo_url": ""}
                }
            """
        ).getMe()

        assertRequest(HttpMethod.Get, "/auth/me")
        val me = result.getOrThrow()
        assertEquals(1L, me.user.id)
        assertEquals("Jane", me.user.name)
        assertTrue(me.member.isAdmin)
        assertEquals("member-x", me.member.stytchMemberId)
        assertEquals("acme", me.organization.slug)
    }

    @Test
    fun `updateProfile patches name`() = runTest {
        val result = api(body = USER_JSON).updateProfile(UpdateProfileRequest(name = "Jane D"))

        assertRequest(HttpMethod.Patch, "/auth/me/profile")
        assertEquals("Jane D", requestBodyJson()["name"]?.jsonPrimitive?.content)
        assertEquals("jane@example.com", result.getOrThrow().email)
    }

    @Test
    fun `sendPhoneOtp posts phone number`() = runTest {
        val result = api(body = """{"success":true,"message":"OTP sent"}""")
            .sendPhoneOtp(SendPhoneOtpRequest(phoneNumber = "+14155551234"))

        assertRequest(HttpMethod.Post, "/auth/phone/send-otp")
        assertEquals("+14155551234", requestBodyJson()["phone_number"]?.jsonPrimitive?.content)
        assertTrue(result.getOrThrow().success)
    }

    @Test
    fun `verifyPhoneOtp posts phone number and otp code`() = runTest {
        val result = api(body = USER_JSON).verifyPhoneOtp(
            VerifyPhoneOtpRequest(phoneNumber = "+14155551234", otpCode = "123456")
        )

        assertRequest(HttpMethod.Post, "/auth/phone/verify-otp")
        val body = requestBodyJson()
        assertEquals("+14155551234", body["phone_number"]?.jsonPrimitive?.content)
        assertEquals("123456", body["otp_code"]?.jsonPrimitive?.content)
        assertEquals("+14155551234", result.getOrThrow().phoneNumber)
    }

    @Test
    fun `error body surfaces as Http failure with detail`() = runTest {
        val result = api(status = HttpStatusCode.BadRequest, body = """{"detail":"Invalid magic link token"}""")
            .sendMagicLink(MagicLinkSendRequest(email = "jane@example.com"))

        val error = result.errorOrNull() as APIError.Http
        assertEquals(400, error.statusCode)
        assertEquals("Invalid magic link token", error.detail)
    }
}
