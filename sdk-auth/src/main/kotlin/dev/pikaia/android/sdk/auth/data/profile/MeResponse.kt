package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.Serializable

/**
 * Response from the /me endpoint containing full user context.
 *
 * @param user User information
 * @param member Member information in the current organization
 * @param organization Current organization details
 */
@Serializable
data class MeResponse(
    val user: UserInfo,
    val member: MemberInfo,
    val organization: OrganizationDetail
)
