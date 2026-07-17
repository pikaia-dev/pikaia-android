package dev.pikaia.android.feature.devices.redux

import dev.pikaia.android.lib.store.Action
import dev.pikaia.android.lib.store.Reducer
import dev.pikaia.android.state.AppState

class DevicesActionReducer : Reducer<AppState> {

    override fun invoke(action: Action, state: AppState) = when (action) {
        DevicesAction.LoadDevices,
        DevicesAction.InitiateLink,
        is DevicesAction.RevokeDevice,
        DevicesAction.RefreshSession -> state.updateDevices {
            copy(isBusy = true)
        }

        is DevicesAction.DevicesLoaded -> state.updateDevices {
            copy(devices = action.devices, isBusy = false)
        }

        is DevicesAction.LinkInitiated -> state.updateDevices {
            copy(link = action.link, isBusy = false)
        }

        is DevicesAction.DeviceRevoked -> state.updateDevices {
            copy(devices = devices.filterNot { it.id == action.deviceId }, isBusy = false)
        }

        DevicesAction.SessionRefreshed,
        DevicesAction.OperationFailed -> state.updateDevices {
            copy(isBusy = false)
        }

        DevicesAction.Reset -> state.copy(devices = DevicesState())

        else -> state
    }

    private fun AppState.updateDevices(transform: DevicesState.() -> DevicesState) =
        copy(devices = devices.transform())
}
