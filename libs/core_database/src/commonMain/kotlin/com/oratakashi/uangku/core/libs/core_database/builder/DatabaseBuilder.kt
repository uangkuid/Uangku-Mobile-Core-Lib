package com.oratakashi.uangku.core.libs.core_database.builder

import androidx.room.RoomDatabase

/**
 * Abstracts the platform-specific database file path resolution and RoomDatabase builder configuration.
 * Consumers pass a DatabaseConfig and receive a fully configured RoomDatabase instance
 * without writing a single line of platform-specific code.
 *
 * The actual implementation is provided by expect/actual declarations:
 * - androidMain: Uses Android Context to resolve database file path
 * - iosMain: Uses iOS NSHomeDirectory to resolve database file path
 *
 * @param config The database configuration containing name and migration settings.
 *
 * @author oratakashi
 * @since 15 May 2026
 */
expect class DatabaseBuilder(config: DatabaseConfig) {

    /**
     * Builds and returns a fully configured RoomDatabase instance.
     *
     * @param T The RoomDatabase subclass to build.
     * @param builder A RoomDatabase.Builder that was pre-constructed by the consumer
     *                using their @Database-annotated class.
     * @return The fully configured and ready-to-use database instance.
     */
    fun <T : RoomDatabase> build(builder: RoomDatabase.Builder<T>): T
}

