package dev.pikaia.android.feature.devices.redux

import dev.pikaia.android.lib.store.FeatureStoreProxy
import dev.pikaia.android.lib.store.NavigationEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState

class DevicesStoreProxy(
    store: Store<AppState>
) : FeatureStoreProxy<AppState, DevicesState, DevicesAction>(store) {

    override fun getFeatureStateProjection(appState: AppState) = appState.devices

    override fun isEffectRelevant(effect: NavigationEffect) = false

    override val clearStateAction: DevicesAction = DevicesAction.Reset
}
