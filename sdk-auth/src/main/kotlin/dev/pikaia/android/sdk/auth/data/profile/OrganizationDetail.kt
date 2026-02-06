package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Organization details.
 *
 * @param organizationId Unique organization identifier
 * @param organizationName Organization display name
 */
@Serializable
data class OrganizationDetail(
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("organization_name")
    val organizationName: String
)
