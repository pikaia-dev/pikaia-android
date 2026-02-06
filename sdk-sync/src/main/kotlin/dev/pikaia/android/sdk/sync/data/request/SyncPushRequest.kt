package dev.pikaia.android.sdk.sync.data.request

import kotlinx.serialization.Serializable

/**
 * Request to push a batch of operations to the server.
 *
 * @param operations List of operations to sync
 */
@Serializable
data class SyncPushRequest(
    val operations: List<SyncOperation>
)
