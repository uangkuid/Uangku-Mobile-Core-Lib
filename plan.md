# Plan: Crypto Helper (Encrypt & Decrypt) untuk `core_crypto`

> Reusable cryptography helper lintas platform (Android & iOS) di dalam KMP module
> `libs/core_crypto`, untuk di-publish sebagai external library ke Maven.

---

## 1. Goal

Menyediakan API kriptografi yang **platform-agnostic**, aman, dan mudah dipakai consumer
project. Konsisten dengan komponen yang sudah ada di module ini (`SecureStorage`):
`expect/actual`, suspend functions, sealed exception hierarchy, dan Koin DI.

### Use case yang harus didukung

- **API payload encryption** — encrypt/decrypt request & response body.
- **Data at rest (local)** — encrypt data sebelum disimpan ke DB/file/preferences
  (melengkapi `SecureStorage` yang menyimpan key, bukan payload besar).
- **Hashing / integrity** — SHA & HMAC untuk signature/checksum.
- **General-purpose utility** — helper generik + encoding (Base64/Hex).

---

## 2. Keputusan yang sudah final (hasil diskusi)

| Topik          | Keputusan                                                                                                                           |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------|
| Tipe kripto    | Symmetric (AES) + Asymmetric (RSA/EC) + Hashing (SHA/HMAC) + Base64/Hex encoding                                                    |
| Key management | **Auto-generate + simpan native** (Android Keystore / iOS Keychain). Consumer tidak pernah handle raw key.                          |
| Implementasi   | **Native `expect/actual`** — Android `javax.crypto` + Keystore, iOS CommonCrypto/Security framework. Konsisten dgn `SecureStorage`. |
| Error handling | **Throw sealed exception** — buat `CryptoException` hierarchy (mirror `SecureStorageException`).                                    |
| API shape      | **Suspend functions**, **String ↔ String (Base64)** sebagai bentuk utama. `ByteArray` sebagai overload internal/lanjutan.           |
| Publishing     | Dibahas di fase akhir (lihat §11). Desain API & implementasi crypto didahulukan.                                                    |

---

## 3. Keputusan yang masih terbuka

### 3.1 AES mode — REKOMENDASI: **AES-256-GCM**

- **AES-GCM (recommended)**: authenticated encryption (anti-tamper bawaan), tidak butuh
  HMAC terpisah, IV 12-byte, auth tag 16-byte. Output envelope: `[IV (12)] + [ciphertext + tag]`,
  lalu Base64. Didukung Android (API 24+ via `AES/GCM/NoPadding`) & iOS (CommonCrypto
  `CCCryptorGCM` / atau CryptoKit `AES.GCM` bila min iOS 13+).
- **AES-CBC + HMAC (alternatif)**: lebih klasik & kompatibel backend lama, tapi lebih banyak
  kode (manual HMAC, padding, encrypt-then-MAC) dan rawan salah implementasi.

> **Action needed:** konfirmasi GCM. Jika ada backend yang sudah menetapkan format ciphertext,
> beri tahu supaya format envelope-nya disesuaikan.

### 3.2 Asymmetric (RSA/EC) — scope minimal di v1?

RSA/EC lintas platform jauh lebih kompleks (key format, padding `OAEP`, interop Keystore↔Keychain).
**Usulan:** masukkan ke fase tersendiri (Phase 4) setelah AES + Hashing stabil, agar v1 cepat rilis.

---

## 4. Struktur file (mengikuti konvensi module)

```
libs/core_crypto/src/
├─ commonMain/.../core_crypto/
│  ├─ crypto/
│  │  ├─ AesCryptor.kt            (expect class — encrypt/decrypt String & ByteArray)
│  │  ├─ Hasher.kt               (expect class — SHA-256/512, HMAC)
│  │  ├─ AsymmetricCryptor.kt    (expect class — Phase 4)
│  │  └─ CryptoKeyAlias.kt       (object konstanta alias key, mirror SecureStorageKey)
│  ├─ encoding/
│  │  └─ Base64.kt               (util Base64 + Hex)  // atau pakai kotlin.io.encoding
│  ├─ exception/
│  │  └─ CryptoException.kt       (sealed hierarchy)
│  └─ di/
│     └─ cryptoModule.kt          (expect val cryptoModule: Module)
├─ androidMain/.../core_crypto/
│  ├─ crypto/AesCryptor.android.kt   (javax.crypto.Cipher + AndroidKeyStore)
│  ├─ crypto/Hasher.android.kt
│  └─ di/cryptoModule.android.kt
└─ iosMain/.../core_crypto/
   ├─ crypto/AesCryptor.ios.kt       (Security framework / CommonCrypto)
   ├─ crypto/Hasher.ios.kt
   └─ di/cryptoModule.ios.kt
```

---

## 5. Desain API (draft)

### 5.1 AesCryptor

```kotlin
expect class AesCryptor {
    /** Encrypt plaintext → Base64 ciphertext envelope (IV + ciphertext + tag). */
    suspend fun encrypt(plainText: String, keyAlias: String = DEFAULT_ALIAS): String

    /** Decrypt Base64 ciphertext envelope → plaintext. */
    suspend fun decrypt(cipherText: String, keyAlias: String = DEFAULT_ALIAS): String

    suspend fun encryptBytes(data: ByteArray, keyAlias: String = DEFAULT_ALIAS): ByteArray
    suspend fun decryptBytes(data: ByteArray, keyAlias: String = DEFAULT_ALIAS): ByteArray
}
```

