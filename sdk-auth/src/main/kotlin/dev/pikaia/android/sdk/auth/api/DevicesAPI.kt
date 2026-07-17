package dev.pikaia.android.sdk.auth.api

import dev.pikaia.android.sdk.auth.data.devices.CompleteLinkRequest
import dev.pikaia.android.sdk.auth.data.devices.CompleteLinkResponse
import dev.pikaia.android.sdk.auth.data.devices.DeviceListResponse
import dev.pikaia.android.sdk.auth.data.devices.DeviceSessionRefreshRequest
import dev.pikaia.android.sdk.auth.data.devices.DeviceSessionRefreshResponse
import dev.pikaia.android.sdk.auth.data.devices.InitiateLinkResponse
import dev.pikaia.android.sdk.core.networking.APIClient
import dev.pikaia.android.sdk.core.networking.ApiResult
import dev.pikaia.android.sdk.core.networking.EmptyResponse
import dev.pikaia.android.sdk.core.networking.HTTPMethod
import dev.pikaia.android.sdk.core.networking.endpoint
import dev.pikaia.android.sdk.core.networking.map

/**
 * Devices API client covering the backend's device-linking surface.
 *
 * Device linking is how mobile clients obtain long-lived refreshable sessions:
 * an authenticated (web) client initiates a link and renders the returned QR code;
 * the mobile app scans it, extracts the token from the product-specific deep link,
 * and completes the link to receive session credentials. The session JWT is later
 * renewed via [refreshSession].
 *
 * Paths are relative — the [APIClient]'s `baseUrl` must carry the backend's API prefix
 * (e.g. `https://api.example.com/api/v1`).
 *
 * @param client The underlying API client for making HTTP requests
 */
class DevicesAPI(private val client: APIClient) {

    /**
     * Initiate device linking.
     *
     * Generates QR code data for linking a mobile device. Requires authentication.
     * The returned `qr_url` uses the product's own deep-link scheme; the SDK does not
     * interpret it.
     *
     * @return QR code payload and its expiry
     */
    suspend fun initiateLink(): ApiResult<InitiateLinkResponse> {
        val endpoint = endpoint<InitiateLinkResponse>(
            method = HTTPMethod.POST,
            path = "devices/link/initiate"
        )
        return client.sendResult(endpoint)
    }

    /**
     * Complete device linking using the token from a scanned QR code.
     *
     * Public endpoint (rate-limited by the backend). On success the device receives
     * a long-lived session.
     *
     * @param request Link token plus device identity and metadata
     * @return Session credentials and device/user identifiers
     */
    suspend fun completeLink(request: CompleteLinkRequest): ApiResult<CompleteLinkResponse> {
        val endpoint = endpoint<CompleteLinkRequest, CompleteLinkResponse>(
            method = HTTPMethod.POST,
            path = "devices/link/complete",
            body = request
        )
        return client.sendResult(endpoint)
    }

    /**
     * List the authenticated user's linked devices.
     *
     * @return Linked devices and their count
     */
    suspend fun listDevices(): ApiResult<DeviceListResponse> {
        val endpoint = endpoint<DeviceListResponse>(
            method = HTTPMethod.GET,
            path = "devices/"
        )
        return client.sendResult(endpoint)
    }

    /**
     * Revoke a linked device, preventing it from refreshing its session.
     *
     * @param deviceId Server-side device ID (from [listDevices] or link completion)
     * @return Success with no content (the backend answers 204)
     */
    suspend fun revokeDevice(deviceId: Long): ApiResult<Unit> {
        val endpoint = endpoint<EmptyResponse>(
            method = HTTPMethod.DELETE,
            path = "devices/$deviceId"
        )
        return client.sendResult(endpoint).map { }
    }

    /**
     * Refresh the device session, obtaining fresh session tokens.
     *
     * Requires authentication with the current (possibly just-expired) session JWT —
     * the backend validates the underlying session server-side.
     *
     * @param request The device UUID used when the device was linked
     * @return New session tokens
     */
    suspend fun refreshSession(
        request: DeviceSessionRefreshRequest
    ): ApiResult<DeviceSessionRefreshResponse> {
        val endpoint = endpoint<DeviceSessionRefreshRequest, DeviceSessionRefreshResponse>(
            method = HTTPMethod.POST,
            path = "devices/session/refresh",
            body = request
        )
        return client.sendResult(endpoint)
    }
}
