package dev.pikaia.android.sdk.sync.cursor

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the sync cursor for incremental pulls.
 *
 * The cursor is an opaque string provided by the server for pagination.
 * Client never parses or manipulates it - just stores and sends back.
 */
class SyncCursorStore(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Get the stored cursor.
     *
     * @return Cursor string, or null if no cursor stored
     */
    suspend fun getCursor(): String? = withContext(Dispatchers.IO) {
        prefs.getString(KEY_CURSOR, null)
    }

    /**
     * Store the cursor from server response.
     *
     * @param cursor Opaque cursor string from server
     */
    suspend fun setCursor(cursor: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(KEY_CURSOR, cursor).apply()
    }

    /**
     * Clear the cursor.
     *
     * Used when triggering a full resync.
     */
    suspend fun clearCursor() = withContext(Dispatchers.IO) {
        prefs.edit().remove(KEY_CURSOR).apply()
    }

    companion object {
        private const val PREFS_NAME = "pikaia_sync_cursor"
        private const val KEY_CURSOR = "last_cursor"
    }
}
