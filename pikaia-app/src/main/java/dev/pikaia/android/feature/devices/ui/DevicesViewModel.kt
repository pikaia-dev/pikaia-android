package dev.pikaia.android.feature.devices.ui

import dev.pikaia.android.feature.devices.redux.DevicesAction
import dev.pikaia.android.feature.devices.redux.DevicesState
import dev.pikaia.android.feature.devices.redux.DevicesStoreProxy
import dev.pikaia.android.lib.base.BaseViewModel

class DevicesViewModel(
    storeProxy: DevicesStoreProxy
) : BaseViewModel<DevicesState, DevicesAction>(storeProxy, DevicesState()) {

    init {
        handleAction(DevicesAction.LoadDevices)
    }
}
