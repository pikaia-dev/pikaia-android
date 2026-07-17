package dev.pikaia.android.feature.login.data

/**
 * An organization the user can enter after magic-link authentication.
 */
data class LoginOrganization(
    val id: String,
    val name: String,
    val slug: String
)

/**
 * Result of magic-link authentication: the intermediate session token to exchange
 * plus the organizations discovered for this user.
 */
data class AuthenticatedLogin(
    val intermediateSessionToken: String,
    val organizations: List<LoginOrganization>
)
