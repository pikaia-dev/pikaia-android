package dev.pikaia.android.activity.redux

import dev.pikaia.android.lib.snackbar.SnackbarNavigationEffect
import dev.pikaia.android.lib.store.FeatureStoreProxy
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState

class MainStoreProxy(
    store: Store<AppState>
) : FeatureStoreProxy<AppState, MainState, MainAction>(store) {

    override fun getFeatureStateProjection(appState: AppState) = MainState(
        isLoading = appState.isLoading
    )

    override fun isEffectRelevant(effect: NavigationEffect) = effect is SnackbarNavigationEffect
}