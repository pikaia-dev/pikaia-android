package dev.pikaia.android.sdk.core.networking.interceptors

import android.util.Log
import io.ktor.client.request.*

/**
 * Request interceptor that logs outgoing requests.
 *
 * Logs the HTTP method, URL, and headers (excluding sensitive headers like Authorization).
 *
 * @param tag Log tag to use (default: "PikaiaSDK")
 */
class LogRequestInterceptor(
    private val tag: String = "PikaiaSDK"
) : RequestInterceptor {

    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        Log.d(tag, "→ ${request.method.value} ${request.url.buildString()}")

        request.headers.entries().forEach { (key, values) ->
            if (key.lowercase() != "authorization") {
                Log.d(tag, "  $key: ${values.joinToString()}")
            } else {
                Log.d(tag, "  $key: [REDACTED]")
            }
        }

        return request
    }
}
