package dev.pikaia.android.sdk.sync.data.response

import kotlinx.serialization.Serializable

/**
 * Response from push operation containing results for each operation.
 *
 * @param results List of per-operation results
 */
@Serializable
data class SyncPushResponse(
    val results: List<PushResult>
)
