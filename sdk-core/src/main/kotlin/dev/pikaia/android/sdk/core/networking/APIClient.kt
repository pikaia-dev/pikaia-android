package dev.pikaia.android.sdk.core.networking

import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.RefreshCoordinator
import dev.pikaia.android.sdk.core.auth.TokenStore
import dev.pikaia.android.sdk.core.networking.interceptors.RequestInterceptor
import dev.pikaia.android.sdk.core.networking.interceptors.ResponseInterceptor
import dev.pikaia.android.sdk.core.networking.interceptors.adapt
import dev.pikaia.android.sdk.core.networking.interceptors.handle
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

/**
 * Executes API endpoints using Ktor with optional authentication handling.
 *
 * The client automatically handles:
 * - Token refresh on 401 responses
 * - Request/response interceptor pipelines
 * - JSON encoding/decoding with ISO8601 dates
 * - Error handling with metadata
 *
 * @param config Configuration for the API client
 * @param httpClient The Ktor HTTP client instance
 * @param requestInterceptors List of request interceptors to apply
 * @param responseInterceptors List of response interceptors to apply
 * @param tokenStore Optional token storage for authentication
 * @param authProvider Optional auth provider for token refresh
 * @param refreshCoordinator Coordinator for token refresh operations
 */
class APIClient(
    private val config: APIClientConfig,
    private val requestInterceptors: List<RequestInterceptor> = emptyList(),
    private val responseInterceptors: List<ResponseInterceptor> = emptyList(),
    private val tokenStore: TokenStore? = null,
    private val authProvider: AuthProvider? = null,
    private val refreshCoordinator: RefreshCoordinator? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val httpClient = HttpClient {
        install(ContentNegotiation) { json() }
    }

    /**
     * Send an HTTP request and return the decoded response.
     *
     * @param endpoint The endpoint definition
     * @return The decoded response
     * @throws APIError if the request fails
     */
    suspend fun <Req, Res> send(endpoint: Endpoint<Req, Res>): Res {
        return send(endpoint, allowRefresh = true)
    }

    /**
     * Internal send method with refresh control.
     *
     * @param endpoint The endpoint definition
     * @param allowRefresh Whether to allow token refresh on 401
     * @return The decoded response
     */
    private suspend fun <Req, Res> send(
        endpoint: Endpoint<Req, Res>,
        allowRefresh: Boolean
    ): Res {
        // Build the request
        val requestBuilder = buildRequest(endpoint)

        // Apply request interceptors
        val adaptedRequest = requestInterceptors.adapt(requestBuilder)

        try {
            // Execute the request
            val response: HttpResponse = httpClient.request(adaptedRequest)
            val data = response.bodyAsText()

            // Apply response interceptors
            responseInterceptors.handle(response, data, response.request)

            // Handle response based on status code
            return when (response.status.value) {
                in 200..299 -> {
                    // Success - decode response
                    try {
                        decodeResponse(endpoint, data)
                    } catch (e: Exception) {
                        throw APIError.Decoding(e)
                    }
                }
                401 -> {
                    // Unauthorized - attempt token refresh if allowed
                    handleUnauthorized(endpoint, allowRefresh)
                }
                in 400..499 -> {
                    // Client error
                    throw APIError.Client(
                        statusCode = response.status.value,
                        data = data,
                        metadata = extractMetadata(response)
                    )
                }
                else -> {
                    // Server error
                    throw APIError.Server(
                        statusCode = response.status.value,
                        data = data,
                        metadata = extractMetadata(response)
                    )
                }
            }
        } catch (e: CancellationException) {
            throw APIError.Cancelled
        } catch (e: APIError) {
            // Re-throw API errors
            throw e
        } catch (e: Exception) {
            // Wrap other exceptions as transport errors
            throw APIError.Transport(e)
        }
    }

    /**
     * Handle unauthorized (401) response by attempting token refresh.
     */
    private suspend fun <Req, Res> handleUnauthorized(
        endpoint: Endpoint<Req, Res>,
        allowRefresh: Boolean
    ): Res {
        if (!allowRefresh) {
            throw APIError.Unauthorized
        }

        val store = tokenStore ?: throw APIError.Unauthorized
        val provider = authProvider ?: throw APIError.Unauthorized
        val coordinator = refreshCoordinator ?: throw APIError.Unauthorized

        val refreshToken = store.getRefreshToken() ?: throw APIError.Unauthorized

        // Attempt token refresh
        val (newAccessToken, newRefreshToken) = try {
            coordinator.refresh(refreshToken, provider)
        } catch (e: Exception) {
            throw APIError.RefreshFailed(e)
        }

        // Update tokens in store
        val updatedRefreshToken = newRefreshToken ?: refreshToken
        store.setTokens(newAccessToken, updatedRefreshToken)

        // Retry the request once (with refresh disabled to prevent infinite loop)
        return send(endpoint, allowRefresh = false)
    }

    /**
     * Build an HTTP request from an endpoint definition.
     */
    private fun <Req, Res> buildRequest(
        endpoint: Endpoint<Req, Res>
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            // Set method
            method = endpoint.toKtorMethod()

            // Build URL
            url {
                takeFrom(config.baseUrl)

                // Append path (remove leading slash if present)
                val cleanPath = endpoint.path.removePrefix("/")
                appendPathSegments(cleanPath.split("/"))

                // Add query parameters
                endpoint.query.forEach { (key, value) ->
                    parameters.append(key, value)
                }
            }

            // Set default headers from config
            config.defaultHeaders.forEach { (key, value) ->
                headers.append(key, value)
            }

            // Set endpoint-specific headers (override defaults)
            endpoint.headers.forEach { (key, value) ->
                headers[key] = value
            }

            // Set body if present
            endpoint.body?.let { body ->
                if (body !is EmptyBody) {
                    setBody(body)
                    // Ensure Content-Type is set
                    if (headers[HttpHeaders.ContentType] == null) {
                        headers[HttpHeaders.ContentType] = "application/json"
                    }
                }
            }
        }
    }

    /**
     * Decode the response body into the expected type.
     */
    private fun <Req, Res> decodeResponse(
        endpoint: Endpoint<Req, Res>,
        data: String
    ): Res {
        // Handle empty response
        if (endpoint.responseSerializer.descriptor.serialName == "dev.pikaia.android.sdk.core.networking.EmptyResponse") {
            @Suppress("UNCHECKED_CAST")
            return EmptyResponse as Res
        }

        // Handle empty data for non-empty response types
        if (data.isEmpty() || data.isBlank()) {
            throw IllegalStateException("Expected response data but got empty body")
        }

        // Decode JSON
        return json.decodeFromString(endpoint.responseSerializer, data)
    }

    /**
     * Extract metadata from the HTTP response.
     */
    private fun extractMetadata(response: HttpResponse): APIErrorMetadata {
        val headers = mutableMapOf<String, String>()

        // Convert headers to a simple map
        response.headers.forEach { key, values ->
            headers[key] = values.joinToString(", ")
        }

        // Extract request ID (check common header names)
        val requestId = response.headers["X-Request-Id"]
            ?: response.headers["X-Request-ID"]
            ?: response.headers["Request-Id"]

        return APIErrorMetadata(
            requestId = requestId,
            headers = headers
        )
    }
}
