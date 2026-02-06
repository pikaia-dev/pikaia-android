package dev.pikaia.android.sdk.sync.data.request

import dev.pikaia.android.sdk.sync.data.serialization.InstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Individual sync operation to be pushed to the server.
 *
 * @param idempotencyKey Unique identifier for idempotent operation processing
 * @param entityType Type of entity (e.g., "crm.contact", "crm.tag")
 * @param entityId Unique identifier of the entity
 * @param intent Operation type: "create", "update", or "delete"
 * @param data JSON payload as string
 * @param clientTimestamp When the operation was created on client
 * @param baseVersion Optional version for optimistic concurrency control
 * @param retryCount Number of retry attempts
 */
@Serializable
data class SyncOperation(
    @SerialName("idempotency_key")
    val idempotencyKey: String,
    @SerialName("entity_type")
    val entityType: String,
    @SerialName("entity_id")
    val entityId: String,
    val intent: String,
    val data: String,
    @SerialName("client_timestamp")
    @Serializable(with = InstantSerializer::class)
    val clientTimestamp: Instant,
    @SerialName("base_version")
    val baseVersion: Int? = null,
    @SerialName("retry_count")
    val retryCount: Int = 0
)
