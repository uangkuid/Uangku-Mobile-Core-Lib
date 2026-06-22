# core_crypto

Modul KMP (Kotlin Multiplatform) yang menyediakan kriptografi, hashing, dan penyimpanan sensitif untuk Android dan iOS. Mengabstraksikan semua detail platform (AndroidKeyStore, EncryptedSharedPreferences, iOS Keychain) di balik interface yang seragam dan coroutine-safe.

---

## Daftar Isi
1. [Gambaran Umum](#gambaran-umum)
2. [Struktur File](#struktur-file)
3. [Komponen Utama](#komponen-utama)
   - [AesCryptor](#aescryptor)
   - [Hasher](#hasher)
   - [SecureStorage](#securestorage)
   - [EncodingUtils](#encodingutils)
   - [CryptoKeyAlias](#cryptokeyalias)
   - [CryptoException](#cryptoexception)
   - [SecureStorageException](#securestorageexception)
   - [Koin DI Modules](#koin-di-modules)
4. [Format Ciphertext (Backend Compatibility)](#format-ciphertext-backend-compatibility)
5. [Cara Penggunaan](#cara-penggunaan)
6. [Implementasi Per Platform](#implementasi-per-platform)
7. [Dependency](#dependency)

---

## Gambaran Umum

```
Consumer Project
      │
      ├── AesCryptor          ← enkripsi/dekripsi AES-256-CBC
      │     ├── Managed key: AndroidKeyStore / in-memory (iOS)
      │     └── External key: SHA-256(secretKey) → kompatibel backend
      │
      ├── Hasher              ← SHA-256, SHA-512, HMAC-SHA-256
      │
      ├── SecureStorage       ← penyimpanan string sensitif (token, user ID)
      │     ├── Android: EncryptedSharedPreferences (AES256-GCM)
      │     └── iOS: in-memory placeholder (Keychain TODO)
      │
      └── EncodingUtils       ← Base64 & Hex encode/decode (pure Kotlin)
```

---

## Struktur File

```
libs/core_crypto/
└── src/
    ├── commonMain/kotlin/.../core_crypto/
    │   ├── crypto/
    │   │   ├── AesCryptor.kt          # expect class — enkripsi AES-256-CBC
    │   │   ├── CryptoKeyAlias.kt      # konstanta alias key
    │   │   └── Hasher.kt              # expect class — SHA-256, SHA-512, HMAC
    │   ├── di/
    │   │   ├── cryptoModule.kt        # expect val cryptoModule: Module
    │   │   └── secureStorageModule.kt # expect val secureStorageModule: Module
    │   ├── encoding/
    │   │   └── EncodingUtils.kt       # Base64 + Hex utilities
    │   ├── exception/
    │   │   ├── CryptoException.kt     # sealed class error crypto
    │   │   └── SecureStorageException.kt # sealed class error storage
    │   └── storage/
    │       ├── SecureStorage.kt       # expect class — KV storage sensitif
    │       └── SecureStorageKey.kt    # konstanta key penyimpanan
    │
    ├── androidMain/kotlin/.../core_crypto/
    │   ├── crypto/
    │   │   ├── AesCryptor.android.kt  # AndroidKeyStore + javax.crypto
    │   │   └── Hasher.android.kt      # MessageDigest + Mac (JCE)
    │   ├── di/
    │   │   ├── cryptoModule.android.kt
    │   │   └── secureStorageModule.android.kt
    │   └── storage/SecureStorage.android.kt  # EncryptedSharedPreferences
    │
    └── iosMain/kotlin/.../core_crypto/
        ├── crypto/
        │   ├── AesCryptor.ios.kt      # Pure-Kotlin AES-256-CBC
        │   └── Hasher.ios.kt          # Pure-Kotlin SHA-256, SHA-512, HMAC
        ├── di/
        │   ├── cryptoModule.ios.kt
        │   └── secureStorageModule.ios.kt
        └── storage/SecureStorage.ios.kt  # In-memory placeholder
```

---

## Komponen Utama

### AesCryptor

`expect class` untuk enkripsi/dekripsi AES-256-CBC. Dua mode operasi:

**Mode 1: Managed Key** — key dikelola platform (AndroidKeyStore / in-memory iOS).
```kotlin
// Tidak perlu tahu tentang key management
val cryptor = AesCryptor()
val encrypted = cryptor.encrypt("data sensitif", keyAlias = CryptoKeyAlias.PAYLOAD)
val decrypted = cryptor.decrypt(encrypted, keyAlias = CryptoKeyAlias.PAYLOAD)
```

**Mode 2: External Key** — key dari luar, kompatibel dengan backend.
```kotlin
// Key di-SHA-256 hash → 32 byte AES key, format output sama dengan EncryptionHelper backend
val encrypted = cryptor.encryptWithKey("payload", secretKey = "SECRET_KEY_DARI_ENV")
val decrypted = cryptor.decryptWithKey(encryptedFromBackend, secretKey = "SECRET_KEY_DARI_ENV")
```

**Semua method:**

| Method | Deskripsi |
|---|---|
| `encrypt(plainText, keyAlias)` | Enkripsi string → envelope `"<base64-IV>.<base64-ciphertext>"` |
| `decrypt(cipherText, keyAlias)` | Dekripsi envelope ke plaintext |
| `encryptBytes(data, keyAlias)` | Enkripsi ByteArray → `IV (16 byte) \|\| ciphertext` |
| `decryptBytes(data, keyAlias)` | Dekripsi ByteArray format `IV \|\| ciphertext` |
| `encryptWithKey(plainText, secretKey)` | Enkripsi dengan external key (backend-compatible) |
| `decryptWithKey(cipherText, secretKey)` | Dekripsi dengan external key |

---

### Hasher

`expect class` untuk hashing kriptografis. Semua output adalah hex string huruf kecil.

```kotlin
val hasher = Hasher()

val sha256 = hasher.sha256("input")        // 64 karakter hex
val sha512 = hasher.sha512("input")        // 128 karakter hex
val hmac = hasher.hmacSha256("data", "secret_key")  // 64 karakter hex
```

**Test vectors yang diverifikasi:**
- `sha256("")` = `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855`
- `sha512("abc")` = `ddaf35a193617abacc417349ae20413112e6fa4e89a97ea20a9eeee64b55d39a2192992a274fc1a836ba3c23a3feebbd454d4423643ce80e2a9ac94fa54ca49f`
- `hmacSha256("The quick brown fox jumps over the lazy dog", "key")` = `f7bc83f430538424b13298e6aa6fb143ef4d59a14946175997479dbc2d1a3cd8`

---

### SecureStorage

`expect class` untuk menyimpan string sensitif (token, user ID) secara aman.

```kotlin
val storage = SecureStorage(context)  // Android butuh Context, iOS tidak

storage.put(SecureStorageKey.ACCESS_TOKEN, "Bearer eyJhbGciOiJSUzI1NiJ9...")
val token = storage.get(SecureStorageKey.ACCESS_TOKEN)   // null jika tidak ada
storage.contains(SecureStorageKey.ACCESS_TOKEN)          // true/false
storage.remove(SecureStorageKey.REFRESH_TOKEN)
storage.clear()                                          // hapus semua data (saat logout)
```

**Key constants tersedia di `SecureStorageKey`:**
```kotlin
object SecureStorageKey {
    const val ACCESS_TOKEN = "ACCESS_TOKEN"
    const val REFRESH_TOKEN = "REFRESH_TOKEN"
    const val USER_ID = "USER_ID"
}
```

---

### EncodingUtils

Utility object untuk Base64 dan Hex encoding/decoding. Pure Kotlin, tidak ada dependency platform.

```kotlin
// Base64
val encoded: String = EncodingUtils.encodeBase64(byteArray)
val decoded: ByteArray = EncodingUtils.decodeBase64(base64String)

// Hex
val hex: String = EncodingUtils.encodeHex(byteArray)    // contoh: "a3f0..."
val bytes: ByteArray = EncodingUtils.decodeHex(hexString)
```

> Menggunakan `kotlin.io.encoding.Base64` dari stdlib (requires `@OptIn(ExperimentalEncodingApi::class)`).

---

### CryptoKeyAlias

Konstanta untuk alias managed key di `AesCryptor`.

```kotlin
object CryptoKeyAlias {
    const val DEFAULT = "uangku_crypto_default"
    const val PAYLOAD = "uangku_crypto_payload"
    const val LOCAL_DATA = "uangku_crypto_local"
}
```

Setiap alias punya key tersendiri di AndroidKeyStore / in-memory map. Gunakan alias berbeda untuk data yang berbeda (e.g., gunakan `PAYLOAD` untuk data yang akan dikirim ke backend, `LOCAL_DATA` untuk data lokal).

---

### CryptoException

Sealed class untuk semua error operasi kriptografi.

```kotlin
sealed class CryptoException(message: String, cause: Throwable?) : Exception(...) {
    data class EncryptionFailure(val rootCause: Throwable? = null) : ...   // Enkripsi gagal
    data class DecryptionFailure(val rootCause: Throwable? = null) : ...   // Dekripsi gagal (termasuk data rusak/tampered)
    data class KeyGenerationFailure(val rootCause: Throwable? = null) : .. // Gagal generate key
    data class KeyNotFound(val alias: String, ...) : ...                   // Key untuk alias tidak ditemukan
    data class IntegrityCheckFailure(val rootCause: Throwable? = null) : . // Data kemungkinan di-tamper
    data class NotAvailable(val rootCause: Throwable? = null) : ...        // Crypto service tidak tersedia
}
```

---

### SecureStorageException

Sealed class untuk semua error operasi `SecureStorage`.

```kotlin
sealed class SecureStorageException(message: String, cause: Throwable?) : Exception(...) {
    data class WriteFailure(val rootCause: Throwable? = null) : ...    // Gagal menulis
    data class ReadFailure(val rootCause: Throwable? = null) : ...     // Gagal membaca
    data class DeleteFailure(val rootCause: Throwable? = null) : ...   // Gagal menghapus
    data class NotAvailable(val rootCause: Throwable? = null) : ...    // Storage tidak tersedia
}
```

---

### Koin DI Modules

Dua module Koin yang harus didaftarkan oleh consumer project:

```kotlin
// cryptoModule — registrasi AesCryptor dan Hasher
expect val cryptoModule: Module

// secureStorageModule — registrasi SecureStorage
expect val secureStorageModule: Module
```

**Setup di app:**
```kotlin
startKoin {
    androidContext(this@Application)
    modules(
        cryptoModule,
        secureStorageModule,
        // ... module lain
    )
}
```

**Inject di consumer:**
```kotlin
class TokenRepository(
    private val storage: SecureStorage,
    private val cryptor: AesCryptor
)
```

> **Android:** `SecureStorage` memerlukan `Context` yang harus sudah ada di Koin graph sebelum diinstansiasi.

---

## Format Ciphertext (Backend Compatibility)

Format output `encryptWithKey` / `encrypt` kompatibel dengan backend `EncryptionHelper`:

```
"<base64(IV)>.<base64(ciphertext)>"
```

Contoh:
```
"dGhpcyBpcyBhIHRlc3Q=.YWJjZGVmZ2hpamtsbW5vcA=="
```

**Key derivation untuk external key:**
```
SHA-256(secretKey.toByteArray()) → 32 bytes → AES-256 key
```

Jadi jika backend menggunakan `EncryptionHelper.encrypt(data, "MY_SECRET")`, kamu bisa decrypt di mobile dengan:
```kotlin
cryptor.decryptWithKey(encryptedFromBackend, "MY_SECRET")
```

---

## Cara Penggunaan

### Simpan dan ambil token

```kotlin
class SessionRepository(
    private val storage: SecureStorage
) {
    suspend fun saveTokens(accessToken: String, refreshToken: String) {
        storage.put(SecureStorageKey.ACCESS_TOKEN, accessToken)
        storage.put(SecureStorageKey.REFRESH_TOKEN, refreshToken)
    }

    suspend fun getAccessToken(): String? = storage.get(SecureStorageKey.ACCESS_TOKEN)

    suspend fun logout() = storage.clear()
}
```

### Enkripsi payload untuk dikirim ke backend

```kotlin
class PayloadEncryptionHelper(private val cryptor: AesCryptor) {
    suspend fun encryptPayload(data: String): String {
        // Gunakan key yang sama dengan yang ada di backend
        return cryptor.encryptWithKey(data, secretKey = BuildConfig.PAYLOAD_ENCRYPTION_KEY)
    }

    suspend fun decryptPayload(encrypted: String): String {
        return cryptor.decryptWithKey(encrypted, secretKey = BuildConfig.PAYLOAD_ENCRYPTION_KEY)
    }
}
```

### Hash password / data untuk verifikasi

```kotlin
class AuthHelper(private val hasher: Hasher) {
    suspend fun hashPassword(password: String): String {
        return hasher.sha256(password)
    }

    suspend fun generateSignature(data: String, secret: String): String {
        return hasher.hmacSha256(data, secret)
    }
}
```

### Enkripsi data lokal

```kotlin
class LocalEncryptionService(private val cryptor: AesCryptor) {
    suspend fun encryptNote(note: String): String {
        // Key di-manage otomatis oleh AndroidKeyStore, tidak perlu tahu raw key
        return cryptor.encrypt(note, keyAlias = CryptoKeyAlias.LOCAL_DATA)
    }

    suspend fun decryptNote(encrypted: String): String {
        return cryptor.decrypt(encrypted, keyAlias = CryptoKeyAlias.LOCAL_DATA)
    }
}
```

---

## Implementasi Per Platform

### AesCryptor

| Aspek | Android | iOS |
|---|---|---|
| Key storage | AndroidKeyStore (hardware-backed) | In-memory `HashMap` (per-process, tidak persisten) |
| Enkripsi engine | `javax.crypto` (JCE) | Pure-Kotlin AES-256 (FIPS 197) |
| Cipher | `AES/CBC/PKCS5Padding` | Pure-Kotlin AES-CBC + PKCS7 |
| IV generation | `Cipher.init(ENCRYPT_MODE, key)` auto-generate | `kotlin.random.Random.nextBytes(16)` |
| External key derivation | `MessageDigest("SHA-256")` | `PureKotlinSha256.digest()` |

> **Catatan iOS:** Key managed hanya bertahan selama proses hidup (in-memory). Untuk persistent key, perlu implementasi Keychain. Ini sudah di-note sebagai TODO di codebase.

### Hasher

| Aspek | Android | iOS |
|---|---|---|
| SHA-256 | `MessageDigest.getInstance("SHA-256")` (JCE) | `PureKotlinSha256` object |
| SHA-512 | `MessageDigest.getInstance("SHA-512")` (JCE) | `PureKotlinSha512` object |
| HMAC-SHA-256 | `Mac.getInstance("HmacSHA256")` (JCE) | `PureKotlinHmac` object |

### SecureStorage

| Aspek | Android | iOS |
|---|---|---|
| Backend | `EncryptedSharedPreferences` (AndroidX Security) | In-memory `HashMap` |
| Enkripsi key | AES256-GCM (MasterKey via AndroidKeyStore) | — |
| Enkripsi value | AES256-GCM | — |
| Persistensi | Persisten (file terenkripsi) | Tidak persisten (hilang saat app kill) |
| File | `secure_storage` (SharedPreferences name) | — |

---

## Dependency

```kotlin
// build.gradle.kts (core_crypto)
commonMain {
    dependencies {
        implementation(libs.kotlin.stdlib)
        api(libs.bundles.koin)         // koin-core
        implementation(libs.kotlinx.coroutines.core)
    }
}
androidMain {
    dependencies {
        implementation(libs.androidx.security.crypto)  // EncryptedSharedPreferences
        implementation(libs.kotlinx.coroutines.android)
    }
}
```
