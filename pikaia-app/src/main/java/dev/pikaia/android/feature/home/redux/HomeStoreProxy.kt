package dev.pikaia.android.feature.home.redux

import dev.pikaia.android.lib.store.FeatureStoreProxy
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState

class HomeStoreProxy(
    store: Store<AppState>
) : FeatureStoreProxy<AppState, HomeState, HomeAction>(store) {

    override fun getFeatureStateProjection(appState: AppState) = HomeState(
        message = appState.homeMessage
    )

    override fun isEffectRelevant(effect: NavigationEffect) = false

}
