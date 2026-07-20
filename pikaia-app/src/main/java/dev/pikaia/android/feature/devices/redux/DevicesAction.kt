package dev.pikaia.android.feature.devices.redux

import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.feature.devices.data.DeviceLink
import dev.pikaia.android.lib.store.Action

sealed interface DevicesAction : Action {
    data object LoadDevices : DevicesAction
    data class DevicesLoaded(val devices: List<Device>) : DevicesAction
    data object InitiateLink : DevicesAction
    data class LinkInitiated(val link: DeviceLink) : DevicesAction
    data class RevokeDevice(val deviceId: Long) : DevicesAction
    data class DeviceRevoked(val deviceId: Long) : DevicesAction
    data object RefreshSession : DevicesAction
    data object SessionRefreshed : DevicesAction
    data object OperationFailed : DevicesAction
    data object Reset : DevicesAction
}
