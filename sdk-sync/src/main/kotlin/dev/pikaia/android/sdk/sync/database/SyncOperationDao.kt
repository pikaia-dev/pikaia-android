package dev.pikaia.android.sdk.sync.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.pikaia.android.sdk.sync.data.entity.SyncOperationEntity
import kotlinx.coroutines.flow.Flow

/**
 * DAO for accessing sync operations.
 */
@Dao
interface SyncOperationDao {

    /**
     * Insert or replace an operation.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: SyncOperationEntity)

    /**
     * Insert multiple operations.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<SyncOperationEntity>)

    /**
     * Get pending operations ready to sync.
     * Includes operations with status "pending" and retry time has passed.
     *
     * @param limit Maximum number of operations to return
     * @param currentTime Current time in milliseconds
     */
    @Query("""
        SELECT * FROM sync_operations
        WHERE status = 'pending'
        AND (next_retry_at IS NULL OR next_retry_at <= :currentTime)
        ORDER BY created_at ASC
        LIMIT :limit
    """)
    suspend fun getPending(limit: Int, currentTime: Long): List<SyncOperationEntity>

    /**
     * Get operation by idempotency key.
     */
    @Query("SELECT * FROM sync_operations WHERE idempotency_key = :key")
    suspend fun getByKey(key: String): SyncOperationEntity?

    /**
     * Update operation status.
     */
    @Query("UPDATE sync_operations SET status = :status WHERE idempotency_key = :key")
    suspend fun updateStatus(key: String, status: String)

    /**
     * Update multiple operations to a status.
     */
    @Query("UPDATE sync_operations SET status = :status WHERE idempotency_key IN (:keys)")
    suspend fun updateStatusBatch(keys: List<String>, status: String)

    /**
     * Update operation for retry.
     *
     * @param key Idempotency key
     * @param retryCount New retry count
     * @param nextRetryAt When to retry next (milliseconds)
     * @param lastError Error message
     */
    @Query("""
        UPDATE sync_operations
        SET status = 'pending',
            retry_count = :retryCount,
            next_retry_at = :nextRetryAt,
            last_error = :lastError
        WHERE idempotency_key = :key
    """)
    suspend fun scheduleRetry(
        key: String,
        retryCount: Int,
        nextRetryAt: Long,
        lastError: String
    )

    /**
     * Mark operation as failed.
     */
    @Query("""
        UPDATE sync_operations
        SET status = 'failed', last_error = :error
        WHERE idempotency_key = :key
    """)
    suspend fun markFailed(key: String, error: String)

    /**
     * Delete operation by key.
     */
    @Query("DELETE FROM sync_operations WHERE idempotency_key = :key")
    suspend fun delete(key: String)

    /**
     * Delete operations by keys.
     */
    @Query("DELETE FROM sync_operations WHERE idempotency_key IN (:keys)")
    suspend fun deleteBatch(keys: List<String>)

    /**
     * Delete all operations.
     */
    @Query("DELETE FROM sync_operations")
    suspend fun deleteAll()

    /**
     * Get count of pending operations.
     */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'pending'")
    suspend fun getPendingCount(): Int

    /**
     * Observe pending operation count.
     */
    @Query("SELECT COUNT(*) FROM sync_operations WHERE status = 'pending'")
    fun observePendingCount(): Flow<Int>

    /**
     * Get all failed operations.
     */
    @Query("SELECT * FROM sync_operations WHERE status = 'failed' ORDER BY created_at DESC")
    suspend fun getFailedOperations(): List<SyncOperationEntity>

    /**
     * Observe failed operations.
     */
    @Query("SELECT * FROM sync_operations WHERE status = 'failed' ORDER BY created_at DESC")
    fun observeFailedOperations(): Flow<List<SyncOperationEntity>>
}
