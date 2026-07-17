package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Cross-organization user identity.
 *
 * @param id Local database user ID
 * @param email User's email address
 * @param name User's display name
 * @param avatarUrl URL to the user's avatar image
 * @param phoneNumber Phone number in E.164 format ("" when not set)
 * @param syncWarning Server-reported sync warning, if any
 */
@Serializable
data class UserInfo(
    val id: Long,
    val email: String,
    val name: String,
    @SerialName("avatar_url")
    val avatarUrl: String = "",
    @SerialName("phone_number")
    val phoneNumber: String = "",
    @SerialName("sync_warning")
    val syncWarning: String? = null
)
