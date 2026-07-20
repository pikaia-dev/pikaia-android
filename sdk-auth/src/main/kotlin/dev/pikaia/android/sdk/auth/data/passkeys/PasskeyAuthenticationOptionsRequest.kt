package dev.pikaia.android.sdk.auth.data.passkeys

import kotlinx.serialization.Serializable

/**
 * Request for passkey authentication options.
 *
 * @param email Optional email to filter allowed credentials
 */
@Serializable
data class PasskeyAuthenticationOptionsRequest(
    val email: String? = null
)
