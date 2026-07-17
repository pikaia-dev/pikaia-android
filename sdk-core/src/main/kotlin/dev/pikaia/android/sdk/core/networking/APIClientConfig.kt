package dev.pikaia.android.sdk.core.networking

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Configuration for the core API client.
 *
 * @param baseUrl Base URL for all API requests, including any API prefix the backend is
 *   mounted under (e.g. "https://api.example.com/api/v1"). Endpoint paths are relative to it.
 * @param defaultHeaders Default headers to include in all requests. `Content-Type` is
 *   managed by the client itself and ignored here.
 * @param timeout Request timeout duration
 * @param enableLogging Whether to enable request/response logging
 */
data class APIClientConfig(
    val baseUrl: String,
    val defaultHeaders: Map<String, String> = emptyMap(),
    val timeout: Duration = 30.seconds,
    val enableLogging: Boolean = false
)
