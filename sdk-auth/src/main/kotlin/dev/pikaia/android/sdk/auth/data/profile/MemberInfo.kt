package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Organization-scoped membership details.
 *
 * @param id Local database member ID
 * @param stytchMemberId Stytch member ID
 * @param role Member role (e.g. "admin" or "member")
 * @param isAdmin Whether the member has admin privileges
 */
@Serializable
data class MemberInfo(
    val id: Long,
    @SerialName("stytch_member_id")
    val stytchMemberId: String,
    val role: String,
    @SerialName("is_admin")
    val isAdmin: Boolean
)
