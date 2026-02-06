package dev.pikaia.android.sdk.auth.data.session

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Session response containing authentication tokens and user context.
 *
 * Returned by various authentication endpoints after successful authentication.
 *
 * @param sessionToken Session token for API authentication
 * @param sessionJwt JWT representation of the session
 * @param expiresAt When the session expires
 * @param userId User's unique identifier
 * @param memberId User's member ID in the organization
 * @param organizationId Organization ID the session is scoped to
 */
@Serializable
data class SessionResponse(
    @SerialName("session_token")
    val sessionToken: String,
    @SerialName("session_jwt")
    val sessionJwt: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at")
    val expiresAt: Instant,
    @SerialName("user_id")
    val userId: Int,
    @SerialName("member_id")
    val memberId: String,
    @SerialName("organization_id")
    val organizationId: String
)
