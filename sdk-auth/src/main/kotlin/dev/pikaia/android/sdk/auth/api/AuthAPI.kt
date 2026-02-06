package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.auth.*
import dev.pikaia.android.sdk.auth.data.common.MessageResponse
import dev.pikaia.android.sdk.auth.data.profile.*
import dev.pikaia.android.sdk.auth.data.session.*
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.EmptyBody
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Authentication API client providing all auth-related endpoints.
 *
 * This API handles:
 * - Magic link authentication flow
 * - Organization discovery and selection
 * - Device and mobile provisioning
 * - Session management
 * - Profile updates
 * - Phone verification
 *
 * Each endpoint provides two variants:
 * - Suspend function for direct await usage
 * - Flow-based function for reactive streams
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
    suspend fun sendMagicLink(request: MagicLinkSendRequest): MessageResponse {
        val endpoint = endpoint<MessageResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/magic-link/send",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Send a magic link to the specified email address (Flow variant).
     *
     * @param request Email address to send the link to
     * @return Flow emitting the message response
     */
    fun sendMagicLinkFlow(request: MagicLinkSendRequest): Flow<MessageResponse> = flow {
        emit(sendMagicLink(request))
    }

    /**
     * Authenticate using a magic link token.
     *
     * Returns an interim session token and list of organizations.
     * Client must then select an organization and exchange the token.
     *
     * @param request Magic link token from the email
     * @return Interim session and organization list
     */
    suspend fun authenticateMagicLink(
        request: MagicLinkAuthenticateRequest
    ): MagicLinkAuthenticateResponse {
        val endpoint = endpoint<MagicLinkAuthenticateResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/magic-link/authenticate",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Authenticate using a magic link token (Flow variant).
     *
     * @param request Magic link token from the email
     * @return Flow emitting the interim session and organization list
     */
    fun authenticateMagicLinkFlow(
        request: MagicLinkAuthenticateRequest
    ): Flow<MagicLinkAuthenticateResponse> = flow {
        emit(authenticateMagicLink(request))
    }

    // ========================================================================
    // Organization Discovery
    // ========================================================================

    /**
     * Create a new organization during the discovery flow.
     *
     * Uses the interim session token from magic link authentication.
     *
     * @param request Organization creation request with interim token
     * @return Full session for the new organization
     */
    suspend fun createOrganization(
        request: DiscoveryCreateOrgRequest
    ): SessionResponse {
        val endpoint = endpoint<SessionResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/discovery/create-org",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Create a new organization during the discovery flow (Flow variant).
     *
     * @param request Organization creation request with interim token
     * @return Flow emitting the full session
     */
    fun createOrganizationFlow(
        request: DiscoveryCreateOrgRequest
    ): Flow<SessionResponse> = flow {
        emit(createOrganization(request))
    }

    /**
     * Exchange interim session token for a full session with selected organization.
     *
     * @param request Exchange request with interim token and organization ID
     * @return Full session for the selected organization
     */
    suspend fun exchangeSession(
        request: DiscoveryExchangeRequest
    ): SessionResponse {
        val endpoint = endpoint<SessionResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/discovery/exchange",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Exchange interim session token for a full session (Flow variant).
     *
     * @param request Exchange request with interim token and organization ID
     * @return Flow emitting the full session
     */
    fun exchangeSessionFlow(
        request: DiscoveryExchangeRequest
    ): Flow<SessionResponse> = flow {
        emit(exchangeSession(request))
    }

    // ========================================================================
    // Device Provisioning
    // ========================================================================

    /**
     * Provision a device with a shadow user.
     *
     * Shadow users enable offline-first apps to sync before email verification.
     * Requires a mobile API key passed in the X-Mobile-API-Key header.
     *
     * @param request Device provisioning details
     * @param mobileAPIKey Mobile API key for authentication
     * @return Device token and user context
     */
    suspend fun provisionDevice(
        request: DeviceProvisionRequest,
        mobileAPIKey: String
    ): DeviceProvisionResponse {
        val endpoint = endpoint<DeviceProvisionResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/device/provision",
            headers = mapOf("X-Mobile-API-Key" to mobileAPIKey),
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Provision a device with a shadow user (Flow variant).
     *
     * @param request Device provisioning details
     * @param mobileAPIKey Mobile API key for authentication
     * @return Flow emitting the device token and user context
     */
    fun provisionDeviceFlow(
        request: DeviceProvisionRequest,
        mobileAPIKey: String
    ): Flow<DeviceProvisionResponse> = flow {
        emit(provisionDevice(request, mobileAPIKey))
    }

    // ========================================================================
    // Mobile Provisioning
    // ========================================================================

    /**
     * Provision a mobile app session.
     *
     * Simpler than device provisioning - creates a full session immediately.
     * Requires a mobile API key passed in the X-Mobile-API-Key header.
     *
     * @param request Mobile provisioning details
     * @param mobileAPIKey Mobile API key for authentication
     * @return Full session response
     */
    suspend fun provisionMobile(
        request: MobileProvisionRequest,
        mobileAPIKey: String
    ): SessionResponse {
        val endpoint = endpoint<SessionResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/mobile/provision",
            headers = mapOf("X-Mobile-API-Key" to mobileAPIKey),
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Provision a mobile app session (Flow variant).
     *
     * @param request Mobile provisioning details
     * @param mobileAPIKey Mobile API key for authentication
     * @return Flow emitting the full session response
     */
    fun provisionMobileFlow(
        request: MobileProvisionRequest,
        mobileAPIKey: String
    ): Flow<SessionResponse> = flow {
        emit(provisionMobile(request, mobileAPIKey))
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
    suspend fun logout(): MessageResponse {
        val endpoint = endpoint<MessageResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/logout",
            body = EmptyBody
        )
        return client.send(endpoint)
    }

    /**
     * End the current session (logout) (Flow variant).
     *
     * @return Flow emitting the logout confirmation message
     */
    fun logoutFlow(): Flow<MessageResponse> = flow {
        emit(logout())
    }

    /**
     * Get current user information and context.
     *
     * Returns user details, member info, and organization context.
     * Requires authentication.
     *
     * @return Current user context
     */
    suspend fun getMe(): MeResponse {
        val endpoint = endpoint<MeResponse>(
            method = HTTPMethod.GET,
            path = "/v1/auth/me"
        )
        return client.send(endpoint)
    }

    /**
     * Get current user information and context (Flow variant).
     *
     * @return Flow emitting the current user context
     */
    fun getMeFlow(): Flow<MeResponse> = flow {
        emit(getMe())
    }

    /**
     * Refresh an existing session using a refresh token.
     *
     * This is used by the AuthProvider implementation to handle token refresh.
     * Requires authentication.
     *
     * @param request Refresh token request
     * @return New session tokens
     */
    suspend fun refreshSession(
        request: SessionRefreshRequest
    ): SessionRefreshResponse {
        val endpoint = endpoint<SessionRefreshResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/session/refresh",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Refresh an existing session using a refresh token (Flow variant).
     *
     * @param request Refresh token request
     * @return Flow emitting the new session tokens
     */
    fun refreshSessionFlow(
        request: SessionRefreshRequest
    ): Flow<SessionRefreshResponse> = flow {
        emit(refreshSession(request))
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
    suspend fun updateProfile(request: UpdateProfileRequest): UserInfo {
        val endpoint = endpoint<UserInfo>(
            method = HTTPMethod.PATCH,
            path = "/v1/auth/me/profile",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Update the current user's profile (Flow variant).
     *
     * @param request Profile update details
     * @return Flow emitting the updated user information
     */
    fun updateProfileFlow(request: UpdateProfileRequest): Flow<UserInfo> = flow {
        emit(updateProfile(request))
    }

    // ========================================================================
    // Phone Verification
    // ========================================================================

    /**
     * Send an OTP code to a phone number.
     *
     * The phone number must be in E.164 format (e.g., "+14155551234").
     * Rate limited to 3 requests per hour per phone number.
     * Requires authentication.
     *
     * @param request Phone number to send OTP to
     * @return Method ID for OTP verification
     */
    suspend fun sendPhoneOtp(request: SendPhoneOtpRequest): PhoneOtpResponse {
        val endpoint = endpoint<PhoneOtpResponse>(
            method = HTTPMethod.POST,
            path = "/v1/auth/phone/send-otp",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Send an OTP code to a phone number (Flow variant).
     *
     * @param request Phone number to send OTP to
     * @return Flow emitting the method ID for OTP verification
     */
    fun sendPhoneOtpFlow(request: SendPhoneOtpRequest): Flow<PhoneOtpResponse> = flow {
        emit(sendPhoneOtp(request))
    }

    /**
     * Verify a phone OTP code.
     *
     * The OTP code is valid for 30 minutes.
     * Requires authentication.
     *
     * @param request Method ID and OTP code
     * @return Updated user information with verified phone
     */
    suspend fun verifyPhoneOtp(request: VerifyPhoneOtpRequest): UserInfo {
        val endpoint = endpoint<UserInfo>(
            method = HTTPMethod.POST,
            path = "/v1/auth/phone/verify-otp",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Verify a phone OTP code (Flow variant).
     *
     * @param request Method ID and OTP code
     * @return Flow emitting the updated user information with verified phone
     */
    fun verifyPhoneOtpFlow(request: VerifyPhoneOtpRequest): Flow<UserInfo> = flow {
        emit(verifyPhoneOtp(request))
    }
}
