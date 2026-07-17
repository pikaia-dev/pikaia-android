package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Current organization context.
 *
 * @param id Local database organization ID
 * @param stytchOrgId Stytch organization ID
 * @param name Organization display name
 * @param slug URL-safe organization identifier
 * @param logoUrl URL to the organization logo image
 */
@Serializable
data class OrganizationInfo(
    val id: Long,
    @SerialName("stytch_org_id")
    val stytchOrgId: String,
    val name: String,
    val slug: String,
    @SerialName("logo_url")
    val logoUrl: String = ""
)
