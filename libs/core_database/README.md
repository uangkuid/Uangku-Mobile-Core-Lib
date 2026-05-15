# Core Database - Room KMP Abstraction Library

A comprehensive Kotlin Multiplatform abstraction library for Room Database that eliminates boilerplate and provides a consistent, type-safe database layer across Android and iOS applications.

## Overview

This library provides a well-defined abstraction layer for Room KMP, allowing consumer projects to:

- **Write zero platform-specific code** — All path resolution and driver configuration is handled transparently
- **Avoid CRUD boilerplate** — Inherit standard operations from `BaseRepository`
- **Use typed error handling** — Consistent `DatabaseException` and `DatabaseResult` across all operations
- **Reduce DI configuration** — `DatabaseModule` base class handles Koin registration
- **Never write try-catch blocks** — `SafeDatabaseCall` maps all exceptions automatically

## Key Components

### 1. Result Types

#### `DatabaseResult<T>`

A sealed class representing all possible outcomes of a database operation:

```kotlin
sealed class DatabaseResult<out T> {
    data class Success<T>(val data: T) : DatabaseResult<T>()
    data class Error(val exception: DatabaseException) : DatabaseResult<Nothing>()
    object Loading : DatabaseResult<Nothing>()
}
```

**Extension Functions:**
- `onSuccess(action: (T) -> Unit)` — Execute action if success
- `onError(action: (DatabaseException) -> Unit)` — Execute action if error
- `onLoading(action: () -> Unit)` — Execute action if loading
- `map(transform: (T) -> U)` — Transform success data
- `flatMap(transform: suspend (T) -> DatabaseResult<U>)` — Chain operations
- `getOrThrow()` — Extract data or throw
- `getOrNull()` — Extract data or null
- `isSuccess()`, `isError()`, `isLoading()` — State checking

#### `DatabaseException`

A sealed class representing all possible database failures:

```kotlin
sealed class DatabaseException(message: String, cause: Throwable?) : Exception(message, cause) {
    class NotFound : DatabaseException(...)
    data class InsertFailed(val cause: Throwable) : DatabaseException(...)
    data class UpdateFailed(val cause: Throwable) : DatabaseException(...)
    data class DeleteFailed(val cause: Throwable) : DatabaseException(...)
    data class QueryFailed(val cause: Throwable) : DatabaseException(...)
    data class TransactionFailed(val cause: Throwable) : DatabaseException(...)
    data class Unknown(val cause: Throwable) : DatabaseException(...)
}
```

### 2. BaseDao

Generic DAO interface that eliminates CRUD boilerplate:

```kotlin
@Dao
interface BaseDao<T> {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: T): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<T>)

    @Update
    suspend fun update(entity: T)

    @Delete
    suspend fun delete(entity: T)
}
```

**Consumer Usage:**
```kotlin
@Dao
interface UserDao : BaseDao<UserEntity> {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAll(): List<UserEntity>

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
```

### 3. DatabaseConfig & DatabaseBuilder

**DatabaseConfig** — Configuration for database initialization:

```kotlin
data class DatabaseConfig(
    val databaseName: String,
    val enableDestructiveMigration: Boolean = false,
)
```

**DatabaseBuilder** — Platform-transparent builder:
- Resolves database file path (Android Context or iOS NSHomeDirectory)
- Configures BundledSQLiteDriver for consistent SQLite across devices
- Sets Dispatchers.IO for all database operations
- Applies destructive migration setting

### 4. DatabaseModule

Abstract base class for Koin DI registration:

```kotlin
abstract class DatabaseModule<T : RoomDatabase> {
    abstract fun provideConfig(): DatabaseConfig
    abstract fun createBuilder(config: DatabaseConfig): RoomDatabase.Builder<T>
    abstract fun provideModule(): Module
    protected fun buildDatabase(): T  // Convenience method
}
```

**Consumer Implementation:**
```kotlin
class AppDatabaseModule : DatabaseModule<AppDatabase>() {

    override fun provideConfig() = DatabaseConfig(
        databaseName = "app_database.db",
        enableDestructiveMigration = BuildConfig.DEBUG
    )

    override fun createBuilder(config: DatabaseConfig): RoomDatabase.Builder<AppDatabase> {
        return Room.databaseBuilder<AppDatabase>(name = config.databaseName)
    }

    override fun provideModule() = module {
        single<AppDatabase> { buildDatabase() }
        single<UserDao> { get<AppDatabase>().userDao() }
        single<TransactionDao> { get<AppDatabase>().transactionDao() }
    }
}
```

### 5. SafeDatabaseCall

Centralized exception wrapper for all DAO operations:

```kotlin
object SafeDatabaseCall {
    suspend fun <T> executeRead(block: suspend () -> T): DatabaseResult<T>
    suspend fun <T> executeWrite(block: suspend () -> T): DatabaseResult<T>
}
```

**Exception Mapping:**
- `NullPointerException` → `DatabaseException.NotFound`
- SQLite constraint violations → `DatabaseException.InsertFailed`
- Schema/syntax errors → `DatabaseException.QueryFailed`
- Other SQLite errors → `DatabaseException.Unknown`

