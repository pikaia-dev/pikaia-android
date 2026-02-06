package dev.pikaia.android.sdk.sync.data.response

import dev.pikaia.android.sdk.sync.data.serialization.InstantSerializer
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A change from the server to be applied locally.
 *
 * @param entityType Type of entity (e.g., "crm.contact")
 * @param entityId Unique identifier of the entity
 * @param operation Operation type: "create", "update", or "delete"
 * @param data JSON payload (null for deletes)
 * @param version Sync version for conflict resolution
 * @param updatedAt When the change was made on server
 */
@Serializable
data class SyncChange(
    @SerialName("entity_type")
    val entityType: String,
    @SerialName("entity_id")
    val entityId: String,
    val operation: String,
    val data: String?,
    val version: Int,
    @SerialName("updated_at")
    @Serializable(with = InstantSerializer::class)
    val updatedAt: Instant
)
