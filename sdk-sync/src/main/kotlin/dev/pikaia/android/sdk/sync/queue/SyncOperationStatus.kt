package dev.pikaia.android.sdk.sync.queue

/**
 * Status of a sync operation in the queue.
 */
enum class SyncOperationStatus {
    /**
     * Operation is pending and ready to be synced.
     */
    PENDING,

    /**
     * Operation is currently being synced.
     */
    IN_PROGRESS,

    /**
     * Operation has been successfully synced.
     */
    SYNCED,

    /**
     * Operation failed after max retry attempts.
     */
    FAILED
}
