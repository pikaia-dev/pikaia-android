package dev.pikaia.android.sdk.auth.data.session

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from session refresh.
 *
 * @param sessionToken New session token
 * @param sessionJwt New JWT
 * @param expiresAt When the new session expires
 */
@Serializable
data class SessionRefreshResponse(
    @SerialName("session_token")
    val sessionToken: String,
    @SerialName("session_jwt")
    val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at")
    val expiresAt: Instant
)
