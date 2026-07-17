package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to exchange an intermediate session token for a full session in the
 * selected organization.
 *
 * @param intermediateSessionToken Token from magic link authentication
 * @param organizationId Stytch ID of the organization to enter
 */
@Serializable
data class DiscoveryExchangeRequest(
    @SerialName("intermediate_session_token")
    val intermediateSessionToken: String,
    @SerialName("organization_id")
    val organizationId: String
)
