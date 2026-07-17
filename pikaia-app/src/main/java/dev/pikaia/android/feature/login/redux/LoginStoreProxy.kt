package dev.pikaia.android.feature.login.redux

import dev.pikaia.android.lib.store.FeatureStoreProxy
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState

class LoginStoreProxy(
    store: Store<AppState>
) : FeatureStoreProxy<AppState, LoginState, LoginAction>(store) {

    override fun getFeatureStateProjection(appState: AppState) = appState.login

    override fun isEffectRelevant(effect: NavigationEffect) = effect is LoginNavigationEffect

    override val clearStateAction: LoginAction = LoginAction.Reset
}
