package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request to verify a passkey registration.
 *
 * @param challengeId Challenge ID from the registration options
 * @param credential Credential response from the platform credential API, verbatim
 * @param name User-friendly name for the passkey (e.g. "Pixel 9 Pro")
 */
@Serializable
data class PasskeyRegistrationVerifyRequest(
    @SerialName("challenge_id")
    val challengeId: String,
    val credential: JsonObject,
    val name: String
)
