package dev.pikaia.android.sdk.sync.applier

import dev.pikaia.android.sdk.sync.data.response.SyncChange

/**
 * Interface for applying server changes to local data.
 *
 * Implementations route changes to entity-specific handlers.
 */
interface SyncChangeApplier {

    /**
     * Apply a batch of changes from the server.
     *
     * @param changes List of changes to apply
     * @return Result with success/failure counts
     */
    suspend fun applyChanges(changes: List<SyncChange>): SyncBatchResult
}
