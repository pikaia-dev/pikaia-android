package dev.pikaia.android.sdk.auth.data.profile

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Request to send an OTP to a phone number.
 *
 * @param phoneNumber Phone number in E.164 format (e.g., "+14155551234")
 */
@Serializable
data class SendPhoneOtpRequest(
    @SerialName("phone_number")
    val phoneNumber: String
)
