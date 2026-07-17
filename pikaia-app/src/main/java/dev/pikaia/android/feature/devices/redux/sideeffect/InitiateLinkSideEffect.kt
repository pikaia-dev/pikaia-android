package dev.pikaia.android.feature.devices.redux.sideeffect

import dev.pikaia.android.feature.devices.domain.InitiateDeviceLink
import dev.pikaia.android.feature.devices.redux.DevicesAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.catch

class InitiateLinkSideEffect(
    private val store: Store<AppState>,
    private val initiateDeviceLink: InitiateDeviceLink
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action != DevicesAction.InitiateLink) return

        initiateDeviceLink(Unit)
            .catch {
                store.postNavigationEffect(ErrorNavigationEffect(it))
                store.dispatch(DevicesAction.OperationFailed)
            }
            .collect { store.dispatch(DevicesAction.LinkInitiated(it)) }
    }
}
