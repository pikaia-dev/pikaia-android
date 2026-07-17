package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.Serializable

/**
 * Response after requesting a phone OTP.
 *
 * @param success Whether the operation succeeded
 * @param message Human-readable status message
 */
@Serializable
data class PhoneOtpResponse(
    val success: Boolean,
    val message: String
)
