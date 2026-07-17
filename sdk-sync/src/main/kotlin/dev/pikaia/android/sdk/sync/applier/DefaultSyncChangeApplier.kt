package dev.pikaia.android.sdk.sync.applier

import android.util.Log
import dev.pikaia.android.sdk.sync.data.response.SyncChange
import dev.pikaia.android.sdk.sync.queue.SyncIntent

/**
 * Default implementation of SyncChangeApplier that routes changes to registered handlers.
 *
 * Applications register entity-specific handlers for each entity type they want to sync.
 *
 * Example usage:
 * ```
 * val applier = DefaultSyncChangeApplier()
 * applier.register("crm.contact", ContactChangeHandler(baseContactStore))
 * applier.register("crm.tag", TagChangeHandler(baseTagStore))
 * syncEngine.setChangeApplier(applier)
 * ```
 */
class DefaultSyncChangeApplier : SyncChangeApplier {

    private val handlers = mutableMapOf<String, EntityChangeHandler<*>>()

    /**
     * Register a handler for an entity type.
     *
     * @param entityType Entity type identifier (e.g., "crm.contact")
     * @param handler Handler implementation for this entity type
     */
    fun <T> register(entityType: String, handler: EntityChangeHandler<T>) {
        handlers[entityType] = handler
    }

    /**
     * Unregister a handler for an entity type.
     *
     * @param entityType Entity type to unregister
     */
    fun unregister(entityType: String) {
        handlers.remove(entityType)
    }

    override suspend fun applyChanges(changes: List<SyncChange>): SyncBatchResult {
        if (changes.isEmpty()) {
            return SyncBatchResult.empty()
        }

        var successCount = 0
        var failureCount = 0
        val failures = mutableListOf<SyncBatchResult.FailureDetail>()

        for (change in changes) {
            val result = applyChange(change)
            when (result) {
                is ChangeResult.Success -> successCount++
                is ChangeResult.Failure -> {
                    failureCount++
                    failures.add(
                        SyncBatchResult.FailureDetail(
                            entityType = change.entityType,
                            entityId = change.entityId,
                            error = result.error
                        )
                    )
                    Log.e(TAG, "Failed to apply change: ${change.entityType}/${change.entityId}", result.exception)
                }
            }
        }

        return SyncBatchResult(
            successCount = successCount,
            failureCount = failureCount,
            failures = failures
        )
    }

    @Suppress("UNCHECKED_CAST")
    private suspend fun applyChange(change: SyncChange): ChangeResult {
        try {
            // Get handler for this entity type
            val handler = handlers[change.entityType]
                ?: return ChangeResult.Failure("No handler registered for entity type: ${change.entityType}")

            // Cast handler (type erasure, but safe due to registration)
            val typedHandler = handler as EntityChangeHandler<Any>

            // Parse intent
            val intent = try {
                SyncIntent.fromApiString(change.operation)
            } catch (e: Exception) {
                return ChangeResult.Failure("Invalid intent: ${change.operation}", e)
            }

            // Apply based on intent
            when (intent) {
                SyncIntent.DELETE -> {
                    typedHandler.applyDelete(change.entityId, change.data)
                }
                SyncIntent.CREATE, SyncIntent.UPDATE -> {
                    if (change.data == null) {
                        return ChangeResult.Failure("Payload is null for ${intent.name} operation")
                    }

                    // Decode entity
                    val entity = try {
                        typedHandler.decode(change.data)
                    } catch (e: Exception) {
                        return ChangeResult.Failure("Failed to decode entity: ${e.message}", e)
                    }

                    // Apply change
                    typedHandler.apply(entity, intent, change.entityId, change.version)
                }
            }

            return ChangeResult.Success

        } catch (e: Exception) {
            return ChangeResult.Failure("Unexpected error applying change: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "DefaultSyncChangeApplier"
    }
}
