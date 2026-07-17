package dev.pikaia.android.sdk.auth.data.devices

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * A linked device.
 *
 * @param id Server-side device ID (use for revocation)
 * @param name Device name
 * @param platform Platform identifier (e.g. "android", "ios")
 * @param osVersion Operating system version
 * @param appVersion App version
 * @param createdAt When the device was linked
 */
@Serializable
data class DeviceInfo(
    val id: Long,
    val name: String,
    val platform: String,
    @SerialName("os_version")
    val osVersion: String,
    @SerialName("app_version")
    val appVersion: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("created_at")
    val createdAt: Instant
)
