package com.oratakashi.uangku.core.libs.core_database.builder

/**
 * Configuration data class for database initialization.
 * Carries only the configuration parameters that are relevant to the platform-agnostic
 * decision of how to build the database. Platform-specific path resolution is handled by DatabaseBuilder.
 *
 * @param databaseName The filename of the SQLite database file, including the extension.
 *                     Example: "app_database.db"
 *                     On Android, this resolves to the app's internal database directory.
 *                     On iOS, this resolves to the app's Documents directory.
 * @param enableDestructiveMigration When true, Room drops and recreates the database if no
 *                                   migration path is found for the current schema version.
 *                                   Recommended to set true in debug builds and false in production.
 *                                   In a consumer project: BuildConfig.DEBUG
 *
 * @author oratakashi
 * @since 15 May 2026
 */
data class DatabaseConfig(
    val databaseName: String,
    val enableDestructiveMigration: Boolean = false,
)

