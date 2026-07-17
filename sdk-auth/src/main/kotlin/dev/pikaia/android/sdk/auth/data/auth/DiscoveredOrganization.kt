package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An organization discovered during magic link authentication.
 *
 * @param organizationId Stytch organization ID
 * @param organizationName Organization display name
 * @param organizationSlug URL-safe organization identifier
 */
@Serializable
data class DiscoveredOrganization(
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("organization_name")
    val organizationName: String,
    @SerialName("organization_slug")
    val organizationSlug: String
)
