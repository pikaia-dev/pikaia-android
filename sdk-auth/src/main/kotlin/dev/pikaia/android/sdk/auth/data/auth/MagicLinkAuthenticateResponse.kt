package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from magic link authentication.
 *
 * Contains an interim session token and list of organizations the user belongs to.
 * The client must then select an organization and exchange the interim token for a full session.
 *
 * @param interimSessionToken Temporary token for organization selection
 * @param organizations List of organizations the user can access
 */
@Serializable
data class MagicLinkAuthenticateResponse(
    @SerialName("interim_session_token")
    val interimSessionToken: String,
    val organizations: List<OrganizationInfo>
)
