package dev.pikaia.android.feature.login.redux

import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.Reducer
import dev.pikaia.android.state.AppState

class LoginActionReducer : Reducer<AppState> {

    override fun invoke(action: Action, state: AppState) = when (action) {
        is LoginAction.SubmitEmail -> state.updateLogin {
            copy(email = action.email, isSubmitting = true)
        }

        LoginAction.MagicLinkSent -> state.updateLogin {
            copy(phase = LoginPhase.TOKEN, isSubmitting = false)
        }

        is LoginAction.SubmitToken -> state.updateLogin {
            copy(isSubmitting = true)
        }

        is LoginAction.Authenticated -> state.updateLogin {
            copy(
                phase = LoginPhase.ORGANIZATION,
                intermediateSessionToken = action.login.intermediateSessionToken,
                organizations = action.login.organizations,
                isSubmitting = false
            )
        }

        is LoginAction.SelectOrganization,
        is LoginAction.SubmitNewOrganization -> state.updateLogin {
            copy(isSubmitting = true)
        }

        LoginAction.SubmissionFailed -> state.updateLogin {
            copy(isSubmitting = false)
        }

        LoginAction.LoggedIn,
        LoginAction.Reset -> state.copy(login = LoginState())

        else -> state
    }

    private fun AppState.updateLogin(transform: LoginState.() -> LoginState) =
        copy(login = login.transform())
}
