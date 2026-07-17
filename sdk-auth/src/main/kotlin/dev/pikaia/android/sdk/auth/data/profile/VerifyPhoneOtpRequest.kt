package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to verify a phone OTP code.
 *
 * @param methodId Method ID from the send OTP response
 * @param code The 6-digit OTP code
 */
@Serializable
data class VerifyPhoneOtpRequest(
    @SerialName("method_id")
    val methodId: String,
    val code: String
)
