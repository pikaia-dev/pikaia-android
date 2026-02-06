package dev.pikaia.android.sdk.core.networking.interceptors

import android.util.Log
import io.ktor.client.request.*
import io.ktor.client.statement.*

/**
 * Response interceptor that logs incoming responses.
 *
 * Logs the HTTP status code, method, URL, and response body (truncated to 200 characters).
 *
 * @param tag Log tag to use (default: "PikaiaSDK")
 */
class LogResponseInterceptor(
    private val tag: String = "PikaiaSDK"
) : ResponseInterceptor {

    override suspend fun handle(
        response: HttpResponse,
        data: String?,
        request: HttpRequest
    ) {
        Log.d(tag, "← ${response.status.value} ${request.method.value} ${request.url}")

        data?.let {
            val truncated = if (it.length > 200) "${it.take(200)}..." else it
            Log.d(tag, "  Body: $truncated")
        }
    }
}
