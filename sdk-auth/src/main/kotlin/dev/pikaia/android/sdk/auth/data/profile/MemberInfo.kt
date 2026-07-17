package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Member information within an organization.
 *
 * @param memberId Unique member identifier
 * @param role Member's role (e.g., "admin", "member")
 * @param status Member's status (e.g., "active", "inactive")
 */
@Serializable
data class MemberInfo(
    @SerialName("member_id")
    val memberId: String,
    val role: String,
    val status: String
)
