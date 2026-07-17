package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from magic link authentication.
 *
 * Carries an intermediate session token plus the organizations the user can enter.
 * The client must select (or create) an organization and exchange the token for a
 * full session.
 *
 * @param intermediateSessionToken Token to use with the discovery endpoints
 * @param email Authenticated user's email address
 * @param discoveredOrganizations Organizations available to this user
 */
@Serializable
data class MagicLinkAuthenticateResponse(
    @SerialName("intermediate_session_token")
    val intermediateSessionToken: String,
    val email: String,
    @SerialName("discovered_organizations")
    val discoveredOrganizations: List<DiscoveredOrganization>
)
