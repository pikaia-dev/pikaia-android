package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.Serializable

/**
 * Request to update user profile.
 *
 * @param name New display name for the user
 */
@Serializable
data class UpdateProfileRequest(
    val name: String
)
