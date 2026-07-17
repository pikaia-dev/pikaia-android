package dev.pikaia.android.sdk.core.networking

import kotlin.time.Duration

/**
 * Typed errors thrown by [APIClient] and carried by [ApiResult.Failure].
 *
 * The backend reports errors as a uniform `{"detail": "..."}` body on non-2xx responses;
 * the parsed message is exposed as `detail` where available.
 */
sealed class APIError : Exception() {

    /**
     * The server answered with a non-2xx status code.
     *
     * @param statusCode HTTP status code
     * @param detail Message parsed from the backend's `{"detail": "..."}` error body, if present
     * @param body Raw response body
     * @param metadata Response metadata (request id, headers)
     */
    data class Http(
        val statusCode: Int,
        val detail: String?,
        val body: String? = null,
        val metadata: APIErrorMetadata? = null
    ) : APIError() {
        override val message: String = buildString {
            append("HTTP $statusCode")
            detail?.let { append(": $it") }
            metadata?.requestId?.let { append(" (request id: $it)") }
        }
    }

    /**
     * The server answered 429 Too Many Requests.
     *
     * @param retryAfter Parsed `Retry-After` header, when the server provided one
     * @param detail Message parsed from the backend's `{"detail": "..."}` error body, if present
     * @param metadata Response metadata (request id, headers)
     */
    data class RateLimited(
        val retryAfter: Duration?,
        val detail: String? = null,
        val metadata: APIErrorMetadata? = null
    ) : APIError() {
        override val message: String = buildString {
            append("Rate limited")
            retryAfter?.let { append(", retry after $it") }
            detail?.let { append(": $it") }
        }
    }

    /** The request never produced an HTTP response (connectivity, TLS, timeout). */
    data class Transport(override val cause: Throwable) : APIError() {
        override val message: String = "Network transport error: ${cause.message}"
    }

    /** A 2xx response body could not be decoded into the expected type. */
    data class Decoding(override val cause: Throwable) : APIError() {
        override val message: String = "Failed to decode response: ${cause.message}"
    }

    /** A 401 triggered a session refresh and the refresh itself failed. */
    data class RefreshFailed(override val cause: Throwable) : APIError() {
        override val message: String = "Failed to refresh session: ${cause.message}"
    }
}

/**
 * Metadata extracted from an error response.
 *
 * @param requestId Server-assigned request id (`X-Request-ID`), useful for support/debugging
 * @param headers All response headers
 */
data class APIErrorMetadata(
    val requestId: String?,
    val headers: Map<String, String>
)
