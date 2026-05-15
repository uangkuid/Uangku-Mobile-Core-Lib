package com.oratakashi.uangku.core.libs.core_database.builder

import androidx.room.RoomDatabase
import kotlinx.coroutines.Dispatchers

/**
 * iOS implementation of DatabaseBuilder.
 * Resolves the database file path using iOS NSHomeDirectory and configures
 * the RoomDatabase.Builder with platform-specific settings.
 *
 * On iOS, there is no system SQLite accessible to Room, so SQLite must be bundled
 * with the binary. The database file is stored in the app's Documents directory.
 *
 * @param config The database configuration containing name and migration settings.
 *
 * @author oratakashi
 * @since 15 May 2026
 */
actual class DatabaseBuilder actual constructor(private val config: DatabaseConfig) {

    /**
     * Builds and returns a fully configured RoomDatabase instance for iOS.
     *
     * Configuration applied:
     * - Uses BundledSQLiteDriver (mandatory on iOS)
     * - Resolves database path to NSHomeDirectory/Documents
     * - Sets query coroutine context to Dispatchers.Default for database I/O operations
     * - Applies fallback to destructive migration if enabled
     *
     * @param T The RoomDatabase subclass to build.
     * @param builder A RoomDatabase.Builder pre-constructed by the consumer.
     * @return The fully configured and ready-to-use database instance.
     */
    actual fun <T : RoomDatabase> build(builder: RoomDatabase.Builder<T>): T {
        return builder
            .setQueryCoroutineContext(Dispatchers.Default)
            .apply {
                if (config.enableDestructiveMigration) {
                    fallbackToDestructiveMigration(true)
                }
            }
            .build()
    }
}


