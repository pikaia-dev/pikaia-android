package dev.pikaia.android.sdk.sync.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * Helper for scheduling background sync work with WorkManager.
 *
 * Provides methods to schedule periodic and one-time sync operations.
 *
 * Example usage:
 * ```
 * val scheduler = SyncWorkScheduler(context)
 *
 * // Schedule periodic sync every 15 minutes
 * scheduler.schedulePeriodicSync(intervalMinutes = 15)
 *
 * // Schedule one-time sync immediately
 * scheduler.scheduleOneTimeSync()
 *
 * // Cancel all sync work
 * scheduler.cancelAllSync()
 * ```
 */
class SyncWorkScheduler(private val context: Context) {

    private val workManager = WorkManager.getInstance(context)

    /**
     * Schedule periodic background sync.
     *
     * @param intervalMinutes Interval between syncs in minutes (minimum 15)
     * @param requiresNetwork Whether to require network connectivity (default true)
     * @param requiresCharging Whether to require device charging (default false)
     * @param policy Policy for handling existing periodic work (default KEEP)
     */
    fun schedulePeriodicSync(
        intervalMinutes: Long = DEFAULT_INTERVAL_MINUTES,
        requiresNetwork: Boolean = true,
        requiresCharging: Boolean = false,
        policy: ExistingPeriodicWorkPolicy = ExistingPeriodicWorkPolicy.KEEP
    ) {
        require(intervalMinutes >= MIN_INTERVAL_MINUTES) {
            "Interval must be at least $MIN_INTERVAL_MINUTES minutes"
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .setRequiresCharging(requiresCharging)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            intervalMinutes,
            TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.PERIODIC_SYNC_WORK_NAME,
            policy,
            workRequest
        )

        Log.i(TAG, "Scheduled periodic sync every $intervalMinutes minutes")
    }

    /**
     * Schedule a one-time sync operation.
     *
     * @param requiresNetwork Whether to require network connectivity (default true)
     * @param policy Policy for handling existing one-time work (default KEEP)
     */
    fun scheduleOneTimeSync(
        requiresNetwork: Boolean = true,
        policy: ExistingWorkPolicy = ExistingWorkPolicy.KEEP
    ) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (requiresNetwork) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()

        val workRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniqueWork(
            SyncWorker.ONE_TIME_SYNC_WORK_NAME,
            policy,
            workRequest
        )

        Log.i(TAG, "Scheduled one-time sync")
    }

    /**
     * Cancel periodic sync work.
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(SyncWorker.PERIODIC_SYNC_WORK_NAME)
        Log.i(TAG, "Cancelled periodic sync")
    }

    /**
     * Cancel one-time sync work.
     */
    fun cancelOneTimeSync() {
        workManager.cancelUniqueWork(SyncWorker.ONE_TIME_SYNC_WORK_NAME)
        Log.i(TAG, "Cancelled one-time sync")
    }

    /**
     * Cancel all sync work (periodic and one-time).
     */
    fun cancelAllSync() {
        cancelPeriodicSync()
        cancelOneTimeSync()
        Log.i(TAG, "Cancelled all sync work")
    }

    companion object {
        private const val TAG = "SyncWorkScheduler"

        /**
         * Default interval for periodic sync (15 minutes).
         * This is the minimum interval allowed by WorkManager.
         */
        private const val DEFAULT_INTERVAL_MINUTES = 15L

        /**
         * Minimum interval allowed by WorkManager.
         */
        private const val MIN_INTERVAL_MINUTES = 15L
    }
}
