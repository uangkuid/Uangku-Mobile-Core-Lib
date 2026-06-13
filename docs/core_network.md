# core_network

Modul KMP (Kotlin Multiplatform) yang menyediakan layer jaringan berbasis **Ktor** untuk Android dan
iOS. Menangani semua boilerplate konfigurasi HTTP client, error mapping, authentication, retry, dan
serialisasi — sehingga feature module tidak perlu mengurus detail tersebut.

---

## Daftar Isi

1. [Gambaran Umum](#gambaran-umum)
2. [Struktur File](#struktur-file)
3. [Komponen Utama](#komponen-utama)
    - [KtorConfig](#ktorconfig)
    - [KtorClientFactory](#ktorclientfactory)
    - [KtorNetworkClient](#ktornetworkclient)
    - [NetworkResult](#networkresult)
    - [NetworkException](#networkexception)
    - [NetworkConstant](#networkconstant)
    - [NetworkModule (DI)](#networkmodule-di)
4. [Cara Penggunaan](#cara-penggunaan)
5. [Dependency](#dependency)

---

## Gambaran Umum

```
Feature Module
     │
     ▼
KtorNetworkClient.safeApiCall { ... }   ← bungkus semua panggilan API
     │
     ▼
HttpClient (dibuat oleh KtorClientFactory)
     │  ├── Bearer Auth (token + refresh)
     │  ├── ContentNegotiation (kotlinx.serialization JSON)
     │  ├── HttpTimeout (15s connect / 30s request / 15s socket)
     │  ├── HttpRequestRetry (exponential backoff, GET/HEAD + 5xx)
     │  └── Logging (hanya jika enableLogging = true)
     ▼
NetworkResult.Success / NetworkResult.Error (NetworkException)
```

---

## Struktur File

```
libs/core_network/
└── src/
    └── commonMain/kotlin/.../core_network/
        ├── client/
        │   ├── KtorConfig.kt          # Konfigurasi HttpClient (builder pattern)
        │   ├── KtorClientFactory.kt   # Factory untuk membuat HttpClient
        │   └── KtorNetworkClient.kt   # Wrapper safeApiCall
        ├── constants/
        │   └── NetworkConstant.kt     # URL env + XOR obfuscation util
        ├── di/
        │   └── NetworkModule.kt       # Interface Koin DI
        ├── exception/
        │   └── NetworkException.kt    # Sealed class error hierarchy
        └── NetworkResult.kt           # Sealed class result + extension functions
```

---

## Komponen Utama

### KtorConfig

Data class konfigurasi HttpClient dengan **builder pattern**. Semua nilai memiliki default yang
sudah disetel.

```kotlin
data class KtorConfig(
    val baseUrl: String,
    val enableLogging: Boolean = false,
    val connectTimeout: Long = 15_000L,
    val requestTimeout: Long = 30_000L,
    val socketTimeout: Long = 15_000L,
    val maxRetries: Int = 2,
    val expectSuccess: Boolean = true,
    val customPlugins: List<HttpClientConfig<*>.() -> Unit> = emptyList()
)
```

**Cara membuat:**

```kotlin
val config = KtorConfig.Builder("https://api.example.com")
    .enableLogging(BuildConfig.DEBUG)
    .connectTimeout(20_000)
    .requestTimeout(45_000)
    .maxRetries(3)
    .build()
```

**Default values:**

| Property         | Default   | Keterangan                                            |
|------------------|-----------|-------------------------------------------------------|
| `enableLogging`  | `false`   | Log level BODY, Authorization header di-sanitize      |
| `connectTimeout` | 15.000 ms | Batas waktu membuka koneksi                           |
| `requestTimeout` | 30.000 ms | Batas waktu total request                             |
| `socketTimeout`  | 15.000 ms | Batas waktu tidak ada data dari socket                |
| `maxRetries`     | `2`       | Retry hanya untuk GET/HEAD + status 5xx/429           |
| `expectSuccess`  | `true`    | Ktor lempar ResponseException jika response bukan 2xx |

---

### KtorClientFactory

Singleton factory yang menghasilkan `HttpClient` terkonfigurasi penuh. Dipanggil sekali saat DI
initialization; hasilnya adalah singleton di Koin.

```kotlin
val client = KtorClientFactory.create(
    config = config,
    tokenProvider = { sessionRepository.getAccessToken() },
    refreshTokenProvider = { authRepository.refreshToken() },
    customHeaders = mapOf("X-App-Version" to BuildConfig.VERSION_NAME)
)
```

**Plugin yang selalu dipasang:**

- `Auth` (Bearer) — load token dari `tokenProvider`, refresh dari `refreshTokenProvider`
- `ContentNegotiation` — JSON dengan `ignoreUnknownKeys = true`, `isLenient = true`
- `HttpTimeout` — dari nilai `KtorConfig`
- `HttpRequestRetry` — exponential backoff, hanya untuk idempotent method + retry-able status

**Plugin kondisional:**

- `Logging` — hanya jika `config.enableLogging = true`; Authorization header di-redact otomatis

---

### KtorNetworkClient

Singleton wrapper untuk semua pemanggilan API. Mengubah exception Ktor menjadi `NetworkResult`.

```kotlin
object KtorNetworkClient {
    suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T>
}
```

**Mapping exception:**

| Exception Ktor                | NetworkException                      |
|-------------------------------|---------------------------------------|
| `ResponseException` (401)     | `NetworkException.Unauthorized`       |
| `ResponseException` (403)     | `NetworkException.Forbidden`          |
| `ResponseException` (404)     | `NetworkException.NotFound`           |
| `ResponseException` (400)     | `NetworkException.BadRequest(errors)` |
| `ResponseException` (5xx)     | `NetworkException.ServerError(code)`  |
| `UnresolvedAddressException`  | `NetworkException.NoInternet`         |
| `HttpRequestTimeoutException` | `NetworkException.Timeout`            |
| `SerializationException`      | `NetworkException.ParseError`         |
| `Exception` (lainnya)         | `NetworkException.Unknown`            |

**Contoh penggunaan di repository:**

```kotlin
class UserRepository(private val client: HttpClient) {
    suspend fun getProfile(userId: String): NetworkResult<UserResponse> {
        return KtorNetworkClient.safeApiCall {
            client.get("users/$userId").body<UserResponse>()
        }
    }
}
```

---

### NetworkResult

Sealed class yang merepresentasikan hasil operasi jaringan.

```kotlin
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val exception: NetworkException) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
```

**Extension functions tersedia:**

```kotlin
// Chaining idiomatik
viewModelScope.launch {
    repository.getProfile(userId)
        .onSuccess { data -> _uiState.value = UiState.Success(data) }
        .onError { error -> _uiState.value = UiState.Error(error.message) }
        .onLoading { _uiState.value = UiState.Loading }
}

// Transformasi data
val nameResult: NetworkResult<String> = profileResult.map { it.name }

// Chaining operations (flatMap)
val ordersResult = profileResult.flatMap { profile ->
    repository.getOrders(profile.id)
}

// Throw on error
val profile = profileResult.getOrThrow()

// Null-safe
val profile = profileResult.getOrNull()
```

---

### NetworkException

Sealed class untuk semua error jaringan yang mungkin terjadi.

```kotlin
sealed class NetworkException(message: String?, cause: Throwable?) : Exception(message, cause) {
    class Unauthorized : NetworkException(...)       // 401 — biasanya redirect ke login
    class Forbidden : NetworkException(...)          // 403 — tidak punya izin akses resource
    class NotFound : NetworkException(...)           // 404 — resource tidak ditemukan
    data class BadRequest(                           // 400 — validasi gagal, errors bisa null
        val errors: Map<String, String>?
    ) : NetworkException(...)
    data class ServerError(val code: Int) : ...      // 5xx — error di sisi server
    class NoInternet : NetworkException(...)         // Tidak ada koneksi internet
    class Timeout : NetworkException(...)            // Request timeout
    data class ParseError(val error: Throwable) : .. // JSON parsing gagal
    data class Unknown(val error: Throwable) : ...   // Error tidak dikenal
}
```

**Cara handle di ViewModel:**

```kotlin
when (exception) {
    is NetworkException.Unauthorized -> navigateToLogin()
    is NetworkException.NoInternet -> showOfflineBanner()
    is NetworkException.BadRequest -> showFieldErrors(exception.errors)
    is NetworkException.ServerError -> showRetryDialog()
    else -> showGenericError(exception.message)
}
```

---

### NetworkConstant

Object yang menyimpan identifier environment dan URL backend. URL di-obfuscate menggunakan XOR untuk
mencegah hardcoded string terlihat jelas di APK.

```kotlin
object NetworkConstant {
    const val ENV_DEV = "env_dev"
    const val ENV_PROD = "env_prod"

    // URL internal (ter-obfuscate, decode via decodeXorToString)
    internal const val BASE_URL_DEV = "..."
    internal const val BASE_URL_PROD = "..."
}
```

> **Catatan:** URL `BASE_URL_DEV` dan `BASE_URL_PROD` di-encode dengan XOR agar tidak terbaca di
> tools seperti `apktool` atau `strings`. Decode dilakukan secara internal saat digunakan di
`NetworkModule`.

---

### NetworkModule (DI)

Interface Koin yang menyediakan `HttpClient` untuk environment DEV dan PROD. Feature module
mengimplementasikan interface ini untuk mendaftarkan client ke graph Koin.

```kotlin
interface NetworkModule {
    fun provideNetworkModule(): Module
}
```

**Cara implementasi di consumer project:**

```kotlin
class AppNetworkModule(
    private val tokenProvider: suspend () -> String?,
    private val refreshProvider: suspend () -> String?
) : NetworkModule {
    override fun provideNetworkModule() = module {
        single(named(NetworkConstant.ENV_PROD)) { (enableLog: Boolean) ->
            KtorClientFactory.create(
                config = KtorConfig.Builder(BASE_URL_PROD)
                    .enableLogging(enableLog)
                    .build(),
                tokenProvider = tokenProvider,
                refreshTokenProvider = refreshProvider
            )
        }
    }
}
```

**Inject di repository/data source:**

```kotlin
class UserRemoteDataSource(
    private val client: HttpClient  // Koin inject dengan qualifier named(ENV_PROD)
)
```

---

## Cara Penggunaan

### 1. Setup di App Module

```kotlin
// Di Koin module aplikasi
val appModule = module {
    single(named(NetworkConstant.ENV_PROD)) { (enableLog: Boolean) ->
        KtorClientFactory.create(
            config = KtorConfig.Builder("https://api.uangku.app")
                .enableLogging(enableLog)
                .build(),
            tokenProvider = { get<SessionRepository>().getAccessToken() },
            refreshTokenProvider = { get<AuthRepository>().refreshToken() }
        )
    }
}
```

### 2. Panggil API di Repository

```kotlin
class TransactionRepository(
    private val client: HttpClient
) {
    suspend fun getTransactions(): NetworkResult<List<Transaction>> {
        return KtorNetworkClient.safeApiCall {
            client.get("transactions").body<List<Transaction>>()
        }
    }

    suspend fun createTransaction(request: CreateTransactionRequest): NetworkResult<Transaction> {
        return KtorNetworkClient.safeApiCall {
            client.post("transactions") {
                setBody(request)
            }.body<Transaction>()
        }
    }
}
```

### 3. Konsumsi di ViewModel

```kotlin
class TransactionViewModel(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _state = MutableStateFlow<NetworkResult<List<Transaction>>>(NetworkResult.Loading)
    val state = _state.asStateFlow()

    fun loadTransactions() {
        viewModelScope.launch {
            _state.value = NetworkResult.Loading
            _state.value = repository.getTransactions()
        }
    }
}
```

---

## Dependency

```kotlin
// build.gradle.kts (core_network)
commonMain {
    dependencies {
        api(libs.bundles.ktor)   // ktor-client-core, cio, auth, content-negotiation,
        // logging, serialization, retry, timeout
        api(libs.bundles.koin)   // koin-core
    }
}
```

**Versi library utama:**

- Ktor: lihat `gradle/libs.versions.toml` → `ktor`
- Koin: lihat `gradle/libs.versions.toml` → `koin`
