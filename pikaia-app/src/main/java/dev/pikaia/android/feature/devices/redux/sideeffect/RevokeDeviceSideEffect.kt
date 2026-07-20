package dev.pikaia.android.feature.devices.redux.sideeffect

import dev.pikaia.android.feature.devices.domain.RevokeDevice
import dev.pikaia.android.feature.devices.redux.DevicesAction
import dev.pikaia.android.lib.snackbar.ErrorNavigationEffect
import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.SideEffect
import dev.pikaia.android.lib.store.Store
import dev.pikaia.android.state.AppState
import kotlinx.coroutines.flow.catch

class RevokeDeviceSideEffect(
    private val store: Store<AppState>,
    private val revokeDevice: RevokeDevice
) : SideEffect {

    override suspend fun invoke(action: Action) {
        if (action !is DevicesAction.RevokeDevice) return

        revokeDevice(action.deviceId)
            .catch {
                store.postNavigationEffect(ErrorNavigationEffect(it))
                store.dispatch(DevicesAction.OperationFailed)
            }
            .collect { store.dispatch(DevicesAction.DeviceRevoked(action.deviceId)) }
    }
}
