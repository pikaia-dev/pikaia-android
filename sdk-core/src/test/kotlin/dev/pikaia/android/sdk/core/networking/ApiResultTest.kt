package dev.pikaia.android.sdk.core.networking

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ApiResultTest {

    private val error = APIError.Http(statusCode = 400, detail = "bad request")

    @Test
    fun `apiResult wraps success`() = runTest {
        val result = apiResult { 42 }
        assertEquals(ApiResult.Success(42), result)
        assertTrue(result.isSuccess)
        assertEquals(42, result.getOrNull())
        assertNull(result.errorOrNull())
    }

    @Test
    fun `apiResult catches APIError`() = runTest {
        val result = apiResult<Int> { throw error }
        assertEquals(ApiResult.Failure(error), result)
        assertNull(result.getOrNull())
        assertEquals(error, result.errorOrNull())
    }

    @Test
    fun `apiResult wraps unexpected exceptions as Transport`() = runTest {
        val result = apiResult<Int> { throw IllegalStateException("boom") }
        val wrapped = result.errorOrNull()
        assertTrue(wrapped is APIError.Transport)
        assertEquals("boom", (wrapped as APIError.Transport).cause.message)
    }

    @Test
    fun `getOrThrow rethrows the error`() {
        val thrown = runCatching { ApiResult.Failure(error).getOrThrow() }.exceptionOrNull()
        assertEquals(error, thrown)
    }

    @Test
    fun `map transforms success and passes failure through`() {
        assertEquals(ApiResult.Success("42"), ApiResult.Success(42).map { it.toString() })
        val failure: ApiResult<Int> = ApiResult.Failure(error)
        assertEquals(failure, failure.map { it.toString() })
    }

    @Test
    fun `onSuccess and onFailure fire on the matching side only`() {
        var succeeded: Int? = null
        var failed: APIError? = null

        ApiResult.Success(7).onSuccess { succeeded = it }.onFailure { failed = it }
        assertEquals(7, succeeded)
        assertNull(failed)

        succeeded = null
        ApiResult.Failure(error).onSuccess { succeeded = 1 }.onFailure { failed = it }
        assertNull(succeeded)
        assertEquals(error, failed)
    }
}
