package dev.pikaia.android.sdk.auth.interceptor

import dev.pikaia.android.sdk.core.auth.TokenStore
import dev.pikaia.android.sdk.core.networking.interceptors.RequestInterceptor
import io.ktor.client.request.*
import io.ktor.http.*

/**
 * Request interceptor that injects the session JWT as a Bearer authorization header.
 *
 * The backend's auth middleware only accepts `Authorization: Bearer <session_jwt>`;
 * when no session is stored, no header is added and the request proceeds
 * unauthenticated.
 *
 * ## Usage
 *
 * ```kotlin
 * val apiClient = APIClient(
 *     config = config,
 *     requestInterceptors = listOf(AuthTokenInterceptor(tokenStore))
 * )
 * ```
 *
 * @param tokenStore Session storage to read the JWT from
 */
class AuthTokenInterceptor(
    private val tokenStore: TokenStore
) : RequestInterceptor {

    override suspend fun adapt(request: HttpRequestBuilder): HttpRequestBuilder {
        val jwt = tokenStore.getSession()?.sessionJwt
        if (!jwt.isNullOrBlank()) {
            request.headers[HttpHeaders.Authorization] = "Bearer $jwt"
        }
        return request
    }
}
