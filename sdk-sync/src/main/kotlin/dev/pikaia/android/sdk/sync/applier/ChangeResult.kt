package dev.pikaia.android.sdk.sync.applier

/**
 * Result of applying a single change.
 */
sealed class ChangeResult {
    /**
     * Change was successfully applied.
     */
    object Success : ChangeResult()

    /**
     * Change failed to apply.
     *
     * @param error Error message
     * @param exception Optional exception
     */
    data class Failure(
        val error: String,
        val exception: Throwable? = null
    ) : ChangeResult()
}
