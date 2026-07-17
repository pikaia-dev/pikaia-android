package dev.pikaia.android.sdk.auth.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A full authenticated session.
 *
 * @param sessionToken Opaque Stytch session token for server-side use
 * @param sessionJwt Session JWT used as the bearer credential on API calls
 * @param memberId Stytch member ID within the organization
 * @param organizationId Stytch organization ID
 */
@Serializable
data class SessionResponse(
    @SerialName("session_token")
    val sessionToken: String,
    @SerialName("session_jwt")
    val sessionJwt: String,
    @SerialName("member_id")
    val memberId: String,
    @SerialName("organization_id")
    val organizationId: String
)
