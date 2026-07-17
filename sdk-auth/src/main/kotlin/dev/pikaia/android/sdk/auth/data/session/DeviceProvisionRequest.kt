package dev.pikaia.android.sdk.auth.data.session

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to provision a device with a shadow user.
 *
 * Shadow users allow offline-first apps to sync data before email verification.
 * Requires a mobile API key.
 *
 * @param deviceUuid Unique identifier for the device
 * @param email Email address for the shadow user
 * @param platform Platform identifier (e.g., "android", "ios")
 * @param name Optional user name
 * @param phoneNumber Optional phone number
 * @param deviceName Optional device name
 * @param osVersion Optional OS version
 * @param appVersion Optional app version
 */
@Serializable
data class DeviceProvisionRequest(
    @SerialName("device_uuid")
    val deviceUuid: String,
    val email: String,
    val platform: String,
    val name: String = "",
    @SerialName("phone_number")
    val phoneNumber: String = "",
    @SerialName("device_name")
    val deviceName: String = "",
    @SerialName("os_version")
    val osVersion: String = "",
    @SerialName("app_version")
    val appVersion: String = ""
)
