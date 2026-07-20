package dev.pikaia.android.feature.login.redux.sideeffect

import dev.pikaia.android.feature.login.domain.AuthenticateMagicLink
import dev.pikaia.android.feature.login.redux.LoginAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.catch

class AuthenticateMagicLinkSideEffect(
    private val store: Store<AppState>,
    private val authenticateMagicLink: AuthenticateMagicLink
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action !is LoginAction.SubmitToken) return

        authenticateMagicLink(action.token)
            .catch {
                store.postNavigationEffect(ErrorNavigationEffect(it))
                store.dispatch(LoginAction.SubmissionFailed)
            }
            .collect { store.dispatch(LoginAction.Authenticated(it)) }
    }
}
