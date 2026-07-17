package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to verify a phone OTP code.
 *
 * @param phoneNumber Phone number the code was sent to, in E.164 format
 * @param otpCode The one-time code from the SMS
 */
@Serializable
data class VerifyPhoneOtpRequest(
    @SerialName("phone_number")
    val phoneNumber: String,
    @SerialName("otp_code")
    val otpCode: String
)
