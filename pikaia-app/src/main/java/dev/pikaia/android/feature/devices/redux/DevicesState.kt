package dev.pikaia.android.feature.devices.redux

import dev.pikaia.android.feature.devices.data.Device
import dev.pikaia.android.feature.devices.data.DeviceLink

data class DevicesState(
    val devices: List<Device> = emptyList(),
    val link: DeviceLink? = null,
    val isBusy: Boolean = false
)
