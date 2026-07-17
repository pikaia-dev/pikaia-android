package dev.pikaia.android.sdk.sync.queue

/**
 * Sync operation intent.
 *
 * Represents the type of operation to perform on an entity.
 */
enum class SyncIntent {
    CREATE,
    UPDATE,
    DELETE;

    /**
     * Convert to API string format.
     */
    fun toApiString(): String = when (this) {
        CREATE -> "create"
        UPDATE -> "update"
        DELETE -> "delete"
    }

    companion object {
        /**
         * Parse from API string format.
         */
        fun fromApiString(value: String): SyncIntent = when (value) {
            "create" -> CREATE
            "update" -> UPDATE
            "delete" -> DELETE
            else -> throw IllegalArgumentException("Unknown sync intent: $value")
        }
    }
}
