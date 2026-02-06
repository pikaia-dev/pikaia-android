package dev.pikaia.android.sdk.sync.queue

import dev.pikaia.android.sdk.sync.data.entity.SyncOperationEntity
import dev.pikaia.android.sdk.sync.database.SyncOperationDao
import dev.pikaia.android.sdk.sync.retry.SyncRetryPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Queue for managing pending sync operations.
 *
 * Persists operations using Room and manages their lifecycle:
 * pending → in_progress → synced/failed
 */
class OperationQueue(
    private val dao: SyncOperationDao,
    private val retryPolicy: SyncRetryPolicy
) {

    /**
     * Enqueue a new operation for syncing.
     *
     * @param idempotencyKey Unique identifier for idempotent processing
     * @param entityType Type of entity (e.g., "crm.contact")
     * @param entityId Unique identifier of the entity
     * @param intent Operation intent (CREATE, UPDATE, DELETE)
     * @param payload JSON payload as string
     * @param baseVersion Optional version for optimistic concurrency
     */
    suspend fun enqueue(
        idempotencyKey: String,
        entityType: String,
        entityId: String,
        intent: SyncIntent,
        payload: String,
        baseVersion: Int? = null
    ) {
        val operation = SyncOperationEntity(
            idempotencyKey = idempotencyKey,
            entityType = entityType,
            entityId = entityId,
            intent = intent.toApiString(),
            payload = payload,
            baseVersion = baseVersion,
            status = SyncOperationStatus.PENDING.name.lowercase(),
            retryCount = 0,
            nextRetryAt = null,
            createdAt = Clock.System.now().toEpochMilliseconds(),
            lastError = null
        )
        dao.insert(operation)
    }

    /**
     * Get pending operations ready to sync.
     *
     * Returns operations with status "pending" where retry time has passed.
     *
     * @param limit Maximum number of operations to return
     * @return List of pending operations
     */
    suspend fun getPending(limit: Int): List<SyncOperationEntity> {
        val currentTime = Clock.System.now().toEpochMilliseconds()
        return dao.getPending(limit, currentTime)
    }

    /**
     * Mark operations as in-progress.
     *
     * @param idempotencyKeys List of operation keys
     */
    suspend fun markInProgress(idempotencyKeys: List<String>) {
        dao.updateStatusBatch(idempotencyKeys, SyncOperationStatus.IN_PROGRESS.name.lowercase())
    }

    /**
     * Mark operations as successfully synced.
     *
     * These operations will be deleted from the queue.
     *
     * @param idempotencyKeys List of operation keys
     */
    suspend fun markSynced(idempotencyKeys: List<String>) {
        // Delete synced operations (no need to keep them)
        dao.deleteBatch(idempotencyKeys)
    }

    /**
     * Mark operation as failed (max retries exceeded).
     *
     * @param idempotencyKey Operation key
     * @param error Error message
     */
    suspend fun markFailed(idempotencyKey: String, error: String) {
        dao.markFailed(idempotencyKey, error)
    }

    /**
     * Schedule operation for retry with exponential backoff.
     *
     * @param idempotencyKey Operation key
     * @param error Error message from failed attempt
     */
    suspend fun scheduleRetry(idempotencyKey: String, error: String) {
        val operation = dao.getByKey(idempotencyKey) ?: return

        val newRetryCount = operation.retryCount + 1

        if (retryPolicy.shouldRetry(newRetryCount)) {
            val nextRetry = retryPolicy.calculateNextRetry(newRetryCount)
            dao.scheduleRetry(
                key = idempotencyKey,
                retryCount = newRetryCount,
                nextRetryAt = nextRetry.toEpochMilliseconds(),
                lastError = error
            )
        } else {
            // Max retries exceeded
            markFailed(idempotencyKey, "Max retry attempts exceeded: $error")
        }
    }

    /**
     * Clear all operations from the queue.
     *
     * Used when triggering a full resync.
     */
    suspend fun clearAll() {
        dao.deleteAll()
    }

    /**
     * Check if there are pending operations.
     *
     * @return true if any operations are pending
     */
    suspend fun hasPendingOperations(): Boolean {
        return dao.getPendingCount() > 0
    }

    /**
     * Observe pending operation count.
     *
     * @return Flow emitting count of pending operations
     */
    fun observePendingCount(): Flow<Int> {
        return dao.observePendingCount()
    }

    /**
     * Observe failed operations.
     *
     * @return Flow emitting list of failed operations
     */
    fun observeFailedOperations(): Flow<List<SyncOperationEntity>> {
        return dao.observeFailedOperations()
    }

    /**
     * Get all failed operations.
     *
     * @return List of failed operations
     */
    suspend fun getFailedOperations(): List<SyncOperationEntity> {
        return dao.getFailedOperations()
    }
}
