package dev.pikaia.android.sdk.auth.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to refresh an existing session.
 *
 * @param refreshToken The refresh token to use
 */
@Serializable
data class SessionRefreshRequest(
    @SerialName("refresh_token")
    val refreshToken: String
)
