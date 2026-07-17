package dev.pikaia.android.sdk.auth.data.devices

import dev.pikaia.android.sdk.auth.data.serialization.InstantSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Response with QR code data for device linking.
 *
 * @param qrUrl URL to encode in the QR code. The deep-link scheme is product-specific —
 *   the SDK passes it through verbatim; parsing belongs to the consumer.
 * @param expiresAt Link token expiration timestamp
 * @param expiresInSeconds Seconds until the link token expires
 */
@Serializable
data class InitiateLinkResponse(
    @SerialName("qr_url")
    val qrUrl: String,
    @Serializable(with = InstantSerializer::class)
    @SerialName("expires_at")
    val expiresAt: Instant,
    @SerialName("expires_in_seconds")
    val expiresInSeconds: Int
)
