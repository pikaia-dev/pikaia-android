package dev.pikaia.android.sdk.sync.applier

/**
 * Result of applying a batch of changes.
 *
 * @param successCount Number of successfully applied changes
 * @param failureCount Number of failed changes
 * @param failures List of failures with details
 */
data class SyncBatchResult(
    val successCount: Int,
    val failureCount: Int,
    val failures: List<FailureDetail> = emptyList()
) {
    /**
     * Details of a failed change.
     *
     * @param entityType Entity type that failed
     * @param entityId Entity ID that failed
     * @param error Error message
     */
    data class FailureDetail(
        val entityType: String,
        val entityId: String,
        val error: String
    )

    companion object {
        /**
         * Create an empty result.
         */
        fun empty() = SyncBatchResult(0, 0, emptyList())
    }
}
