package dev.pikaia.android.sdk.core.networking

import io.ktor.http.*
import kotlinx.serialization.KSerializer
import kotlinx.serialization.serializer

/**
 * A description of a single API operation.
 *
 * Endpoints are type-safe definitions of HTTP requests that include:
 * - HTTP method (GET, POST, etc.)
 * - URL path
 * - Query parameters
 * - Headers
 * - Request body (optional)
 * - Response type and deserializer
 *
 * @param Req Request body type
 * @param Res Response type
 *
 * @param method HTTP method to use
 * @param path Relative path (e.g., "/v1/users")
 * @param query Query parameters
 * @param headers Additional headers for this endpoint
 * @param body Optional request body
 * @param responseSerializer Serializer for the response type
 */
data class Endpoint<Req, Res>(
    val method: HTTPMethod,
    val path: String,
    val query: Map<String, String> = emptyMap(),
    val headers: Map<String, String> = emptyMap(),
    val body: Req? = null,
    val responseSerializer: KSerializer<Res>
) {
    /**
     * Convert HTTPMethod to Ktor's HttpMethod.
     */
    fun toKtorMethod(): HttpMethod = when (method) {
        HTTPMethod.GET -> HttpMethod.Get
        HTTPMethod.POST -> HttpMethod.Post
        HTTPMethod.PUT -> HttpMethod.Put
        HTTPMethod.PATCH -> HttpMethod.Patch
        HTTPMethod.DELETE -> HttpMethod.Delete
    }
}

/**
 * Create an endpoint with automatic serializer inference for the response type.
 *
 * This is a convenience function that uses Kotlin's reified type parameters
 * to automatically obtain the serializer for the response type.
 *
 * @param Res Response type (must be serializable)
 * @param method HTTP method
 * @param path URL path
 * @param query Query parameters
 * @param headers Additional headers
 * @param body Request body
 * @return An endpoint with the inferred response serializer
 */
inline fun <reified Res> endpoint(
    method: HTTPMethod,
    path: String,
    query: Map<String, String> = emptyMap(),
    headers: Map<String, String> = emptyMap(),
    body: Any? = null
): Endpoint<Any, Res> = Endpoint(
    method = method,
    path = path,
    query = query,
    headers = headers,
    body = body,
    responseSerializer = serializer()
)
