package dev.pikaia.android.sdk.auth.interceptor

import dev.pikaia.android.sdk.auth.token.InMemoryTokenStore
import dev.pikaia.android.sdk.core.auth.AuthSession
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AuthTokenInterceptorTest {

    @Test
    fun `injects the session jwt as bearer header`() = runTest {
        val store = InMemoryTokenStore()
        store.setSession(AuthSession("jwt-1", "st-1"))
        val builder = HttpRequestBuilder()

        AuthTokenInterceptor(store).adapt(builder)

        assertEquals("Bearer jwt-1", builder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `adds no header when no session is stored`() = runTest {
        val builder = HttpRequestBuilder()

        AuthTokenInterceptor(InMemoryTokenStore()).adapt(builder)

        assertNull(builder.headers[HttpHeaders.Authorization])
    }

    @Test
    fun `replaces a stale authorization header`() = runTest {
        val store = InMemoryTokenStore()
        store.setSession(AuthSession("jwt-2", "st-1"))
        val builder = HttpRequestBuilder()
        builder.headers[HttpHeaders.Authorization] = "Bearer stale"

        AuthTokenInterceptor(store).adapt(builder)

        assertEquals("Bearer jwt-2", builder.headers[HttpHeaders.Authorization])
    }
}
