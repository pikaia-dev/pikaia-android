package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * WebAuthn options for registering a new passkey.
 *
 * @param challengeId Challenge ID to echo back in the verification request
 * @param options WebAuthn creation options, passed verbatim to the platform
 *   credential API (Credential Manager / `navigator.credentials.create()`)
 */
@Serializable
data class PasskeyRegistrationOptionsResponse(
    @SerialName("challenge_id")
    val challengeId: String,
    val options: JsonObject
)
