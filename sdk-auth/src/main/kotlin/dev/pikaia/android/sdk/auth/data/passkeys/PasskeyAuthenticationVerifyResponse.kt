package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Full session created by successful passkey authentication.
 *
 * @param sessionToken Opaque Stytch session token
 * @param sessionJwt Stytch session JWT used as the bearer credential
 * @param memberId Stytch member ID
 * @param organizationId Stytch organization ID
 * @param userId Local database user ID
 */
@Serializable
data class PasskeyAuthenticationVerifyResponse(
    @SerialName("session_token")
    val sessionToken: String,
    @SerialName("session_jwt")
    val sessionJwt: String,
    @SerialName("member_id")
    val memberId: String,
    @SerialName("organization_id")
    val organizationId: String,
    @SerialName("user_id")
    val userId: Long
)
