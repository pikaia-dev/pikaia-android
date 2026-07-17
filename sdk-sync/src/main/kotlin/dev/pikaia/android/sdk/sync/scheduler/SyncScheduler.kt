package dev.pikaia.android.sdk.sync.scheduler

import android.util.Log
import dev.pikaia.android.sdk.sync.engine.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Scheduler that debounces sync requests to avoid excessive sync operations.
 *
 * When changes are made rapidly, the scheduler waits for a quiet period before
 * triggering a sync. This prevents hammering the server with sync requests.
 *
 * Default debounce period: 5 seconds (matches iOS implementation)
 *
 * Example usage:
 * ```
 * val scheduler = SyncScheduler(syncEngine, coroutineScope)
 * scheduler.start()
 *
 * // After making changes
 * scheduler.scheduleSync() // Will debounce
 *
 * // For urgent changes
 * scheduler.syncNow() // Immediate sync, cancels pending debounced sync
 * ```
 */
class SyncScheduler(
    private val syncEngine: SyncEngine,
    private val scope: CoroutineScope,
    private val debounceDelay: Duration = 5.seconds
) {
    private val mutex = Mutex()
    private var debounceJob: Job? = null
    private var isStarted = false

    /**
     * Start the scheduler.
     *
     * Must be called before scheduling syncs.
     */
    fun start() {
        isStarted = true
        Log.i(TAG, "SyncScheduler started with ${debounceDelay.inWholeSeconds}s debounce")
    }

    /**
     * Stop the scheduler and cancel any pending syncs.
     */
    fun stop() {
        isStarted = false
        debounceJob?.cancel()
        debounceJob = null
        Log.i(TAG, "SyncScheduler stopped")
    }

    /**
     * Schedule a sync operation with debouncing.
     *
     * If called multiple times within the debounce period, only the last call
     * will trigger a sync after the delay.
     *
     * @param entityTypes Optional list of entity types to sync
     * @param limit Optional limit for number of changes to pull
     */
    fun scheduleSync(
        entityTypes: List<String>? = null,
        limit: Int? = null
    ) {
        if (!isStarted) {
            Log.w(TAG, "Scheduler not started, ignoring scheduleSync")
            return
        }

        scope.launch {
            mutex.withLock {
                // Cancel existing debounce job
                debounceJob?.cancel()

                // Schedule new debounced sync
                debounceJob = scope.launch {
                    Log.d(TAG, "Debouncing sync for ${debounceDelay.inWholeSeconds}s")
                    delay(debounceDelay)

                    Log.i(TAG, "Debounce period elapsed, triggering sync")
                    try {
                        syncEngine.sync(entityTypes, limit)
                    } catch (e: Exception) {
                        Log.e(TAG, "Debounced sync failed", e)
                    }
                }
            }
        }
    }

    /**
     * Trigger an immediate sync, bypassing debounce.
     *
     * This will cancel any pending debounced sync and execute immediately.
     *
     * @param entityTypes Optional list of entity types to sync
     * @param limit Optional limit for number of changes to pull
     */
    fun syncNow(
        entityTypes: List<String>? = null,
        limit: Int? = null
    ) {
        if (!isStarted) {
            Log.w(TAG, "Scheduler not started, ignoring syncNow")
            return
        }

        scope.launch {
            mutex.withLock {
                // Cancel any pending debounced sync
                debounceJob?.cancel()
                debounceJob = null

                Log.i(TAG, "Triggering immediate sync")
                try {
                    syncEngine.sync(entityTypes, limit)
                } catch (e: Exception) {
                    Log.e(TAG, "Immediate sync failed", e)
                }
            }
        }
    }

    /**
     * Check if there's a pending debounced sync.
     */
    fun hasPendingSync(): Boolean {
        return debounceJob?.isActive == true
    }

    /**
     * Cancel any pending debounced sync without triggering it.
     */
    fun cancelPendingSync() {
        scope.launch {
            mutex.withLock {
                debounceJob?.cancel()
                debounceJob = null
                Log.d(TAG, "Cancelled pending sync")
            }
        }
    }

    companion object {
        private const val TAG = "SyncScheduler"
    }
}
