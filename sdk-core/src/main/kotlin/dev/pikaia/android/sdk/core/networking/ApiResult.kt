package dev.pikaia.android.sdk.core.networking

import kotlinx.coroutines.CancellationException

/**
 * Non-throwing outcome of an API call: [Success] with the decoded value, or [Failure]
 * with a typed [APIError].
 *
 * API surfaces built on sdk-core return [ApiResult] instead of throwing, so call sites
 * handle errors exhaustively:
 *
 * ```kotlin
 * when (val result = authAPI.getMe()) {
 *     is ApiResult.Success -> render(result.value)
 *     is ApiResult.Failure -> when (val error = result.error) {
 *         is APIError.RateLimited -> scheduleRetry(error.retryAfter)
 *         else -> showError(error.message)
 *     }
 * }
 * ```
 */
sealed interface ApiResult<out T> {

    /** The call succeeded with [value]. */
    data class Success<out T>(val value: T) : ApiResult<T>

    /** The call failed with a typed [error]. */
    data class Failure(val error: APIError) : ApiResult<Nothing>

    /** The value on success, or null on failure. */
    fun getOrNull(): T? = (this as? Success)?.value

    /** The error on failure, or null on success. */
    fun errorOrNull(): APIError? = (this as? Failure)?.error

    /** The value on success; throws the [APIError] on failure. */
    fun getOrThrow(): T = when (this) {
        is Success -> value
        is Failure -> throw error
    }

    val isSuccess: Boolean get() = this is Success
}

/** Transform the success value, passing failures through unchanged. */
inline fun <T, R> ApiResult<T>.map(transform: (T) -> R): ApiResult<R> = when (this) {
    is ApiResult.Success -> ApiResult.Success(transform(value))
    is ApiResult.Failure -> this
}

/** Run [action] when the result is a success. Returns the result for chaining. */
inline fun <T> ApiResult<T>.onSuccess(action: (T) -> Unit): ApiResult<T> {
    if (this is ApiResult.Success) action(value)
    return this
}

/** Run [action] when the result is a failure. Returns the result for chaining. */
inline fun <T> ApiResult<T>.onFailure(action: (APIError) -> Unit): ApiResult<T> {
    if (this is ApiResult.Failure) action(error)
    return this
}

/**
 * Run [block] and fold its outcome into an [ApiResult].
 *
 * [APIError]s become [ApiResult.Failure], coroutine cancellation propagates, and any
 * other unexpected exception is wrapped as [APIError.Transport].
 */
suspend inline fun <T> apiResult(crossinline block: suspend () -> T): ApiResult<T> = try {
    ApiResult.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: APIError) {
    ApiResult.Failure(e)
} catch (e: Exception) {
    ApiResult.Failure(APIError.Transport(e))
}
