package dev.pikaia.android.feature.login.redux

import dev.pikaia.android.feature.login.data.AuthenticatedLogin
import dev.pikaia.android.lib.store.Action

sealed interface LoginAction : Action {
    data class SubmitEmail(val email: String) : LoginAction
    data object MagicLinkSent : LoginAction
    data class SubmitToken(val token: String) : LoginAction
    data class Authenticated(val login: AuthenticatedLogin) : LoginAction
    data class SelectOrganization(val organizationId: String) : LoginAction
    data class SubmitNewOrganization(val name: String, val slug: String) : LoginAction
    data object LoggedIn : LoginAction
    data object SubmissionFailed : LoginAction
    data object Reset : LoginAction
}
