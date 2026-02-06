package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from sending phone OTP.
 *
 * @param methodId Method ID for OTP verification
 */
@Serializable
data class PhoneOtpResponse(
    @SerialName("method_id")
    val methodId: String
)
