package dev.pikaia.android.sdk.sync.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for storing pending sync operations.
 *
 * Operations are persisted to survive app restarts and process death.
 */
@Entity(tableName = "sync_operations")
data class SyncOperationEntity(
    /**
     * Unique identifier for idempotent operation processing.
     * Also serves as primary key.
     */
    @PrimaryKey
    @ColumnInfo(name = "idempotency_key")
    val idempotencyKey: String,

    /**
     * Type of entity (e.g., "crm.contact", "crm.tag").
     * Application-defined, SDK is agnostic.
     */
    @ColumnInfo(name = "entity_type")
    val entityType: String,

    /**
     * Unique identifier of the entity.
     */
    @ColumnInfo(name = "entity_id")
    val entityId: String,

    /**
     * Operation intent: "create", "update", or "delete".
     */
    @ColumnInfo(name = "intent")
    val intent: String,

    /**
     * JSON payload as string.
     * Empty for delete operations.
     */
    @ColumnInfo(name = "payload")
    val payload: String,

    /**
     * Optional version for optimistic concurrency control.
     */
    @ColumnInfo(name = "base_version")
    val baseVersion: Int? = null,

    /**
     * Current status of the operation.
     * One of: "pending", "in_progress", "synced", "failed".
     */
    @ColumnInfo(name = "status")
    val status: String,

    /**
     * Number of retry attempts made.
     */
    @ColumnInfo(name = "retry_count")
    val retryCount: Int = 0,

    /**
     * Unix timestamp (milliseconds) for next retry attempt.
     * Null if no retry scheduled.
     */
    @ColumnInfo(name = "next_retry_at")
    val nextRetryAt: Long? = null,

    /**
     * Unix timestamp (milliseconds) when operation was created.
     */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /**
     * Last error message if operation failed.
     */
    @ColumnInfo(name = "last_error")
    val lastError: String? = null
)
