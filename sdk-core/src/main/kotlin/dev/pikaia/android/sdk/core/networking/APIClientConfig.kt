package dev.pikaia.android.sdk.core.networking

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the core API client.
 *
 * @param baseUrl Base URL for all API requests (e.g., "https://api.example.com")
 * @param defaultHeaders Default headers to include in all requests
 * @param timeout Request timeout duration
 * @param enableLogging Whether to enable request/response logging
 */
data class APIClientConfig(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = mapOf("Content-Type" to "application/json"),
    val timeout: Duration = 30.seconds,
    val enableLogging: Boolean = false
)
