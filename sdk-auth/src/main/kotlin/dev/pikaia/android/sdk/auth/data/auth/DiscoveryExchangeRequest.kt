package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to exchange interim session for a full session with selected organization.
 *
 * @param interimSessionToken Interim token from magic link authentication
 * @param organizationId ID of the organization to access
 */
@Serializable
data class DiscoveryExchangeRequest(
    @SerialName("interim_session_token")
    val interimSessionToken: String,
    @SerialName("organization_id")
    val organizationId: String
)
