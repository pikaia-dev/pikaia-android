package dev.pikaia.android.feature.login.redux.sideeffect

import dev.pikaia.android.feature.login.domain.SendMagicLink
import dev.pikaia.android.feature.login.redux.LoginAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.catch

class SendMagicLinkSideEffect(
    private val store: Store<AppState>,
    private val sendMagicLink: SendMagicLink
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action !is LoginAction.SubmitEmail) return

        sendMagicLink(action.email)
            .catch {
                store.postNavigationEffect(ErrorNavigationEffect(it))
                store.dispatch(LoginAction.SubmissionFailed)
            }
            .collect { store.dispatch(LoginAction.MagicLinkSent) }
    }
}
