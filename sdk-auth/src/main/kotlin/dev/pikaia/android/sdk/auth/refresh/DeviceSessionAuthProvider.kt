package dev.pikaia.android.sdk.auth.refresh

import dev.pikaia.android.sdk.auth.api.DevicesAPI
import dev.pikaia.android.sdk.auth.data.devices.DeviceSessionRefreshRequest
import dev.pikaia.android.sdk.core.auth.AuthProvider
import dev.pikaia.android.sdk.core.auth.AuthSession

/**
 * [AuthProvider] for device-linked sessions, refreshing via `POST devices/session/refresh`.
 *
 * The refresh call authenticates with the current session JWT — the backend validates the
 * underlying long-lived session server-side, so a just-expired JWT still refreshes as long
 * as the session itself is alive. When the refresh fails, the session is gone for good
 * (revoked or fully expired) and the user must re-link the device.
 *
 * ## Assembly
 *
 * The [devicesAPI] passed here must be built on an [dev.pikaia.android.sdk.core.networking.APIClient]
 * **without** an `authProvider`/`refreshCoordinator` (so a failing refresh can never
 * trigger a recursive refresh) but **with** an `AuthTokenInterceptor` sharing the app's
 * `TokenStore` (so the refresh request carries the current bearer credential):
 *
 * ```kotlin
 * val refreshClient = APIClient(
 *     config = config,
 *     requestInterceptors = listOf(AuthTokenInterceptor(tokenStore))
 * )
 * val authProvider = DeviceSessionAuthProvider(DevicesAPI(refreshClient))
 * ```
 *
 * @param devicesAPI Devices API on a refresh-free client (see above)
 */
class DeviceSessionAuthProvider(
    private val devicesAPI: DevicesAPI
) : AuthProvider {

    override suspend fun refreshSession(current: AuthSession): AuthSession {
        val deviceUuid = current.deviceUuid ?: throw IllegalStateException(
            "Stored session has no device UUID; only device-linked sessions can be refreshed"
        )

        val response = devicesAPI
            .refreshSession(DeviceSessionRefreshRequest(deviceUuid))
            .getOrThrow()

        return AuthSession(
            sessionJwt = response.sessionJwt,
            sessionToken = response.sessionToken,
            sessionExpiresAt = response.sessionExpiresAt,
            deviceUuid = deviceUuid
        )
    }
}
