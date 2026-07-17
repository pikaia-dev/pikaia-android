package dev.pikaia.android.sdk.core.networking

/**
 * Errors surfaced by the core API client.
 */
sealed class APIError : Exception() {
    /**
     * The request URL was invalid.
     */
    data class InvalidURL(val url: String) : APIError() {
        override val message: String = "Invalid URL: $url"
    }

    /**
     * Network transport error occurred.
     */
    data class Transport(override val cause: Throwable) : APIError() {
        override val message: String = "Network transport error: ${cause.message}"
    }

    /**
     * Client error (4xx status codes) occurred.
     *
     * @param statusCode HTTP status code
     * @param data Raw response data, if any
     * @param metadata Additional metadata like request ID and headers
     */
    data class Client(
        val statusCode: Int,
        val data: String?,
        val metadata: APIErrorMetadata?
    ) : APIError() {
        override val message: String = buildMessage("Client error", statusCode, metadata)
    }

    /**
     * Server error (5xx status codes) occurred.
     *
     * @param statusCode HTTP status code
     * @param data Raw response data, if any
     * @param metadata Additional metadata like request ID and headers
     */
    data class Server(
        val statusCode: Int,
        val data: String?,
        val metadata: APIErrorMetadata?
    ) : APIError() {
        override val message: String = buildMessage("Server error", statusCode, metadata)
    }

    /**
     * Failed to decode the response.
     */
    data class Decoding(override val cause: Throwable) : APIError() {
        override val message: String = "Failed to decode response: ${cause.message}"
    }

    /**
     * The request was unauthorized (401).
     */
    object Unauthorized : APIError() {
        override val message: String = "The request was unauthorized"
    }

    /**
     * Failed to refresh authentication tokens.
     */
    data class RefreshFailed(override val cause: Throwable) : APIError() {
        override val message: String = "Failed to refresh authentication: ${cause.message}"
    }

    /**
     * The request was cancelled.
     */
    object Cancelled : APIError() {
        override val message: String = "The request was cancelled"
    }

    private companion object {
        fun buildMessage(
            prefix: String,
            statusCode: Int,
            metadata: APIErrorMetadata?
        ): String {
            return if (metadata?.requestId != null) {
                "$prefix with status code $statusCode. Request ID: ${metadata.requestId}"
            } else {
                "$prefix with status code $statusCode"
            }
        }
    }
}

/**
 * Additional metadata returned by the server for diagnostics.
 *
 * @param requestId Unique request identifier from the server
 * @param headers Response headers
 */
data class APIErrorMetadata(
    val requestId: String?,
    val headers: Map<String, String>
)
