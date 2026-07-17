package dev.pikaia.android.sdk.sync.engine

/**
 * Result of a sync operation.
 *
 * @param pushedCount Number of operations pushed to server
 * @param pulledCount Number of changes pulled from server
 * @param appliedCount Number of changes successfully applied locally
 * @param failedCount Number of changes that failed to apply
 */
data class SyncResult(
    val pushedCount: Int,
    val pulledCount: Int,
    val appliedCount: Int,
    val failedCount: Int
) {
    companion object {
        fun empty() = SyncResult(0, 0, 0, 0)
    }
}
