package dev.pikaia.android.sdk.core.networking

import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * A description of a single API operation.
 *
 * Paths are relative to [APIClientConfig.baseUrl], which carries any API prefix — e.g. a
 * baseUrl of `https://api.example.com/api/v1` combined with a path of `auth/me`.
 *
 * @param method HTTP method
 * @param path Path relative to the client's base URL
 * @param query Query parameters
 * @param headers Endpoint-specific headers (override the client's defaults)
 * @param body Request body, serialized as JSON via [requestSerializer]
 * @param requestSerializer Serializer for [body]; null for bodiless requests
 * @param responseSerializer Serializer for the response body
 */
data class Endpoint<Req, Res>(
    val method: HTTPMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: Req? = null,
    val requestSerializer: KSerializer<Req>? = null,
    val responseSerializer: KSerializer<Res>
) {
    fun toKtorMethod(): HttpMethod = when (method) {
        HTTPMethod.GET -> HttpMethod.Get
        HTTPMethod.POST -> HttpMethod.Post
        HTTPMethod.PUT -> HttpMethod.Put
        HTTPMethod.PATCH -> HttpMethod.Patch
        HTTPMethod.DELETE -> HttpMethod.Delete
    }
}

/**
 * Create a bodiless endpoint.
 */
inline fun <reified Res> endpoint(
    method: HTTPMethod,
    path: String,
    query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap()
): Endpoint<Unit, Res> = Endpoint(
    method = method,
    path = path,
    query = query,
    headers = headers,
    body = null,
    requestSerializer = null,
    responseSerializer = serializer()
)

/**
 * Create an endpoint with a JSON request body.
 */
inline fun <reified Req : Any, reified Res> endpoint(
    method: HTTPMethod,
    path: String,
    body: Req,
    query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap()
): Endpoint<Req, Res> = Endpoint(
    method = method,
    path = path,
    query = query,
    headers = headers,
    body = body,
    requestSerializer = serializer(),
    responseSerializer = serializer()
)
