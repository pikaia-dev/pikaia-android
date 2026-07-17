package dev.pikaia.android.sdk.sync.api

import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.ApiResult
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint
import dev.pikaia.android.sdk.sync.data.request.SyncPushRequest
import dev.pikaia.android.sdk.sync.data.response.SyncPullResponse
import dev.pikaia.android.sdk.sync.data.response.SyncPushResponse

/**
 * Sync API client providing push/pull endpoints.
 *
 * Paths are relative — the [APIClient]'s `baseUrl` must carry the backend's API prefix
 * (e.g. `https://api.example.com/api/v1`).
 *
 * @param client The underlying API client for making HTTP requests
 */
class SyncAPI(private val client: APIClient) {

    /**
     * Push a batch of operations to the server.
     *
     * @param request Batch of operations to sync
     * @return Response with per-operation results
     */
    suspend fun push(request: SyncPushRequest): ApiResult<SyncPushResponse> {
        val endpoint = endpoint<SyncPushRequest, SyncPushResponse>(
            method = HTTPMethod.POST,
            path = "sync/push",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * Pull changes from the server.
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
    ): ApiResult<SyncPullResponse> {
        val queryParams = buildList {
            since?.let { add("since" to it) }
            entityTypes?.takeIf { it.isNotEmpty() }?.let {
                add("entity_types" to it.joinToString(","))
            }
            limit?.let { add("limit" to it.toString()) }
        }

        val endpoint = endpoint<SyncPullResponse>(
            method = HTTPMethod.GET,
            path = "sync/pull",
            query = queryParams.toMap()
        )
        return client.sendResult(endpoint)
    }
}
