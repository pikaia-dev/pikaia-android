package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.Serializable

/**
 * Current user context: identity, membership, and organization.
 *
 * @param user Cross-organization user identity
 * @param member Organization-scoped membership details
 * @param organization Current organization context
 */
@Serializable
data class MeResponse(
    val user: UserInfo,
    val member: MemberInfo,
    val organization: OrganizationInfo
)
