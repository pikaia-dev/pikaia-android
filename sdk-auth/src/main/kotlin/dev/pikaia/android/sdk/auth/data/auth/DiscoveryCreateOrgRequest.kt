package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to create a new organization during the discovery flow.
 *
 * @param intermediateSessionToken Token from magic link authentication
 * @param organizationName Display name for the new organization
 * @param organizationSlug URL-safe identifier (lowercase, hyphens allowed)
 */
@Serializable
data class DiscoveryCreateOrgRequest(
    @SerialName("intermediate_session_token")
    val intermediateSessionToken: String,
    @SerialName("organization_name")
    val organizationName: String,
    @SerialName("organization_slug")
    val organizationSlug: String
)
