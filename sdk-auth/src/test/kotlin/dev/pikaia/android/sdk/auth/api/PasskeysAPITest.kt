package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyAuthenticationOptionsRequest
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyAuthenticationVerifyRequest
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyRegistrationVerifyRequest
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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PasskeysAPITest {

    private val jsonHeaders = headersOf(HttpHeaders.ContentType, "application/json")

    private var captured: HttpRequestData? = null

    private fun api(status: HttpStatusCode = HttpStatusCode.OK, body: String = ""): PasskeysAPI {
        val engine = MockEngine { request ->
            captured = request
            respond(body, status, jsonHeaders)
        }
        val client = APIClient(
            config = APIClientConfig(baseUrl = "https://api.example.com/api/v1"),
            engine = engine
        )
        return PasskeysAPI(client)
    }

    private fun requestBodyJson() =
        Json.parseToJsonElement((captured!!.body as TextContent).text).jsonObject

    @Test
    fun `registerOptions posts without body and passes options through verbatim`() = runTest {
        val result = api(
            body = """
                {
                  "challenge_id": "chal-1",
                  "options": {"rp": {"id": "example.com"}, "challenge": "abc", "timeout": 60000}
                }
            """
        ).registerOptions()

        assertEquals(HttpMethod.Post, captured!!.method)
        assertEquals(
            "https://api.example.com/api/v1/auth/passkeys/register/options",
            captured!!.url.toString()
        )
        assertTrue(captured!!.body !is TextContent)
        val response = result.getOrThrow()
        assertEquals("chal-1", response.challengeId)
        assertEquals("example.com", response.options["rp"]!!.jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `registerVerify sends challenge, verbatim credential, and name`() = runTest {
        val credential = buildJsonObject {
            put("id", "cred-id")
            put("type", "public-key")
        }
        val result = api(
            body = """{"id": 5, "name": "Pixel 9 Pro", "created_at": "2026-07-17T12:00:00+00:00"}"""
        ).registerVerify(
            PasskeyRegistrationVerifyRequest(
                challengeId = "chal-1",
                credential = credential,
                name = "Pixel 9 Pro"
            )
        )

        assertEquals(
            "https://api.example.com/api/v1/auth/passkeys/register/verify",
            captured!!.url.toString()
        )
        val body = requestBodyJson()
        assertEquals("chal-1", body["challenge_id"]?.jsonPrimitive?.content)
        assertEquals(credential, body["credential"]?.jsonObject)
        assertEquals("Pixel 9 Pro", body["name"]?.jsonPrimitive?.content)

        val response = result.getOrThrow()
        assertEquals(5L, response.id)
        assertEquals("Pixel 9 Pro", response.name)
    }

    @Test
    fun `authenticateOptions sends optional email filter`() = runTest {
        val result = api(
            body = """{"challenge_id": "chal-2", "options": {"challenge": "xyz"}}"""
        ).authenticateOptions(PasskeyAuthenticationOptionsRequest(email = "jane@example.com"))

        assertEquals(
            "https://api.example.com/api/v1/auth/passkeys/authenticate/options",
            captured!!.url.toString()
        )
        assertEquals("jane@example.com", requestBodyJson()["email"]?.jsonPrimitive?.content)
        assertEquals("chal-2", result.getOrThrow().challengeId)
    }

    @Test
    fun `authenticateVerify returns a full session`() = runTest {
        val credential = buildJsonObject { put("id", "cred-id") }
        val result = api(
            body = """
                {
                  "session_token": "st-1",
                  "session_jwt": "jwt-1",
                  "member_id": "member-x",
                  "organization_id": "org-x",
                  "user_id": 1
                }
            """
        ).authenticateVerify(
            PasskeyAuthenticationVerifyRequest(
                challengeId = "chal-2",
                credential = credential,
                organizationId = "org-x"
            )
        )

        assertEquals(
            "https://api.example.com/api/v1/auth/passkeys/authenticate/verify",
            captured!!.url.toString()
        )
        val body = requestBodyJson()
        assertEquals("chal-2", body["challenge_id"]?.jsonPrimitive?.content)
        assertEquals("org-x", body["organization_id"]?.jsonPrimitive?.content)

        val session = result.getOrThrow()
        assertEquals("jwt-1", session.sessionJwt)
        assertEquals("member-x", session.memberId)
        assertEquals(1L, session.userId)
    }

    @Test
    fun `listPasskeys calls the collection path with trailing slash`() = runTest {
        val result = api(
            body = """
                {
                  "passkeys": [
                    {"id": 5, "name": "Pixel 9 Pro", "created_at": "2026-07-01T09:30:00+00:00",
                     "last_used_at": null, "backup_eligible": true, "backup_state": false}
                  ]
                }
            """
        ).listPasskeys()

        assertEquals(HttpMethod.Get, captured!!.method)
        assertEquals("https://api.example.com/api/v1/auth/passkeys/", captured!!.url.toString())
        val passkey = result.getOrThrow().passkeys.single()
        assertEquals(5L, passkey.id)
        assertNull(passkey.lastUsedAt)
        assertTrue(passkey.backupEligible)
    }

    @Test
    fun `deletePasskey deletes by id and decodes confirmation`() = runTest {
        val result = api(
            body = """{"success": true, "message": "Passkey deleted successfully"}"""
        ).deletePasskey(5)

        assertEquals(HttpMethod.Delete, captured!!.method)
        assertEquals("https://api.example.com/api/v1/auth/passkeys/5", captured!!.url.toString())
        assertTrue(result.getOrThrow().success)
    }

    @Test
    fun `failed authentication surfaces as Http 401 with detail`() = runTest {
        val result = api(status = HttpStatusCode.Unauthorized, body = """{"detail":"Invalid credential"}""")
            .authenticateVerify(
                PasskeyAuthenticationVerifyRequest(
                    challengeId = "chal-2",
                    credential = buildJsonObject { put("id", "cred-id") }
                )
            )

        val error = result.errorOrNull() as APIError.Http
        assertEquals(401, error.statusCode)
        assertEquals("Invalid credential", error.detail)
    }
}
