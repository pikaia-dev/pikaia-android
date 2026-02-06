package dev.pikaia.android.sdk.auth.data.auth

import kotlinx.serialization.Serializable

/**
 * Request to send a magic link email.
 *
 * @param email Email address to send the magic link to
 */
@Serializable
data class MagicLinkSendRequest(
    val email: String
)
