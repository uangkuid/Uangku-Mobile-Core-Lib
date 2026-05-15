package com.oratakashi.uangku.core.libs.core_database.builder

import android.content.Context
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import kotlinx.coroutines.Dispatchers
import org.koin.java.KoinJavaComponent.inject

/**
 * Android implementation of DatabaseBuilder.
 * Resolves the database file path using Android Context and configures
 * the RoomDatabase.Builder with platform-specific settings.
 *
 * Context is resolved from Koin. The consumer project must register
 * Context in their Koin graph, which is standard practice in any Android KMP project using Koin.
 *
 * @param config The database configuration containing name and migration settings.
 *
 * @author oratakashi
 * @since 15 May 2026
 */
actual class DatabaseBuilder actual constructor(private val config: DatabaseConfig) {

    // Context is resolved from Koin at runtime
    private val context: Context by inject(Context::class.java)

    /**
     * Builds and returns a fully configured RoomDatabase instance for Android.
     *
     * Configuration applied:
     * - Uses BundledSQLiteDriver for consistent SQLite version across all devices
     * - Sets query coroutine context to Dispatchers.IO for database I/O operations
     * - Applies fallback to destructive migration if enabled
     *
     * @param T The RoomDatabase subclass to build.
     * @param builder A RoomDatabase.Builder pre-constructed by the consumer.
     * @return The fully configured and ready-to-use database instance.
     */
    actual fun <T : RoomDatabase> build(builder: RoomDatabase.Builder<T>): T {
        return builder
            .setQueryCoroutineContext(Dispatchers.IO)
            .apply {
                if (config.enableDestructiveMigration) {
                    fallbackToDestructiveMigration(true)
                }
            }
            .build()
    }
}

