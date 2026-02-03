package dev.pikaia.android.feature.profile.redux

import dev.pikaia.android.lib.store.FeatureStoreProxy
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState

class ProfileStoreProxy(
    store: Store<AppState>
) : FeatureStoreProxy<AppState, ProfileState, ProfileAction>(store) {

    override fun getFeatureStateProjection(appState: AppState) = ProfileState(
        profile = appState.profile
    )

    override fun isEffectRelevant(effect: NavigationEffect) = effect is ProfileNavigationEffect
}
