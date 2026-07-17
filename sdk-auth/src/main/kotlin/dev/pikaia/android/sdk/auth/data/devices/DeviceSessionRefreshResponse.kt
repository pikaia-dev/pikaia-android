package dev.pikaia.android.sdk.auth.data.devices

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Response with refreshed device session tokens.
 *
 * @param sessionToken New opaque Stytch session token
 * @param sessionJwt New Stytch session JWT
 * @param sessionExpiresAt When the session expires
 */
@Serializable
data class DeviceSessionRefreshResponse(
    @SerialName("session_token")
    val sessionToken: String,
    @SerialName("session_jwt")
    val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("session_expires_at")
    val sessionExpiresAt: Instant
)
