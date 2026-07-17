package dev.pikaia.android.sdk.core.networking

/**
 * HTTP methods supported by the API client.
 */
enum class HTTPMethod(val value: String) {
    GET("GET"),
    POST("POST"),
    PUT("PUT"),
    PATCH("PATCH"),
    DELETE("DELETE");

    override fun toString(): String = value
}
