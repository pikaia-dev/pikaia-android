package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Organization information for discovery/selection.
 *
 * @param organizationId Unique identifier for the organization
 * @param organizationName Display name of the organization
 * @param role User's role in this organization
 */
@Serializable
data class OrganizationInfo(
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("organization_name")
    val organizationName: String,
    val role: String
)
