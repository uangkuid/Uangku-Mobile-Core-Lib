# core_database

Modul KMP (Kotlin Multiplatform) yang menyediakan abstraksi **Room KMP** untuk Android dan iOS. Menghilangkan boilerplate konfigurasi database, path resolution per-platform, exception mapping, dan CRUD base class — sehingga feature module hanya perlu mendeklarasikan entity, DAO, dan query spesifik mereka.

---

## Daftar Isi
1. [Gambaran Umum](#gambaran-umum)
2. [Struktur File](#struktur-file)
3. [Komponen Utama](#komponen-utama)
   - [DatabaseConfig](#databaseconfig)
   - [DatabaseBuilder](#databasebuilder)
   - [BaseDao](#basedao)
   - [BaseRepository](#baserepository)
   - [SafeDatabaseCall](#safedatabasecall)
   - [DatabaseResult](#databaseresult)
   - [DatabaseException](#databaseexception)
   - [DatabaseModule (DI)](#databasemodule-di)
4. [Cara Penggunaan End-to-End](#cara-penggunaan-end-to-end)
5. [Perbedaan Platform](#perbedaan-platform)
6. [Dependency](#dependency)

---

## Gambaran Umum

```
Consumer Project
      │
      ├── @Database (Room) ← hanya declare entity & DAO functions
      ├── UserDao extends BaseDao<UserEntity>
      └── UserRepository extends BaseRepository<UserEntity, UserDao>
             │
             ▼
     SafeDatabaseCall.executeRead { dao.query() }
             │
             ▼
     DatabaseResult.Success / DatabaseResult.Error (DatabaseException)
             │
     DatabaseBuilder (platform-specific path + BundledSQLite)
             │
      ├── Android: app internal database dir + Dispatchers.IO
      └── iOS: NSHomeDirectory/Documents + Dispatchers.Default
```

---

## Struktur File

```
libs/core_database/
└── src/
    ├── commonMain/kotlin/.../core_database/
    │   ├── builder/
    │   │   ├── DatabaseBuilder.kt      # expect class — platform-agnostic interface
    │   │   └── DatabaseConfig.kt       # data class konfigurasi
    │   ├── dao/
    │   │   └── BaseDao.kt              # interface generic CRUD @Dao
    │   ├── di/
    │   │   └── DatabaseModule.kt       # abstract class DI helper
    │   ├── exception/
    │   │   └── DatabaseException.kt    # sealed class error hierarchy
    │   ├── helper/
    │   │   └── SafeDatabaseCall.kt     # exception mapping wrapper
    │   ├── repository/
    │   │   └── BaseRepository.kt       # abstract class dengan CRUD built-in
    │   └── DatabaseResult.kt           # sealed class result + extension functions
    ├── androidMain/kotlin/.../
    │   └── builder/DatabaseBuilder.android.kt  # Context + Dispatchers.IO
    └── iosMain/kotlin/.../
        └── builder/DatabaseBuilder.ios.kt      # NSHomeDirectory + Dispatchers.Default
```

---

## Komponen Utama

### DatabaseConfig

Data class sederhana untuk konfigurasi database. Diteruskan ke `DatabaseBuilder` dan `DatabaseModule`.

```kotlin
data class DatabaseConfig(
    val databaseName: String,                      // e.g. "uangku_app.db"
    val enableDestructiveMigration: Boolean = false
)
```

| Property | Keterangan |
|---|---|
| `databaseName` | Nama file SQLite. Android: `databases/` internal dir. iOS: `NSHomeDirectory/Documents/` |
| `enableDestructiveMigration` | Jika `true`, Room drop & recreate DB saat tidak ada migration path. Set `BuildConfig.DEBUG` di production app |

---

### DatabaseBuilder

`expect class` yang mengabstraksikan path resolution dan konfigurasi `RoomDatabase.Builder` per platform. Consumer **tidak perlu menulis kode platform-spesifik** sama sekali.

```kotlin
expect class DatabaseBuilder(config: DatabaseConfig) {
    fun <T : RoomDatabase> build(builder: RoomDatabase.Builder<T>): T
}
```

**Platform Android** (`DatabaseBuilder.android.kt`):
- Mengambil `Context` dari Koin (harus sudah diregistrasi oleh app module)
- `setQueryCoroutineContext(Dispatchers.IO)`
- Menggunakan SQLite bawaan Android

**Platform iOS** (`DatabaseBuilder.ios.kt`):
- Path: `NSHomeDirectory()/Documents/<databaseName>`
- `setQueryCoroutineContext(Dispatchers.Default)`
- Wajib menggunakan `BundledSQLiteDriver` (iOS tidak punya system SQLite yang bisa diakses Room)

---

### BaseDao

Interface Room generik yang menyediakan 4 operasi CRUD standar. Setiap DAO di consumer project **extend interface ini** dan hanya menambahkan query spesifik.

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

**Conflict strategy:** `REPLACE` — jika entity dengan primary key yang sama sudah ada, data lama akan ditimpa.

**Cara extend di consumer project:**
```kotlin
@Dao
interface UserDao : BaseDao<UserEntity> {
    // insert, insertAll, update, delete sudah ada dari BaseDao
    // Hanya tulis query yang spesifik untuk UserEntity:

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users ORDER BY created_at DESC")
    fun getAllAsFlow(): Flow<List<UserEntity>>
}
```

---

### BaseRepository

Abstract class yang mengimplementasikan 4 CRUD operation standar menggunakan `SafeDatabaseCall`. Consumer repository **extend class ini** dan hanya menambahkan fungsi query spesifik.

```kotlin
abstract class BaseRepository<Entity, Dao : BaseDao<Entity>> {
    protected abstract val dao: Dao

    suspend fun insert(entity: Entity): DatabaseResult<Long>
    suspend fun insertAll(entities: List<Entity>): DatabaseResult<Unit>
    suspend fun update(entity: Entity): DatabaseResult<Unit>
    suspend fun delete(entity: Entity): DatabaseResult<Unit>
}
```

**Cara extend di consumer project:**
```kotlin
class UserRepository(
    private val userDao: UserDao
) : BaseRepository<UserEntity, UserDao>() {

    override val dao: UserDao = userDao

    // insert, insertAll, update, delete sudah diwarisi, tidak perlu ditulis ulang

    suspend fun getById(id: Long): DatabaseResult<UserEntity?> =
        SafeDatabaseCall.executeRead { dao.getById(id) }

    suspend fun getAll(): DatabaseResult<List<UserEntity>> =
        SafeDatabaseCall.executeRead { dao.getAll() }
}
```

---

### SafeDatabaseCall

Singleton wrapper yang menjalankan operasi DAO dan memetakan semua exception ke `DatabaseException`. **Tidak ada try-catch di repository** — semua penanganan error dilakukan di sini.

```kotlin
object SafeDatabaseCall {
    suspend fun <T> executeRead(block: suspend () -> T): DatabaseResult<T>
    suspend fun <T> executeWrite(block: suspend () -> T): DatabaseResult<T>
}
```

**Mapping exception:**

| Exception | DatabaseException |
|---|---|
| `NullPointerException` | `NotFound` — Room lempar NPE saat return type non-null terima null |
| `SQLiteException` + "UNIQUE constraint" | `InsertFailed` |
| `SQLiteException` + "no such table" / "syntax error" | `QueryFailed` |
| `SQLiteException` + "insert" dalam message | `InsertFailed` |
| `SQLiteException` + "update" | `UpdateFailed` |
| `SQLiteException` + "delete" | `DeleteFailed` |
| `SQLiteException` + "select"/"query" | `QueryFailed` |
| `CancellationException` | **Tidak ditangkap** — propagate ke coroutine parent |
| `Exception` lainnya | `Unknown` |

**Perbedaan `executeRead` vs `executeWrite`:** Secara teknis identik — perbedaannya **semantik** saja di call site untuk keterbacaan kode.

---

### DatabaseResult

Sealed class yang merepresentasikan hasil operasi database. Pola yang sama dengan `NetworkResult`.

```kotlin
sealed class DatabaseResult<out T> {
    data class Success<T>(val data: T) : DatabaseResult<T>()
    data class Error(val exception: DatabaseException) : DatabaseResult<Nothing>()
    object Loading : DatabaseResult<Nothing>()
}
```

**Extension functions tersedia:**

```kotlin
repository.getById(userId)
    .onSuccess { user -> showProfile(user) }
    .onError { error ->
        when (error) {
            is DatabaseException.NotFound -> showEmptyState()
            else -> showError(error.message)
        }
    }

// Transform data
val nameResult: DatabaseResult<String> = userResult.map { it.name }

// Chaining operations
val ordersResult = userResult.flatMap { user ->
    orderRepository.getByUserId(user.id)
}

// Throw on error (use in tests or guaranteed-success flows)
val user = userResult.getOrThrow()

// Null-safe
val user = userResult.getOrNull()

// Predicate checks
userResult.isSuccess()
userResult.isError()
userResult.isLoading()
```

---

### DatabaseException

Sealed class untuk semua kemungkinan error operasi database.

```kotlin
sealed class DatabaseException(message: String, cause: Throwable?) : Exception(message, cause) {
    class NotFound : DatabaseException(...)                          // Query sukses tapi tidak ada data
    data class InsertFailed(val causedException: Throwable) : ...   // INSERT gagal (constraint, disk)
    data class UpdateFailed(val causedException: Throwable) : ...   // UPDATE gagal
    data class DeleteFailed(val causedException: Throwable) : ...   // DELETE gagal (foreign key)
    data class QueryFailed(val causedException: Throwable) : ...    // SELECT / @RawQuery gagal
    data class TransactionFailed(val causedException: Throwable) :  // @Transaction rollback
    data class Unknown(val causedException: Throwable) : ...        // Error tidak dikenal
}
```

---

### DatabaseModule (DI)

Abstract class Koin yang menghilangkan boilerplate setup database. Consumer project extend class ini.

```kotlin
abstract class DatabaseModule<T : RoomDatabase> {
    abstract fun provideConfig(): DatabaseConfig
    abstract fun createBuilder(config: DatabaseConfig): RoomDatabase.Builder<T>
    abstract fun provideModule(): Module

    protected fun buildDatabase(): T  // ← panggil ini di provideModule()
}
```

**Cara implementasi di consumer project:**
```kotlin
class AppDatabaseModule : DatabaseModule<AppDatabase>() {

    override fun provideConfig() = DatabaseConfig(
        databaseName = "uangku_app.db",
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

**Daftarkan ke Koin di Application:**
```kotlin
startKoin {
    modules(AppDatabaseModule().provideModule())
}
```

> **Android:** `Context` harus sudah ada di Koin graph sebelum `buildDatabase()` dipanggil, karena `DatabaseBuilder.android.kt` resolve `Context` dari Koin. Biasanya diregistrasi via `androidContext(this)` di `startKoin { ... }`.

---

## Cara Penggunaan End-to-End

### Langkah 1: Buat Entity

```kotlin
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val email: String,
    val createdAt: Long = System.currentTimeMillis()
)
```

### Langkah 2: Buat DAO

```kotlin
@Dao
interface UserDao : BaseDao<UserEntity> {
    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    @Query("SELECT * FROM users")
    fun getAllAsFlow(): Flow<List<UserEntity>>
}
```

### Langkah 3: Buat Database

```kotlin
@Database(entities = [UserEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
```

### Langkah 4: Buat Repository

```kotlin
class UserRepository(userDao: UserDao) : BaseRepository<UserEntity, UserDao>() {
    override val dao = userDao

    suspend fun getById(id: Long): DatabaseResult<UserEntity?> =
        SafeDatabaseCall.executeRead { dao.getById(id) }
}
```

### Langkah 5: Setup DI

```kotlin
class AppDatabaseModule : DatabaseModule<AppDatabase>() {
    override fun provideConfig() = DatabaseConfig("app.db", BuildConfig.DEBUG)
    override fun createBuilder(config: DatabaseConfig) =
        Room.databaseBuilder<AppDatabase>(name = config.databaseName)
    override fun provideModule() = module {
        single<AppDatabase> { buildDatabase() }
        single<UserDao> { get<AppDatabase>().userDao() }
        single { UserRepository(get()) }
    }
}
```

### Langkah 6: Gunakan di ViewModel

```kotlin
class UserViewModel(private val repo: UserRepository) : ViewModel() {
    fun loadUser(id: Long) {
        viewModelScope.launch {
            repo.getById(id)
                .onSuccess { user -> _uiState.value = UiState.Content(user) }
                .onError { error ->
                    if (error is DatabaseException.NotFound)
                        _uiState.value = UiState.Empty
                    else
                        _uiState.value = UiState.Error(error.message)
                }
        }
    }
}
```

---

## Perbedaan Platform

| Aspek | Android | iOS |
|---|---|---|
| Context | Diambil dari Koin (`by inject`) | Tidak diperlukan |
| SQLite driver | System SQLite (bawaan Android) | `BundledSQLiteDriver` (wajib) |
| Coroutine dispatcher | `Dispatchers.IO` | `Dispatchers.Default` |
| Path database | `context.getDatabasePath(name)` | `NSHomeDirectory()/Documents/<name>` |

---

## Dependency

```kotlin
// build.gradle.kts (core_database)
commonMain {
    dependencies {
        api(libs.androidx.room.runtime)    // Room KMP
        api(libs.androidx.sqlite.bundled)  // BundledSQLiteDriver
        api(libs.koin.core)
    }
}
// KSP (annotation processor untuk Room)
dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosX64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
}
```

Schema Room disimpan di `src/commonMain/schemas/` untuk keperluan migration.
