package dev.pikaia.android.sdk.sync.engine

/**
 * Represents the current state of sync operations.
 */
sealed class SyncState {
    /**
     * Sync engine is idle, no operations in progress.
     */
    object Idle : SyncState()

    /**
     * Sync operation is in progress.
     *
     * @param phase Current phase of sync (pushing or pulling)
     */
    data class Syncing(val phase: SyncPhase) : SyncState()

    /**
     * Last sync operation completed successfully.
     *
     * @param result Summary of the sync operation
     */
    data class Success(val result: SyncResult) : SyncState()

    /**
     * Last sync operation failed.
     *
     * @param error Error message
     * @param exception Optional exception
     */
    data class Error(val error: String, val exception: Throwable? = null) : SyncState()
}

/**
 * Phases of a sync operation.
 */
enum class SyncPhase {
    PUSHING,  // Pushing local changes to server
    PULLING   // Pulling remote changes from server
}
