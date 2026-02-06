package dev.pikaia.android.sdk.sync.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Result of a single operation push.
 *
 * @param idempotencyKey The idempotency key of the operation
 * @param status Operation status: "success", "conflict", or "error"
 * @param message Optional error or conflict message
 */
@Serializable
data class PushResult(
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    val status: String,
    val message: String? = null
)
