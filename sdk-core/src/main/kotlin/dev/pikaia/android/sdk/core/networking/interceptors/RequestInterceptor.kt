package dev.pikaia.android.sdk.core.networking.interceptors

import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * A hook to adapt requests before they are sent.
 *
 * Request interceptors can modify the request (e.g., add headers, modify URL)
 * and optionally determine if a failed request should be retried.
 */
interface RequestInterceptor {
    /**
     * Adapt the request before it is sent.
     *
     * @param request The request builder to modify
     * @return The modified request builder
     */
    suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder

    /**
     * Determine if a failed request should be retried.
     *
     * @param response The HTTP response
     * @param data The response body as a string, if available
     * @return True if the request should be retried, false otherwise
     */
    suspend fun shouldRetry(response: HttpResponse, data: String?): Boolean = false
}

/**
 * Extension function to apply a list of request interceptors to a request.
 *
 * Interceptors are applied in order, with each interceptor's output
 * becoming the input for the next.
 *
 * @param request The initial request builder
 * @return The request builder after all interceptors have been applied
 */
suspend fun List<RequestInterceptor>.adapt(
    request: HttpRequestBuilder
): HttpRequestBuilder {
    return fold(request) { req, interceptor ->
        interceptor.adapt(req)
    }
}
