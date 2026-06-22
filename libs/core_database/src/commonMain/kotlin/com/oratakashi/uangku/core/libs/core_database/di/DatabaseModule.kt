package com.oratakashi.uangku.core.libs.core_database.di

import androidx.room.RoomDatabase
import com.oratakashi.uangku.core.libs.core_database.builder.DatabaseBuilder
import com.oratakashi.uangku.core.libs.core_database.builder.DatabaseConfig
import org.koin.core.module.Module

/**
 * Abstract base class for database module registration in Koin.
 * Eliminates boilerplate database configuration and DI registration across consumer projects.
 *
 * Consumer projects extend this class to declare their specific database configuration,
 * database creation, and DAO registrations. The base class handles all cross-cutting concerns
 * like DatabaseBuilder instantiation and database configuration.
 *
 * Example usage in a consumer project:
 * ```
 * class AppDatabaseModule : DatabaseModule<AppDatabase>() {
 *
 *     override fun provideConfig() = DatabaseConfig(
 *         databaseName = "app_database.db",
 *         enableDestructiveMigration = BuildConfig.DEBUG
 *     )
 *
 *     override fun createBuilder(config: DatabaseConfig): RoomDatabase.Builder<AppDatabase> {
 *         return Room.databaseBuilder<AppDatabase>(name = config.databaseName)
 *     }
 *
 *     override fun provideModule() = module {
 *         single<AppDatabase> { buildDatabase() }
 *         single<UserDao> { get<AppDatabase>().userDao() }
 *         single<TransactionDao> { get<AppDatabase>().transactionDao() }
 *     }
 * }
 * ```
 *
 * @param T The RoomDatabase subclass managed by this module.
 *
 * @author oratakashi
 * @since 15 May 2026
 */
abstract class DatabaseModule<T : RoomDatabase> {

    /**
     * Provides the database configuration.
     * Consumer implementations declare the database name and migration settings here.
     *
     * @return A DatabaseConfig instance with the desired settings.
     */
    abstract fun provideConfig(): DatabaseConfig

    /**
     * Creates and returns a RoomDatabase.Builder for the specific database type.
     * Consumer implementations provide the Room.databaseBuilder call here,
     * which is where the @Database-annotated class is referenced.
     *
     * @param config The database configuration.
     * @return A RoomDatabase.Builder instance ready to be passed to buildDatabase().
     */
    abstract fun createBuilder(config: DatabaseConfig): RoomDatabase.Builder<T>

    /**
     * Provides the Koin module with all database and DAO registrations.
     * Consumer implementations declare all single {} and factory {} registrations here.
     *
     * @return A Koin Module with all necessary bindings.
     */
    abstract fun provideModule(): Module

    /**
     * Convenience function provided by the base class.
     * Internally creates DatabaseBuilder(provideConfig()) and calls
     * build(createBuilder(provideConfig())).
     *
     * Consumer implementations call this inside provideModule() when registering
     * the database singleton.
     *
     * @return A fully configured and ready-to-use database instance.
     */
    protected fun buildDatabase(): T {
        val config = provideConfig()
        val builder = createBuilder(config)
        val databaseBuilder = DatabaseBuilder(config)
        return databaseBuilder.build(builder)
    }
}

