package dev.pikaia.android.sdk.auth.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to provision a mobile app session.
 *
 * Simpler than device provisioning, creates a full session immediately.
 * Requires a mobile API key.
 *
 * @param email User's email address
 * @param name Optional user name
 * @param phoneNumber Optional phone number
 * @param organizationId Optional organization ID to join
 */
@Serializable
data class MobileProvisionRequest(
    val email: String,
    val name: String? = null,
    @SerialName("phone_number")
    val phoneNumber: String? = null,
    @SerialName("organization_id")
    val organizationId: String? = null
)
