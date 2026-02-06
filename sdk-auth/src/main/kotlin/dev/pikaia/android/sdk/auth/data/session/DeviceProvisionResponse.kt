package dev.pikaia.android.sdk.auth.data.session

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from device provisioning.
 *
 * @param deviceToken Token for device authentication
 * @param tokenExpiresAt When the device token expires
 * @param userId User ID created/assigned
 * @param memberId Member ID in the organization
 * @param organizationId Organization ID
 * @param isNewUser Whether this is a newly created user
 */
@Serializable
data class DeviceProvisionResponse(
    @SerialName("device_token")
    val deviceToken: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("token_expires_at")
    val tokenExpiresAt: Instant,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("member_id")
    val memberId: Int,
    @SerialName("organization_id")
    val organizationId: Int,
    @SerialName("is_new_user")
    val isNewUser: Boolean
)
