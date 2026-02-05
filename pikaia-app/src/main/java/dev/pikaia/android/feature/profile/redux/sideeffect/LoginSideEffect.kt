package dev.pikaia.android.feature.profile.redux.sideeffect

import dev.pikaia.android.feature.profile.redux.ProfileAction
import dev.pikaia.android.feature.profile.redux.ProfileNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState

class LoginSideEffect(
    private val store: Store<AppState>
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action != ProfileAction.Login) {
            return
        }
        store.postNavigationEffect(ProfileNavigationEffect.GoToLogin)
    }
}