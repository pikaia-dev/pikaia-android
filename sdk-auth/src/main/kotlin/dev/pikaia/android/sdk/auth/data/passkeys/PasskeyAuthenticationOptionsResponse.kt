package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * WebAuthn options for authenticating with a passkey.
 *
 * @param challengeId Challenge ID to echo back in the verification request
 * @param options WebAuthn request options, passed verbatim to the platform
 *   credential API (Credential Manager / `navigator.credentials.get()`)
 */
@Serializable
data class PasskeyAuthenticationOptionsResponse(
    @SerialName("challenge_id")
    val challengeId: String,
    val options: JsonObject
)
