package dev.pikaia.android.sdk.sync.api

import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.EmptyBody
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint
import dev.pikaia.android.sdk.sync.data.request.SyncPushRequest
import dev.pikaia.android.sdk.sync.data.response.SyncPullResponse
import dev.pikaia.android.sdk.sync.data.response.SyncPushResponse
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Sync API client providing push/pull endpoints.
 *
 * Handles data synchronization with the Pikaia backend.
 *
 * Each endpoint provides two variants:
 * - Suspend function for direct await usage
 * - Flow-based function for reactive streams
 *
 * @param client The underlying API client for making HTTP requests
 */
class SyncAPI(private val client: APIClient) {

    // ========================================================================
    // Push Operations
    // ========================================================================

    /**
     * Push a batch of operations to the server.
     *
     * @param request Batch of operations to sync
     * @return Response with per-operation results
     */
    suspend fun push(request: SyncPushRequest): SyncPushResponse {
        val endpoint = endpoint<SyncPushResponse>(
            method = HTTPMethod.POST,
            path = "/v1/sync/push",
            body = request
        )
        return client.send(endpoint)
    }

    /**
     * Push a batch of operations to the server (Flow variant).
     *
     * @param request Batch of operations to sync
     * @return Flow emitting the response
     */
    fun pushFlow(request: SyncPushRequest): Flow<SyncPushResponse> = flow {
        emit(push(request))
    }

    // ========================================================================
    // Pull Operations
    // ========================================================================

    /**
     * Pull changes from the server.
     *
     * Uses query parameters for filtering:
     * - since: Cursor from previous pull (pagination)
     * - entity_types: Comma-separated list of entity types to filter
     * - limit: Maximum number of changes to return
     *
     * @param since Cursor from previous pull (null for initial pull)
     * @param entityTypes Optional list of entity types to filter
     * @param limit Maximum number of changes to return
     * @return Response with changes, cursor, and pagination info
     */
    suspend fun pull(
        since: String? = null,
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): SyncPullResponse {
        // Build query parameters
        val queryParams = buildList {
            since?.let { add("since" to it) }
            entityTypes?.takeIf { it.isNotEmpty() }?.let {
                add("entity_types" to it.joinToString(","))
            }
            limit?.let { add("limit" to it.toString()) }
        }

        val endpoint = endpoint<SyncPullResponse>(
            method = HTTPMethod.GET,
            path = "/v1/sync/pull",
            query = queryParams.toMap()
        )
        return client.send(endpoint)
    }

    /**
     * Pull changes from the server (Flow variant).
     *
     * @param since Cursor from previous pull (null for initial pull)
     * @param entityTypes Optional list of entity types to filter
     * @param limit Maximum number of changes to return
     * @return Flow emitting the response
     */
    fun pullFlow(
        since: String? = null,
        entityTypes: List<String>? = null,
        limit: Int? = null
    ): Flow<SyncPullResponse> = flow {
        emit(pull(since, entityTypes, limit))
    }
}
