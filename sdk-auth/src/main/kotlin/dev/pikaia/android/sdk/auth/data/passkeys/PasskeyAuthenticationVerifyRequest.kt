package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/**
 * Request to verify a passkey authentication.
 *
 * @param challengeId Challenge ID from the authentication options
 * @param credential Credential response from the platform credential API, verbatim
 * @param organizationId Optional organization to authenticate into
 */
@Serializable
data class PasskeyAuthenticationVerifyRequest(
    @SerialName("challenge_id")
    val challengeId: String,
    val credential: JsonObject,
    @SerialName("organization_id")
    val organizationId: String? = null
)
