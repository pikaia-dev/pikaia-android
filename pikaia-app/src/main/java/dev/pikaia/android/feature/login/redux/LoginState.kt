package dev.pikaia.android.feature.login.redux

import dev.pikaia.android.feature.login.data.LoginOrganization

enum class LoginPhase {
    /** Enter the email address to receive a magic link. */
    EMAIL,

    /** Enter the token from the received magic link. */
    TOKEN,

    /** Pick a discovered organization, or create one when none were found. */
    ORGANIZATION
}

data class LoginState(
    val phase: LoginPhase = LoginPhase.EMAIL,
    val email: String = "",
    val intermediateSessionToken: String = "",
    val organizations: List<LoginOrganization> = emptyList(),
    val isSubmitting: Boolean = false
)
