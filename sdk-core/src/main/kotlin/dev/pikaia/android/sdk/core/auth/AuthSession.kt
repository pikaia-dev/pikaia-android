package dev.pikaia.android.sdk.core.auth

import kotlin.time.Instant

/**
 * An authenticated backend session.
 *
 * The backend issues Stytch-based sessions consisting of a short-lived JWT used as the
 * bearer credential and an opaque session token identifying the underlying long-lived
 * session. Device-linked sessions additionally carry the device UUID required to refresh
 * the session via `POST devices/session/refresh`.
 *
 * @param sessionJwt Short-lived JWT sent as `Authorization: Bearer <sessionJwt>`
 * @param sessionToken Opaque session token identifying the long-lived session
 * @param sessionExpiresAt When the underlying session fully expires (re-authentication required)
 * @param deviceUuid Device identity for device-linked sessions; required for session refresh
 */
data class AuthSession(
    val sessionJwt: String,
    val sessionToken: String,
    val sessionExpiresAt: Instant? = null,
    val deviceUuid: String? = null
)
