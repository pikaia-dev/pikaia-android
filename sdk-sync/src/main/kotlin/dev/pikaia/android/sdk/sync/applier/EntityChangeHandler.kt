package dev.pikaia.android.sdk.sync.applier

import dev.pikaia.android.sdk.sync.queue.SyncIntent

/**
 * Handler for applying server changes to local data for a specific entity type.
 *
 * Applications implement this interface for each entity type they want to sync.
 *
 * @param T The entity type (e.g., Contact, Tag, Note)
 */
interface EntityChangeHandler<T> {

    /**
     * Decode JSON payload to entity.
     *
     * @param payload JSON string from server
     * @return Decoded entity
     * @throws Exception if decoding fails
     */
    fun decode(payload: String): T

    /**
     * Apply a change to local database.
     *
     * For CREATE/UPDATE operations:
     * - Check if entity exists locally
     * - Compare sync versions if applicable
     * - Apply change only if server version is newer
     *
     * For DELETE operations:
     * - Delete the entity from local database
     *
     * @param entity Decoded entity from server
     * @param intent Operation intent (CREATE, UPDATE, DELETE)
     * @param entityId Entity identifier
     * @param version Sync version from server for conflict resolution
     */
    suspend fun apply(entity: T, intent: SyncIntent, entityId: String, version: Int)

    /**
     * Apply a delete operation.
     *
     * Called for DELETE operations where payload might be null/empty.
     *
     * @param entityId Entity identifier to delete
     * @param payload Optional payload (usually null for deletes)
     */
    suspend fun applyDelete(entityId: String, payload: String?)
}
