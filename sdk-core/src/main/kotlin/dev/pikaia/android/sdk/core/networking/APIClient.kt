package dev.pikaia.android.sdk.core.networking

import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.RefreshCoordinator
import dev.pikaia.android.sdk.core.auth.TokenStore
import dev.pikaia.android.sdk.core.networking.interceptors.RequestInterceptor
import dev.pikaia.android.sdk.core.networking.interceptors.ResponseInterceptor
import dev.pikaia.android.sdk.core.networking.interceptors.adapt
import dev.pikaia.android.sdk.core.networking.interceptors.handle
import io.ktor.client.*
import io.ktor.client.engine.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Duration.Companion.seconds

/**
 * Executes API endpoints using Ktor with optional authentication handling.
 *
 * The client automatically handles:
 * - Session refresh on 401 responses (retry once, never loops)
 * - Request/response interceptor pipelines
 * - JSON encoding/decoding with ISO8601 dates
 * - Typed errors, including 429 with `Retry-After`
 *
 * @param config Configuration for the API client
 * @param requestInterceptors List of request interceptors to apply
 * @param responseInterceptors List of response interceptors to apply
 * @param tokenStore Optional session storage for authentication
 * @param authProvider Optional auth provider for session refresh
 * @param refreshCoordinator Coordinator that coalesces concurrent session refreshes
 * @param engine Optional Ktor engine, injectable for tests (e.g. MockEngine)
 */
class APIClient(
    private val config: APIClientConfig,
    private val requestInterceptors: List<RequestInterceptor> = emptyList(),
    private val responseInterceptors: List<ResponseInterceptor> = emptyList(),
    private val tokenStore: TokenStore? = null,
    private val authProvider: AuthProvider? = null,
    private val refreshCoordinator: RefreshCoordinator? = null,
    engine: HttpClientEngine? = null
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
    }

    private val clientSetup: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = config.timeout.inWholeMilliseconds
            connectTimeoutMillis = config.timeout.inWholeMilliseconds
            socketTimeoutMillis = config.timeout.inWholeMilliseconds
        }
    }

    private val httpClient: HttpClient =
        if (engine != null) HttpClient(engine, clientSetup) else HttpClient(clientSetup)

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
     * Non-throwing variant of [send].
     *
     * @param endpoint The endpoint definition
     * @return [ApiResult.Success] with the decoded response, or [ApiResult.Failure]
     */
    suspend fun <Req, Res> sendResult(endpoint: Endpoint<Req, Res>): ApiResult<Res> =
        apiResult { send(endpoint) }

    /**
     * Internal send method with refresh control.
     */
    private suspend fun <Req, Res> send(
        endpoint: Endpoint<Req, Res>,
        allowRefresh: Boolean
    ): Res {
        val response: HttpResponse
        val data: String
        try {
            val requestBuilder = buildRequest(endpoint)
            val adaptedRequest = requestInterceptors.adapt(requestBuilder)
            response = httpClient.request(adaptedRequest)
            data = response.bodyAsText()
            responseInterceptors.handle(response, data, response.request)
        } catch (e: CancellationException) {
            throw e
        } catch (e: APIError) {
            throw e
        } catch (e: Exception) {
            throw APIError.Transport(e)
        }

        return when {
            response.status.value in 200..299 -> {
                try {
                    decodeResponse(endpoint, data)
                } catch (e: Exception) {
                    throw APIError.Decoding(e)
                }
            }
            response.status.value == 401 -> handleUnauthorized(endpoint, allowRefresh, response, data)
            response.status.value == 429 -> throw APIError.RateLimited(
                retryAfter = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.seconds,
                detail = parseDetail(data),
                metadata = extractMetadata(response)
            )
            else -> throw APIError.Http(
                statusCode = response.status.value,
                detail = parseDetail(data),
                body = data,
                metadata = extractMetadata(response)
            )
        }
    }

    /**
     * Handle an unauthorized (401) response by refreshing the session and retrying once.
     */
    private suspend fun <Req, Res> handleUnauthorized(
        endpoint: Endpoint<Req, Res>,
        allowRefresh: Boolean,
        response: HttpResponse,
        data: String
    ): Res {
        val store = tokenStore
        val provider = authProvider
        val coordinator = refreshCoordinator
        if (!allowRefresh || store == null || provider == null || coordinator == null) {
            throw unauthorizedError(response, data)
        }

        val session = store.getSession() ?: throw unauthorizedError(response, data)

        val refreshed = try {
            coordinator.refresh(session, provider)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw APIError.RefreshFailed(e)
        }

        store.setSession(refreshed)

        // Retry the request once, with refresh disabled to prevent a refresh loop
        return send(endpoint, allowRefresh = false)
    }

    private fun unauthorizedError(response: HttpResponse, data: String) = APIError.Http(
        statusCode = HttpStatusCode.Unauthorized.value,
        detail = parseDetail(data),
        body = data,
        metadata = extractMetadata(response)
    )

    /**
     * Build an HTTP request from an endpoint definition.
     */
    private fun <Req, Res> buildRequest(
        endpoint: Endpoint<Req, Res>
    ): HttpRequestBuilder {
        return HttpRequestBuilder().apply {
            method = endpoint.toKtorMethod()

            url {
                takeFrom(config.baseUrl)
                appendPathSegments(endpoint.path.removePrefix("/").split("/"))
                endpoint.query.forEach { (key, value) ->
                    parameters.append(key, value)
                }
            }

            // Content-Type travels with the body content; Ktor rejects it as a raw header
            (config.defaultHeaders + endpoint.headers).forEach { (key, value) ->
                if (!key.equals(HttpHeaders.ContentType, ignoreCase = true)) {
                    headers[key] = value
                }
            }

            val body = endpoint.body
            val serializer = endpoint.requestSerializer
            if (body != null && serializer != null) {
                setBody(TextContent(json.encodeToString(serializer, body), ContentType.Application.Json))
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
        if (endpoint.responseSerializer.descriptor.serialName ==
            EmptyResponse.serializer().descriptor.serialName
        ) {
            @Suppress("UNCHECKED_CAST")
            return EmptyResponse as Res
        }

        check(data.isNotBlank()) { "Expected response body but got an empty one" }

        return json.decodeFromString(endpoint.responseSerializer, data)
    }

    /**
     * Parse the backend's uniform `{"detail": "..."}` error body.
     */
    private fun parseDetail(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            json.parseToJsonElement(body).jsonObject["detail"]?.jsonPrimitive?.contentOrNull
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extract metadata from the HTTP response.
     */
    private fun extractMetadata(response: HttpResponse): APIErrorMetadata {
        val headers = mutableMapOf<String, String>()
        response.headers.forEach { key, values ->
            headers[key] = values.joinToString(", ")
        }

        val requestId = response.headers["X-Request-Id"]
            ?: response.headers["X-Request-ID"]
            ?: response.headers["Request-Id"]

        return APIErrorMetadata(
            requestId = requestId,
            headers = headers
        )
    }
}
