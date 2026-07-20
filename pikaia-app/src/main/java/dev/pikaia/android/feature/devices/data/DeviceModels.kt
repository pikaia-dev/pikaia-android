package dev.pikaia.android.feature.devices.data

/**
 * A device linked to the user's account.
 */
data class Device(
    val id: Long,
    val name: String,
    val platform: String,
    val createdAt: String
)

/**
 * A pending device-link: render [qrUrl] as a QR code and scan it from the new device.
 */
data class DeviceLink(
    val qrUrl: String,
    val expiresInSeconds: Int
)
