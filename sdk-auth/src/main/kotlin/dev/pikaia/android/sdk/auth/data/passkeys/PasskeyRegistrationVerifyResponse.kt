package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response after successful passkey registration.
 *
 * @param id Passkey ID
 * @param name Passkey name
 * @param createdAt Creation timestamp (ISO-8601 string, as sent by the backend)
 */
@Serializable
data class PasskeyRegistrationVerifyResponse(
    val id: Long,
    val name: String,
    @SerialName("created_at")
    val createdAt: String
)
