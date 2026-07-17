package dev.pikaia.android.sdk.auth.data.devices

import kotlinx.serialization.Serializable

/**
 * The authenticated user's linked devices.
 *
 * @param devices Linked devices
 * @param count Number of linked devices
 */
@Serializable
data class DeviceListResponse(
    val devices: List<DeviceInfo>,
    val count: Int
)
