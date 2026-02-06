package dev.pikaia.android.sdk.auth.data.common

import kotlinx.serialization.Serializable

/**
 * Common response containing a simple message.
 *
 * Used by endpoints that return simple success/status messages.
 *
 * @param message The response message
 */
@Serializable
data class MessageResponse(
    val message: String
)
