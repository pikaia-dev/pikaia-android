package dev.pikaia.android.sdk.auth.data.devices

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to refresh a device session.
 *
 * @param deviceUuid Unique device identifier used when the device was linked
 */
@Serializable
data class DeviceSessionRefreshRequest(
    @SerialName("device_uuid")
    val deviceUuid: String
)