### 6. BaseRepository

Abstract base class for repositories providing inherited CRUD operations:

```kotlin
abstract class BaseRepository<Entity, Dao : BaseDao<Entity>> {
    protected abstract val dao: Dao

    suspend fun insert(entity: Entity): DatabaseResult<Long>
    suspend fun insertAll(entities: List<Entity>): DatabaseResult<Unit>
    suspend fun update(entity: Entity): DatabaseResult<Unit>
    suspend fun delete(entity: Entity): DatabaseResult<Unit>
}
```

**Consumer Implementation:**
```kotlin
class UserRepository(
    private val userDao: UserDao
) : BaseRepository<UserEntity, UserDao>() {

    override val dao: UserDao = userDao

    suspend fun getById(id: Long): DatabaseResult<UserEntity?> =
        SafeDatabaseCall.executeRead { dao.getById(id) }

    suspend fun getAll(): DatabaseResult<List<UserEntity>> =
        SafeDatabaseCall.executeRead { dao.getAll() }
}
```

## Consumer Integration Checklist

To integrate this library in a consumer project:

### Step 1: Define Entities

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val email: String,
)
```

### Step 2: Define DAOs

```kotlin
@Dao
interface UserDao : BaseDao<UserEntity> {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users")
    suspend fun getAll(): List<UserEntity>

    @Query("DELETE FROM users")
    suspend fun deleteAll()
}
```

### Step 3: Define Database Class

```kotlin
@Database(
    entities = [UserEntity::class],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

### Step 4: Create DatabaseModule

```kotlin
class AppDatabaseModule : DatabaseModule<AppDatabase>() {

    override fun provideConfig() = DatabaseConfig(
        databaseName = "app_database.db",
        enableDestructiveMigration = BuildConfig.DEBUG
    )

    override fun createBuilder(config: DatabaseConfig): RoomDatabase.Builder<AppDatabase> {
        return Room.databaseBuilder<AppDatabase>(name = config.databaseName)
    }

    override fun provideModule() = module {
        single<AppDatabase> { buildDatabase() }
        single<UserDao> { get<AppDatabase>().userDao() }
        single<UserRepository> { UserRepository(get()) }
    }
}
```

### Step 5: Register in Koin

```kotlin
startKoin {
    modules(
        AppDatabaseModule().provideModule(),
        // other modules...
    )
}
```

### Step 6: Create Repositories

```kotlin
class UserRepository(
    private val userDao: UserDao
) : BaseRepository<UserEntity, UserDao>() {

    override val dao: UserDao = userDao

    suspend fun getById(id: Long): DatabaseResult<UserEntity?> =
        SafeDatabaseCall.executeRead { dao.getById(id) }

    suspend fun getAll(): DatabaseResult<List<UserEntity>> =
        SafeDatabaseCall.executeRead { dao.getAll() }
}
```

## Error Handling Examples

### Pattern 1: Chain Operations

```kotlin
viewModelScope.launch {
    val result = userRepository.getById(userId)
        .onSuccess { user ->
            println("User: ${user.name}")
        }
        .onError { exception ->
            when (exception) {
                is DatabaseException.NotFound -> println("User not found")
                is DatabaseException.QueryFailed -> println("Query error: ${exception.cause}")
                else -> println("Unknown error: ${exception.cause}")
            }
        }
}
```

### Pattern 2: Extract Data

```kotlin
val user = userRepository.getById(userId)
    .map { it?.name ?: "Unknown" }
    .getOrNull() ?: "Error"
```

### Pattern 3: Flat Map for Chained Operations

```kotlin
suspend fun getUserWithTransactions(userId: Long): DatabaseResult<Pair<UserEntity, List<TransactionEntity>>> {
    return userRepository.getById(userId)
        .flatMap { user ->
            if (user == null) {
                DatabaseResult.Error(DatabaseException.NotFound())
            } else {
                transactionRepository.getByUserId(user.id)
                    .map { transactions ->
                        user to transactions
                    }
            }
        }
}
```

## Performance Considerations

- **Memory**: BundledSQLiteDriver ensures consistent behavior across devices
- **Concurrency**: All DAO operations run on `Dispatchers.IO` by default
- **Queries**: Use `@Query` annotations with parameterized queries to prevent SQL injection
- **Transactions**: Use `@Transaction` annotation for operations that should be atomic

## Platform-Specific Notes

### Android

- Context must be registered in Koin for `DatabaseBuilder` to resolve the database path
- Database file is stored in the app's internal database directory
- BundledSQLiteDriver ensures SQLite version consistency across API levels

### iOS

- Database file is stored in `NSHomeDirectory()/Documents`
- BundledSQLiteDriver is mandatory (no system SQLite access)
- Framework is built as part of the KMP compilation

## Version Compatibility

- **Room**: 2.7.2+ (first stable KMP release)
- **KSP**: 2.3.2+ (KSP2, KSP1 is deprecated)
- **Kotlin**: 2.3.0+
- **SQLite**: 2.5.1 (bundled)
- **Koin**: 4.2.0+

## License

This library is part of the Uangku-Mobile-Core-Lib project.

