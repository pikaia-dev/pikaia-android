package dev.pikaia.android.sdk.auth.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request for mobile user provisioning (API key auth).
 *
 * Either provide [organizationId] to join an existing organization, or
 * [organizationName] + [organizationSlug] to create a new one.
 *
 * @param email User's email address
 * @param name User's display name (optional)
 * @param phoneNumber User's phone number in E.164 format (optional, stored unverified)
 * @param organizationId Stytch org ID to join (mutually exclusive with org creation fields)
 * @param organizationName Name for a new organization (requires [organizationSlug])
 * @param organizationSlug Slug for a new organization (requires [organizationName])
 */
@Serializable
data class MobileProvisionRequest(
    val email: String,
    val name: String = "",
    @SerialName("phone_number")
    val phoneNumber: String = "",
    @SerialName("organization_id")
    val organizationId: String? = null,
    @SerialName("organization_name")
    val organizationName: String? = null,
    @SerialName("organization_slug")
    val organizationSlug: String? = null
)
