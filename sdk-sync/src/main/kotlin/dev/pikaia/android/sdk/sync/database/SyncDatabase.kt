package dev.pikaia.android.sdk.sync.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dev.pikaia.android.sdk.sync.data.entity.SyncOperationEntity

/**
 * Room database for sync operations.
 *
 * Stores pending, in-progress, and failed operations.
 * Separate from app's main database to isolate SDK data.
 */
@Database(
    entities = [SyncOperationEntity::class],
    version = 1,
    exportSchema = false
)
abstract class SyncDatabase : RoomDatabase() {

    abstract fun syncOperationDao(): SyncOperationDao

    companion object {
        private const val DATABASE_NAME = "pikaia_sync.db"

        @Volatile
        private var INSTANCE: SyncDatabase? = null

        /**
         * Get singleton instance of the database.
         *
         * @param context Application context
         * @param databaseName Optional custom database name
         */
        fun getInstance(
            context: Context,
            databaseName: String = DATABASE_NAME
        ): SyncDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context, databaseName).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context, databaseName: String): SyncDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                SyncDatabase::class.java,
                databaseName
            )
                .fallbackToDestructiveMigration() // For now, simple migration strategy
                .build()
        }
    }
}
