package dev.pikaia.android.sdk.sync.engine

import android.util.Log
import dev.pikaia.android.sdk.sync.api.SyncAPI
import dev.pikaia.android.sdk.sync.applier.SyncChangeApplier
import dev.pikaia.android.sdk.sync.cursor.SyncCursorStore
import dev.pikaia.android.sdk.sync.data.request.SyncPushRequest
import dev.pikaia.android.sdk.sync.queue.OperationQueue
import dev.pikaia.android.sdk.sync.queue.SyncIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Core sync engine that orchestrates bidirectional synchronization.
 *
 * Implements push-then-pull strategy:
 * 1. Push pending local operations to server
 * 2. Pull remote changes from server
 * 3. Apply remote changes to local database via ChangeApplier
 *
 * Thread-safe with mutex to prevent concurrent sync operations.
 */
class SyncEngine(
    private val syncAPI: SyncAPI,
    private val operationQueue: OperationQueue,
    private val cursorStore: SyncCursorStore,
    private val changeApplier: SyncChangeApplier
) {
    private val syncMutex = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)

    /**
     * Observable sync state.
     */
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /**
     * Perform a full sync operation (push then pull).
     *
     * @param entityTypes Optional list of entity types to sync (null = all)
     * @param limit Optional limit for number of changes to pull
     * @return Result of the sync operation
     */
    suspend fun sync(
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): SyncResult {
        return syncMutex.withLock {
            try {
                _state.value = SyncState.Syncing(SyncPhase.PUSHING)
                val pushResult = pushInternal()

                _state.value = SyncState.Syncing(SyncPhase.PULLING)
                val pullResult = pullInternal(entityTypes, limit)

                val result = SyncResult(
                    pushedCount = pushResult,
                    pulledCount = pullResult.pulledCount,
                    appliedCount = pullResult.appliedCount,
                    failedCount = pullResult.failedCount
                )

                _state.value = SyncState.Success(result)
                result

            } catch (e: Exception) {
                Log.e(TAG, "Sync failed", e)
                _state.value = SyncState.Error("Sync failed: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Push pending operations to server only.
     *
     * @return Number of operations pushed
     */
    suspend fun forcePush(): Int {
        return syncMutex.withLock {
            try {
                _state.value = SyncState.Syncing(SyncPhase.PUSHING)
                val result = pushInternal()
                _state.value = SyncState.Idle
                result
            } catch (e: Exception) {
                Log.e(TAG, "Push failed", e)
                _state.value = SyncState.Error("Push failed: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Pull changes from server only.
     *
     * @param entityTypes Optional list of entity types to sync
     * @param limit Optional limit for number of changes to pull
     * @return Result of the pull operation
     */
    suspend fun forcePull(
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): PullResult {
        return syncMutex.withLock {
            try {
                _state.value = SyncState.Syncing(SyncPhase.PULLING)
                val result = pullInternal(entityTypes, limit)
                _state.value = SyncState.Idle
                result
            } catch (e: Exception) {
                Log.e(TAG, "Pull failed", e)
                _state.value = SyncState.Error("Pull failed: ${e.message}", e)
                throw e
            }
        }
    }

    /**
     * Force a complete resync by clearing local state.
     *
     * This clears:
     * - Sync cursor (will pull all changes from beginning)
     * - Failed operations (gives them another chance)
     *
     * Does NOT clear successfully synced operations or local data.
     */
    suspend fun forceResync() {
        syncMutex.withLock {
            Log.i(TAG, "Force resync initiated")
            cursorStore.clearCursor()
        }
    }

    /**
     * Flow variant of sync operation.
     */
    fun syncFlow(
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): Flow<SyncResult> = flow {
        emit(sync(entityTypes, limit))
    }

    /**
     * Flow variant of force push.
     */
    fun forcePushFlow(): Flow<Int> = flow {
        emit(forcePush())
    }

    /**
     * Flow variant of force pull.
     */
    fun forcePullFlow(
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): Flow<PullResult> = flow {
        emit(forcePull(entityTypes, limit))
    }

    /**
     * Internal push implementation.
     *
     * @return Number of operations pushed
     */
    private suspend fun pushInternal(): Int {
        val pendingOps = operationQueue.getPending(limit = 100)
        if (pendingOps.isEmpty()) {
            Log.d(TAG, "No pending operations to push")
            return 0
        }

        Log.i(TAG, "Pushing ${pendingOps.size} operations")

        // Convert to API format
        val operations = pendingOps.map { op ->
            dev.pikaia.android.sdk.sync.data.request.SyncOperation(
                idempotencyKey = op.idempotencyKey,
                entityType = op.entityType,
                entityId = op.entityId,
                intent = SyncIntent.fromApiString(op.intent).toApiString(),
                data = op.payload,
                clientTimestamp = kotlinx.datetime.Instant.fromEpochMilliseconds(op.createdAt),
                baseVersion = op.baseVersion,
                retryCount = op.retryCount
            )
        }

        // Push to server
        val request = SyncPushRequest(operations)
        val response = syncAPI.push(request)

        // Mark operations as synced
        val syncedKeys = pendingOps.map { it.idempotencyKey }
        operationQueue.markSynced(syncedKeys)

        Log.i(TAG, "Successfully pushed ${response.results.size} operations")
        return response.results.size
    }

    /**
     * Internal pull implementation.
     *
     * @return Result of the pull operation
     */
    private suspend fun pullInternal(
        entityTypes: List<String>?,
        limit: Int?
    ): PullResult {
        var totalPulled = 0
        var totalApplied = 0
        var totalFailed = 0

        var hasMore = true
        var cursor = cursorStore.getCursor()

        while (hasMore) {
            val response = syncAPI.pull(
                since = cursor,
                entityTypes = entityTypes,
                limit = limit
            )

            // Handle force resync flag from server
            if (response.forceResync) {
                Log.w(TAG, "Server requested force resync")
                cursorStore.clearCursor()
                // In a real implementation, might want to notify the app
                // to clear local data and restart sync
                break
            }

            if (response.changes.isNotEmpty()) {
                Log.i(TAG, "Pulled ${response.changes.size} changes")
                totalPulled += response.changes.size

                // Apply changes
                val batchResult = changeApplier.applyChanges(response.changes)
                totalApplied += batchResult.successCount
                totalFailed += batchResult.failureCount

                Log.i(TAG, "Applied ${batchResult.successCount} changes, ${batchResult.failureCount} failed")
            }

            // Update cursor
            response.cursor?.let { newCursor ->
                cursorStore.setCursor(newCursor)
                cursor = newCursor
            }

            hasMore = response.hasMore
        }

        return PullResult(totalPulled, totalApplied, totalFailed)
    }

    companion object {
        private const val TAG = "SyncEngine"
    }
}

/**
 * Result of a pull operation.
 */
data class PullResult(
    val pulledCount: Int,
    val appliedCount: Int,
    val failedCount: Int
)
