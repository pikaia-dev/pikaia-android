package dev.pikaia.android.sdk.core.networking.interceptors

import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * A hook to process responses after they are received.
 *
 * Response interceptors can log, analyze, or perform side effects based on
 * the response, but cannot modify the response itself.
 */
interface ResponseInterceptor {
    /**
     * Handle the response after it is received.
     *
     * @param response The HTTP response
     * @param data The response body as a string, if available
     * @param request The original HTTP request
     */
    suspend fun handle(response: HttpResponse, data: String?, request: HttpRequest)
}

/**
 * Extension function to apply a list of response interceptors to a response.
 *
 * All interceptors are invoked in order for their side effects.
 *
 * @param response The HTTP response
 * @param data The response body as a string
 * @param request The original HTTP request
 */
suspend fun List<ResponseInterceptor>.handle(
    response: HttpResponse,
    data: String?,
    request: HttpRequest
) {
    forEach { interceptor ->
        interceptor.handle(response, data, request)
    }
}
