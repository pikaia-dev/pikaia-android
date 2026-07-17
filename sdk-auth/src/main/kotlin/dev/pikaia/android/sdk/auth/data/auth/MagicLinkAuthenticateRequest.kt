package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.Serializable

/**
 * Request to authenticate using a magic link token.
 *
 * @param token Magic link token from the email
 */
@Serializable
data class MagicLinkAuthenticateRequest(
    val token: String
)
