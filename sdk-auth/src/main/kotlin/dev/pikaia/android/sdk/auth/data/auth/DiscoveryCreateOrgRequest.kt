package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to create a new organization during discovery flow.
 *
 * @param interimSessionToken Interim token from magic link authentication
 * @param organizationName Name for the new organization
 */
@Serializable
data class DiscoveryCreateOrgRequest(
    @SerialName("interim_session_token")
    val interimSessionToken: String,
    @SerialName("organization_name")
    val organizationName: String
)
