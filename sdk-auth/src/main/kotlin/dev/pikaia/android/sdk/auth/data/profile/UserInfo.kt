package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * User information.
 *
 * @param userId Unique user identifier
 * @param email User's email address
 * @param name User's display name
 * @param phoneNumber User's phone number
 * @param phoneVerified Whether the phone number has been verified
 */
@Serializable
data class UserInfo(
    @SerialName("user_id")
    val userId: Int,
    val email: String?,
    val name: String?,
    @SerialName("phone_number")
    val phoneNumber: String?,
    @SerialName("phone_verified")
    val phoneVerified: Boolean
)
