package dev.pikaia.android.sdk.auth.interceptor

import dev.pikaia.android.sdk.core.auth.TokenStore
import dev.pikaia.android.sdk.core.networking.interceptors.RequestInterceptor
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Request interceptor that automatically injects authentication headers.
 *
 * This interceptor supports both Bearer token authentication (for regular users)
 * and DeviceToken authentication (for shadow/unverified users). The mode
 * determines which credentials to use and in what order.
 *
 * ## Authentication Modes:
 *
 * - **BEARER_ONLY**: Only use Bearer tokens (access token). Falls back to nothing if not available.
 * - **DEVICE_ONLY**: Only use DeviceToken. Falls back to nothing if not available.
 * - **PREFER_BEARER**: Try Bearer first, fall back to DeviceToken if Bearer not available.
 * - **PREFER_DEVICE**: Try DeviceToken first, fall back to Bearer if DeviceToken not available.
 *
 * ## Usage:
 *
 * ```kotlin
 * val apiClient = APIClient(
 *     config = config,
 *     requestInterceptors = listOf(
 *         AuthTokenInterceptor(
 *             tokenStore = tokenStore,
 *             mode = AuthTokenInterceptor.Mode.PREFER_BEARER
 *         )
 *     )
 * )
 * ```
 *
 * @param tokenStore Token storage to retrieve credentials from
 * @param mode Authentication mode determining credential selection strategy
 */
class AuthTokenInterceptor(
    private val tokenStore: TokenStore,
    private val mode: Mode = Mode.PREFER_BEARER
) : RequestInterceptor {

    /**
     * Authentication mode for credential selection.
     */
    enum class Mode {
        /**
         * Only use Bearer token authentication.
         * Does not inject any header if Bearer token is not available.
         */
        BEARER_ONLY,

        /**
         * Only use DeviceToken authentication.
         * Does not inject any header if DeviceToken is not available.
         */
        DEVICE_ONLY,

        /**
         * Prefer Bearer token, fall back to DeviceToken.
         * This is the default and recommended mode for most apps.
         */
        PREFER_BEARER,

        /**
         * Prefer DeviceToken, fall back to Bearer token.
         * Useful for apps with offline-first shadow user support.
         */
        PREFER_DEVICE
    }

    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        // Get credentials from token store
        val bearerCredential = tokenStore.getBearerCredential()
        val deviceCredential = tokenStore.getDeviceCredential()

        // Select credential based on mode
        val credential = selectCredential(bearerCredential, deviceCredential)

        // Inject Authorization header if credential is available
        credential?.let {
            request.header(HttpHeaders.Authorization, "${it.scheme} ${it.token}")
        }

        return request
    }

    /**
     * Select the appropriate credential based on the configured mode.
     *
     * @param bearer Bearer credential (if available)
     * @param device Device credential (if available)
     * @return The selected credential, or null if no suitable credential
     */
    private fun selectCredential(
        bearer: Credential?,
        device: Credential?
    ): Credential? {
        return when (mode) {
            Mode.BEARER_ONLY -> bearer
            Mode.DEVICE_ONLY -> device
            Mode.PREFER_BEARER -> bearer ?: device
            Mode.PREFER_DEVICE -> device ?: bearer
        }
    }

    /**
     * Represents authentication credentials.
     *
     * @param scheme Authorization scheme (e.g., "Bearer", "DeviceToken")
     * @param token The authentication token
     */
    private data class Credential(
        val scheme: String,
        val token: String
    )

    /**
     * Extension function to get Bearer credentials from token store.
     */
    private suspend fun TokenStore.getBearerCredential(): Credential? {
        val token = getAccessToken() ?: return null
        return if (token.isNotBlank()) {
            Credential(scheme = "Bearer", token = token)
        } else {
            null
        }
    }

    /**
     * Extension function to get Device credentials from token store.
     */
    private suspend fun TokenStore.getDeviceCredential(): Credential? {
        val token = getDeviceToken() ?: return null
        return if (token.isNotBlank()) {
            Credential(scheme = "DeviceToken", token = token)
        } else {
            null
        }
    }
}
