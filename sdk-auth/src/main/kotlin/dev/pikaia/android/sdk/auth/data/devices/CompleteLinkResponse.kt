package dev.pikaia.android.sdk.auth.data.devices

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Response after successful device linking.
 *
 * @param sessionToken Opaque Stytch session token (identifies the long-lived session)
 * @param sessionJwt Stytch session JWT for API calls (short-lived, refreshable)
 * @param sessionExpiresAt When the session fully expires (requires a new QR scan)
 * @param deviceId Server-side device ID
 * @param userId Local database user ID
 * @param memberId Stytch member ID
 * @param organizationId Stytch organization ID
 */
@Serializable
data class CompleteLinkResponse(
    @SerialName("session_token")
    val sessionToken: String,
    @SerialName("session_jwt")
    val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("session_expires_at")
    val sessionExpiresAt: Instant,
    @SerialName("device_id")
    val deviceId: Long,
    @SerialName("user_id")
    val userId: Long,
    @SerialName("member_id")
    val memberId: String,
    @SerialName("organization_id")
    val organizationId: String
)