- Key dibuat otomatis (lazy) saat alias pertama kali dipakai, lalu disimpan di Keystore/Keychain.
- `keyAlias` memungkinkan multiple key (mis. satu untuk payload API, satu untuk data lokal).

### 5.2 Hasher

```kotlin
expect class Hasher {
    suspend fun sha256(input: String): String      // hex output
    suspend fun sha512(input: String): String
    suspend fun hmacSha256(input: String, secret: String): String
}
```

### 5.3 Encoding

`Base64`/`Hex` util. Pertimbangkan pakai `kotlin.io.encoding.Base64` (stdlib, common) agar
tidak perlu `expect/actual`. **Action:** verifikasi versi Kotlin 2.3.0 untuk status API ini.

---

## 6. Exception hierarchy

```kotlin
sealed class CryptoException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {
    data class EncryptionFailure(val rootCause: Throwable? = null) : CryptoException(...)
    data class DecryptionFailure(val rootCause: Throwable? = null) : CryptoException(...)
    data class KeyGenerationFailure(val rootCause: Throwable? = null) : CryptoException(...)
    data class KeyNotFound(val alias: String, val rootCause: Throwable? = null) :
        CryptoException(...)
    data class IntegrityCheckFailure(val rootCause: Throwable? = null) :
        CryptoException(...) // GCM tag mismatch / tamper
    data class NotAvailable(val rootCause: Throwable? = null) : CryptoException(...)
}
```

Semua exception platform (raw `GeneralSecurityException`, OSStatus, dll) dipetakan ke sini —
tidak ada raw exception yang bocor ke consumer. Mirror pola `SecureStorageException`.

---

## 7. Key management

- **Android**: `KeyGenParameterSpec` → `AndroidKeyStore`. AES-256, `BLOCK_MODE_GCM`,
  `ENCRYPTION_PADDING_NONE`. Key tidak pernah keluar dari Keystore (hardware-backed bila ada).
- **iOS**: simpan symmetric key sebagai item Keychain (`kSecClassKey` / generic password),
  hardware-backed via Secure Enclave bila memungkinkan. Generate via `SecRandomCopyBytes`.
- IV digenerate random per-operasi (jangan pernah reuse IV pada GCM).
- Alias default + dukungan multi-alias lewat parameter.

---

## 8. Dependency injection (Koin)

`expect val cryptoModule: Module` (mirror `secureStorageModule`). Android actual butuh
`Context`, iOS actual tanpa dependency tambahan. Consumer: `modules(cryptoModule)`.

---

## 9. Dependencies yang perlu ditambah

- Android: cukup `javax.crypto` (JDK) + `java.security.KeyStore` — **tidak perlu dependency baru**.
  `androidx.security.crypto` yang ada sekarang hanya dipakai SecureStorage.
- iOS: framework `Security` / `CryptoKit` — disediakan SDK native (tanpa entry di catalog).
- `kotlinx-coroutines` sudah ada di module.
- (Opsional) `kotlin.io.encoding.Base64` dari stdlib — perlu `@OptIn(ExperimentalEncodingApi)`
  bila masih experimental di Kotlin 2.3.0.

---

## 10. Testing

- `commonTest`: round-trip (encrypt→decrypt = input), tamper test (ubah ciphertext →
  `IntegrityCheckFailure`), hashing known-vectors (SHA-256/HMAC test vectors), Base64 edge cases.
- `androidHostTest` / `androidDeviceTest`: Keystore integration (butuh device/emulator).
- iOS: test via simulator target.
- Pakai vektor uji standar (NIST/RFC) untuk SHA & HMAC.

---

## 11. Publishing ke Maven (fase akhir)

- Pilih tooling (kandidat: `vanniktech maven-publish` untuk KMP — handle signing + XCFramework,
  vs GitHub Packages, vs private Nexus/Artifactory). **Belum diputuskan.**
- Tentukan `group`, `artifactId` (mis. `com.oratakashi.uangku.core:core-crypto`), versioning.
- Setup `mavenPublishing {}` + signing (GPG) bila ke Maven Central.
- Generate & publish XCFramework untuk konsumsi iOS.
- Dokumentasi README + contoh integrasi consumer.

---

## 12. Milestones / phasing

1. **Phase 1 — Fondasi**: `CryptoException`, `CryptoKeyAlias`, encoding util, `cryptoModule`
   skeleton.
2. **Phase 2 — AES (GCM)**: `AesCryptor` expect + Android & iOS actual + key management + tests. ←
   inti
3. **Phase 3 — Hashing**: `Hasher` (SHA/HMAC) expect/actual + tests.
4. **Phase 4 — Asymmetric**: `AsymmetricCryptor` (RSA/EC) — opsional, setelah inti stabil.
5. **Phase 5 — Publishing**: konfigurasi Maven + dokumentasi.

--

## 13. Docs & Reference

1. Backend Encryption documentation: `encryption.md`

---

## 14. Open questions (perlu jawaban sebelum coding)

1. **Base64**: boleh pakai `kotlin.io.encoding` (stdlib) atau harus `expect/actual` sendiri?
2. Min iOS deployment target? (menentukan CryptoKit `AES.GCM` [iOS 13+] vs CommonCrypto).
3. Konstanta alias domain-spesifik di `CryptoKeyAlias` (mis. `PAYLOAD`, `LOCAL_DATA`)?