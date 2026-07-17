package dev.pikaia.android.sdk.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dev.pikaia.android.sdk.sync.engine.SyncEngine

/**
 * WorkManager worker for background sync operations.
 *
 * This worker is scheduled by WorkManager to perform periodic or one-time syncs
 * even when the app is not in the foreground.
 *
 * Applications must provide a SyncEngine instance via SyncWorkerProvider.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "SyncWorker started")

        val syncEngine = SyncWorkerProvider.syncEngine
        if (syncEngine == null) {
            Log.e(TAG, "SyncEngine not provided to SyncWorkerProvider")
            return Result.failure()
        }

        return try {
            // Perform sync
            val result = syncEngine.sync()

            Log.i(TAG, "SyncWorker completed: pushed=${result.pushedCount}, pulled=${result.pulledCount}, applied=${result.appliedCount}, failed=${result.failedCount}")

            // Consider it a success even if some changes failed to apply
            // (individual failures are logged separately)
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "SyncWorker failed", e)

            // Retry on failure if we haven't exceeded max retries
            if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
                Log.i(TAG, "Will retry (attempt ${runAttemptCount + 1}/$MAX_RETRY_ATTEMPTS)")
                Result.retry()
            } else {
                Log.w(TAG, "Max retry attempts reached")
                Result.failure()
            }
        }
    }

    companion object {
        private const val TAG = "SyncWorker"
        private const val MAX_RETRY_ATTEMPTS = 3

        /**
         * Unique name for periodic sync work.
         */
        const val PERIODIC_SYNC_WORK_NAME = "pikaia_periodic_sync"

        /**
         * Unique name for one-time sync work.
         */
        const val ONE_TIME_SYNC_WORK_NAME = "pikaia_one_time_sync"
    }
}

/**
 * Provider for SyncEngine dependency injection.
 *
 * Applications must set the SyncEngine instance before scheduling work:
 *
 * ```
 * SyncWorkerProvider.syncEngine = syncEngine
 * ```
 */
object SyncWorkerProvider {
    var syncEngine: SyncEngine? = null
}
