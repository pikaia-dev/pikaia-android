package dev.pikaia.android.sdk.sync.data.response

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Response from pull operation containing server changes.
 *
 * @param changes List of changes to apply locally
 * @param cursor Opaque cursor for next pull (pagination)
 * @param hasMore Whether there are more changes to pull
 * @param forceResync Whether server requests a full resync (clear local state)
 */
@Serializable
data class SyncPullResponse(
    val changes: List<SyncChange>,
    val cursor: String?,
    @SerialName("has_more")
    val hasMore: Boolean,
    @SerialName("force_resync")
    val forceResync: Boolean = false
)
