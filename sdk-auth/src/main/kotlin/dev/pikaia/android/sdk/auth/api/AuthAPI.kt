package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.auth.DiscoveryCreateOrgRequest
import dev.pikaia.android.sdk.auth.data.auth.DiscoveryExchangeRequest
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkAuthenticateRequest
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkAuthenticateResponse
import dev.pikaia.android.sdk.auth.data.auth.MagicLinkSendRequest
import dev.pikaia.android.sdk.auth.data.common.MessageResponse
import dev.pikaia.android.sdk.auth.data.profile.MeResponse
import dev.pikaia.android.sdk.auth.data.profile.PhoneOtpResponse
import dev.pikaia.android.sdk.auth.data.profile.SendPhoneOtpRequest
import dev.pikaia.android.sdk.auth.data.profile.UpdateProfileRequest
import dev.pikaia.android.sdk.auth.data.profile.UserInfo
import dev.pikaia.android.sdk.auth.data.profile.VerifyPhoneOtpRequest
import dev.pikaia.android.sdk.auth.data.session.MobileProvisionRequest
import dev.pikaia.android.sdk.auth.data.session.SessionResponse
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.ApiResult
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint

/**
 * Authentication API client covering the backend's shared auth surface.
 *
 * This API handles:
 * - Magic link authentication flow
 * - Organization discovery and selection
 * - Mobile provisioning
 * - Session management (`/me`, logout)
 * - Profile updates
 * - Phone verification
 *
 * Paths are relative — the [APIClient]'s `baseUrl` must carry the backend's API prefix
 * (e.g. `https://api.example.com/api/v1`). Device linking and session refresh live in
 * `DevicesAPI`.
 *
 * @param client The underlying API client for making HTTP requests
 */
class AuthAPI(private val client: APIClient) {

    // ========================================================================
    // Magic Link Authentication
    // ========================================================================

    /**
     * Send a magic link to the specified email address.
     *
     * @param request Email address to send the link to
     * @return Message confirming the email was sent
     */
    suspend fun sendMagicLink(request: MagicLinkSendRequest): ApiResult<MessageResponse> {
        val endpoint = endpoint<MagicLinkSendRequest, MessageResponse>(
            method = HTTPMethod.POST,
            path = "auth/magic-link/send",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * Authenticate using a magic link token.
     *
     * Returns an intermediate session token and the list of discovered organizations.
     * The client must then select (or create) an organization and exchange the token.
     *
     * @param request Magic link token from the email
     * @return Intermediate session and discovered organizations
     */
    suspend fun authenticateMagicLink(
        request: MagicLinkAuthenticateRequest
    ): ApiResult<MagicLinkAuthenticateResponse> {
        val endpoint = endpoint<MagicLinkAuthenticateRequest, MagicLinkAuthenticateResponse>(
            method = HTTPMethod.POST,
            path = "auth/magic-link/authenticate",
            body = request
        )
        return client.sendResult(endpoint)
    }

    // ========================================================================
    // Organization Discovery
    // ========================================================================

    /**
     * Create a new organization during the discovery flow.
     *
     * Uses the intermediate session token from magic link authentication.
     *
     * @param request Organization creation request with intermediate token
     * @return Full session for the new organization
     */
    suspend fun createOrganization(
        request: DiscoveryCreateOrgRequest
    ): ApiResult<SessionResponse> {
        val endpoint = endpoint<DiscoveryCreateOrgRequest, SessionResponse>(
            method = HTTPMethod.POST,
            path = "auth/discovery/create-org",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * Exchange an intermediate session token for a full session with the selected
     * organization.
     *
     * @param request Exchange request with intermediate token and organization ID
     * @return Full session for the selected organization
     */
    suspend fun exchangeSession(
        request: DiscoveryExchangeRequest
    ): ApiResult<SessionResponse> {
        val endpoint = endpoint<DiscoveryExchangeRequest, SessionResponse>(
            method = HTTPMethod.POST,
            path = "auth/discovery/exchange",
            body = request
        )
        return client.sendResult(endpoint)
    }

    // ========================================================================
    // Mobile Provisioning
    // ========================================================================

    /**
     * Provision a mobile user and return a full session.
     *
     * Requires a mobile API key passed in the `X-Mobile-API-Key` header.
     *
     * @param request Mobile provisioning details
     * @param mobileApiKey Mobile API key for authentication
     * @return Full session response
     */
    suspend fun provisionMobile(
        request: MobileProvisionRequest,
        mobileApiKey: String
    ): ApiResult<SessionResponse> {
        val endpoint = endpoint<MobileProvisionRequest, SessionResponse>(
            method = HTTPMethod.POST,
            path = "auth/mobile/provision",
            headers = mapOf("X-Mobile-API-Key" to mobileApiKey),
            body = request
        )
        return client.sendResult(endpoint)
    }

    // ========================================================================
    // Session Management
    // ========================================================================

    /**
     * End the current session (logout).
     *
     * Requires authentication.
     *
     * @return Message confirming logout
     */
    suspend fun logout(): ApiResult<MessageResponse> {
        val endpoint = endpoint<MessageResponse>(
            method = HTTPMethod.POST,
            path = "auth/logout"
        )
        return client.sendResult(endpoint)
    }

    /**
     * Get current user information and context.
     *
     * Returns user details, member info, and organization context.
     * Requires authentication.
     *
     * @return Current user context
     */
    suspend fun getMe(): ApiResult<MeResponse> {
        val endpoint = endpoint<MeResponse>(
            method = HTTPMethod.GET,
            path = "auth/me"
        )
        return client.sendResult(endpoint)
    }

    // ========================================================================
    // Profile Management
    // ========================================================================

    /**
     * Update the current user's profile.
     *
     * Requires authentication.
     *
     * @param request Profile update details
     * @return Updated user information
     */
    suspend fun updateProfile(request: UpdateProfileRequest): ApiResult<UserInfo> {
        val endpoint = endpoint<UpdateProfileRequest, UserInfo>(
            method = HTTPMethod.PATCH,
            path = "auth/me/profile",
            body = request
        )
        return client.sendResult(endpoint)
    }

    // ========================================================================
    // Phone Verification
    // ========================================================================

    /**
     * Send an OTP code to a phone number.
     *
     * The phone number must be in E.164 format (e.g., "+14155551234").
     * Requires authentication.
     *
     * @param request Phone number to send the OTP to
     * @return Whether the OTP was sent
     */
    suspend fun sendPhoneOtp(request: SendPhoneOtpRequest): ApiResult<PhoneOtpResponse> {
        val endpoint = endpoint<SendPhoneOtpRequest, PhoneOtpResponse>(
            method = HTTPMethod.POST,
            path = "auth/phone/send-otp",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * Verify a phone OTP code.
     *
     * Requires authentication.
     *
     * @param request Phone number and OTP code
     * @return Updated user information with the verified phone
     */
    suspend fun verifyPhoneOtp(request: VerifyPhoneOtpRequest): ApiResult<UserInfo> {
        val endpoint = endpoint<VerifyPhoneOtpRequest, UserInfo>(
            method = HTTPMethod.POST,
            path = "auth/phone/verify-otp",
            body = request
        )
        return client.sendResult(endpoint)
    }
}
