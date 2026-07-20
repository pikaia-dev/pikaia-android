package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyAuthenticationOptionsRequest
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyAuthenticationOptionsResponse
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyAuthenticationVerifyRequest
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyAuthenticationVerifyResponse
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyDeleteResponse
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyListResponse
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyRegistrationOptionsResponse
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyRegistrationVerifyRequest
import dev.pikaia.android.sdk.auth.data.passkeys.PasskeyRegistrationVerifyResponse
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.ApiResult
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint

/**
 * Passkeys (WebAuthn) API client.
 *
 * REST client only: the WebAuthn ceremony itself — invoking the platform credential API
 * with the returned `options` and collecting the `credential` response — belongs to the
 * consuming app (Credential Manager on Android). The SDK passes both payloads through
 * verbatim as JSON.
 *
 * Registration requires authentication; authentication endpoints are public and
 * rate-limited by the backend. Paths are relative — the [APIClient]'s `baseUrl` must
 * carry the backend's API prefix (e.g. `https://api.example.com/api/v1`).
 *
 * @param client The underlying API client for making HTTP requests
 */
class PasskeysAPI(private val client: APIClient) {

    /**
     * Get WebAuthn options for registering a new passkey.
     *
     * Requires authentication.
     *
     * @return Challenge ID and creation options for the platform credential API
     */
    suspend fun registerOptions(): ApiResult<PasskeyRegistrationOptionsResponse> {
        val endpoint = endpoint<PasskeyRegistrationOptionsResponse>(
            method = HTTPMethod.POST,
            path = "auth/passkeys/register/options"
        )
        return client.sendResult(endpoint)
    }

    /**
     * Verify the registration response and store the new passkey.
     *
     * Requires authentication.
     *
     * @param request Challenge ID, the platform credential response, and a passkey name
     * @return The stored passkey
     */
    suspend fun registerVerify(
        request: PasskeyRegistrationVerifyRequest
    ): ApiResult<PasskeyRegistrationVerifyResponse> {
        val endpoint = endpoint<PasskeyRegistrationVerifyRequest, PasskeyRegistrationVerifyResponse>(
            method = HTTPMethod.POST,
            path = "auth/passkeys/register/verify",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * Get WebAuthn options for authenticating with a passkey.
     *
     * Public endpoint (rate-limited by the backend).
     *
     * @param request Optional email filter for allowed credentials
     * @return Challenge ID and request options for the platform credential API
     */
    suspend fun authenticateOptions(
        request: PasskeyAuthenticationOptionsRequest = PasskeyAuthenticationOptionsRequest()
    ): ApiResult<PasskeyAuthenticationOptionsResponse> {
        val endpoint = endpoint<PasskeyAuthenticationOptionsRequest, PasskeyAuthenticationOptionsResponse>(
            method = HTTPMethod.POST,
            path = "auth/passkeys/authenticate/options",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * Verify the authentication response and create a full session.
     *
     * Public endpoint (rate-limited by the backend).
     *
     * @param request Challenge ID, the platform credential response, and optional organization
     * @return Full session credentials
     */
    suspend fun authenticateVerify(
        request: PasskeyAuthenticationVerifyRequest
    ): ApiResult<PasskeyAuthenticationVerifyResponse> {
        val endpoint = endpoint<PasskeyAuthenticationVerifyRequest, PasskeyAuthenticationVerifyResponse>(
            method = HTTPMethod.POST,
            path = "auth/passkeys/authenticate/verify",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * List the authenticated user's registered passkeys.
     *
     * @return Registered passkeys, newest first
     */
    suspend fun listPasskeys(): ApiResult<PasskeyListResponse> {
        val endpoint = endpoint<PasskeyListResponse>(
            method = HTTPMethod.GET,
            path = "auth/passkeys/"
        )
        return client.sendResult(endpoint)
    }

    /**
     * Delete a passkey by ID.
     *
     * The backend only deletes passkeys owned by the authenticated user.
     *
     * @param passkeyId Passkey ID from [listPasskeys]
     * @return Deletion confirmation
     */
    suspend fun deletePasskey(passkeyId: Long): ApiResult<PasskeyDeleteResponse> {
        val endpoint = endpoint<PasskeyDeleteResponse>(
            method = HTTPMethod.DELETE,
            path = "auth/passkeys/$passkeyId"
        )
        return client.sendResult(endpoint)
    }
}
