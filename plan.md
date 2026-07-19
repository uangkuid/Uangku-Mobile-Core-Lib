# Plan: Rewrite `core_crypto` → Zero-Knowledge (2SKD)

> **Status: SUDAH DIIMPLEMENTASI** (17 Juli 2026). Blocker #1 terjawab backend (per-user raw salt 16B,
> lihat `Uangku-BE/response.md`); seluruh Fase 1–8 selesai. 63 test hijau di Android host termasuk gate
> byte-identity Set A–E @600k; iOS compile+link bersih (eksekusi iOS-sim menunggu runtime — lihat
> [`vector-report.md`](vector-report.md)).
>
> **Penyimpangan dari rencana ini (deliberate, ponytail):**
> - `sharing/SharedKeyCryptor` **dicoret** — delegasi tipis; app compose langsung `HybridEnvelopeCryptor`.
> - `unlock/UnlockKeyCache` **dicoret** — app pakai `SecureStorage` langsung.
> - Parameter **AAD dibuang** dari `SymmetricCipher` — kontrak backend tidak punya AAD.
> - `RsaKeyEncoding` enum **dibuang** — format wire tunggal (base64(PEM SPKI) + PKCS#8 PEM), di-hardcode.
> - `deriveEncryptionKey()`/`uangku-enc-v1` **dihapus** — backend konfirmasi dead.
>
> **Supersede:** dokumen ini menggantikan plan `core_crypto` sebelumnya (AES-GCM/CBC + Keystore alias),
> yang sudah tidak relevan karena kontrak backend berubah total.

---

## 1. Context

Backend Uangku sudah dirombak total ke **Zero-Knowledge / E2EE** dengan **Two-Secret-Key Derivation**
(password + Secret Key), sudah shipped di `main`. Kontraknya: PBKDF2-600k + HKDF-SHA256 → `unlockKey`
→ `authKey`, AES-256-GCM, RSA-OAEP-4096 — semua kripto di klien, server tidak pernah memegang
password, secret key, private key plaintext, maupun data finansial yang bisa dibaca.

`core_crypto` hari ini tidak punya satupun dari kontrak itu:

| Aspek | Sekarang | Dibutuhkan |
|---|---|---|
| Symmetric | AES-256-**CBC**, tanpa MAC (malleable, nol integrity) | AES-256-**GCM** (AEAD) |
| Key derivation | `SHA-256(secretKey)` — **tanpa salt, 1 iterasi** | PBKDF2 600k + HKDF + XOR (2SKD) |
| Asymmetric | ❌ tidak ada | RSA-OAEP-4096 |
| Hybrid envelope | ❌ tidak ada | `{v, ek, ct}` |
| iOS crypto | **AES/SHA/HMAC hand-rolled Kotlin murni** (~580 LOC, nol test), IV dari `kotlin.random.Random` (**bukan CSPRNG**), key AES di `HashMap` in-memory yang hilang saat proses mati | Platform-native, teraudit |
| iOS SecureStorage | **`mutableMapOf`** — KDoc mengklaim "Keychain (TEE/Secure Enclave)", isinya HashMap biasa | Keychain beneran |
| Test | `commonTest` dideklarasikan di `build.gradle.kts` tapi **kosong**; 16 test yang ada cuma jalan di Android host, cuma menyentuh jalur `encryptWithKey` | `commonTest` jalan di Android **dan** iOS |

Tidak ada apapun di repo ini yang mengonsumsi `core_crypto` (`libs/` = island; satu-satunya edge
adalah `androidApp → composeApp`), versi masih `0.0.1-SNAPSHOT`. Jadi ini **rewrite greenfield** —
tanpa migrasi, tanpa deprecation shim.

> Catatan historis: plan lama sudah **merekomendasikan** AES-256-GCM dan iOS CommonCrypto.
> Implementasi yang ter-ship melakukan keduanya secara berbeda (CBC + AES hand-rolled). Plan ini
> menutup gap itu sekaligus.

**Hasil akhir:** `core_crypto` jadi **toolkit ZK murni** — primitif + facade yang mengembalikan data
class polos. **Tanpa HTTP, tanpa Ktor, tanpa DTO endpoint.** App yang implement API-nya, supaya lib
tetap reusable ke whitelabel / multiproject.

**Kontrak sudah diverifikasi langsung dari source backend**
([github.com/uangkuid/Uangku-BE](https://github.com/uangkuid/Uangku-BE), `main`) — bukan dari
`docs/encryption.md` saja. Itu penting: ternyata **doc dan kode backend berbeda**, dan backend punya
**dua derivasi 2SKD yang saling bertentangan** di repo yang sama. Detail lengkap + semua pertanyaan
ada di [`faq-backend.md`](faq-backend.md).

---

## 2. Blocker — TERPECAHKAN ✅

Blocker #1 (HKDF salt `kdfSecret`) sudah diputuskan backend: **per-user raw salt 16 byte**, salt yang
sama dengan PBKDF2 (`response.md` §1). `KdfConstants.HKDF_SALT_SECRET_KEY` tidak jadi ada — salt hidup
di `KdfParameters`. Sudah dibuktikan byte-identik terhadap vector Set A @600k.

---

## 3. Keputusan teknis

| Aspek | Keputusan |
|---|---|
| Engine | **cryptography-kotlin 0.6.0** (`cryptography-core` + `cryptography-provider-optimal`) |
| API lama | **Hapus total** — tanpa deprecation |
| Layering | Toolkit murni: primitif + facade data-in/data-out. **Tanpa HTTP**, tanpa modul `core_auth` |
| JSON envelope | `kotlinx.serialization` scope `implementation` + `internal @Serializable` — parser JSON hand-rolled di lib security = ladang bug; scope `implementation` menjaga agar tidak bocor ke API consumer |
| Dispatcher | `Dispatchers.Default` (CPU-bound). **Bukan `Dispatchers.IO`** — tidak ada di Kotlin/Native pada coroutines 1.8.1 (baru ada 1.9.0) |

### 3.1 Kenapa cryptography-kotlin aman di iOS (diverifikasi ke source-nya)

Ini risiko terbesar dari pilihan library, dan sudah dicek langsung ke source `whyoleg/cryptography-kotlin`.
Resolusi provider **per-algoritma, bukan per-provider** — `CryptographySystemImpl` membangun
`CompositeProvider`:

```kotlin
override fun <A : CryptographyAlgorithm> getOrNull(identifier: CryptographyAlgorithmId<A>): A? =
    providers.firstNotNullOfOrNull { it.getOrNull(identifier) }
```

`cryptography-provider-optimal` mendaftarkan **keduanya** di target Apple:

| | CryptoKit (prio 110) | Apple/CommonCrypto (prio 120) | iOS resolve ke |
|---|---|---|---|
| AES.GCM | ✅ | ❌ | CryptoKit |
| HKDF | ✅ | ✅ | CryptoKit |
| PBKDF2 | ❌ | ✅ `CCPbkdf2` | Apple (fallback) |
| RSA.OAEP | ❌ | ✅ `SecRsa` | Apple (fallback) |

CryptoKit **tidak punya RSA maupun PBKDF2 sama sekali** — composite fallback inilah yang bikin ini
jalan. Asumsi ini load-bearing → **wajib di-assert di test** (Fase 1).

Terverifikasi juga:
- `AES.GCM cipher().encrypt()` mengembalikan **`IV(12) ‖ ct ‖ tag(16)`** persis → `byteArrayOf(0x02) + encrypt(...)` = container backend, **tanpa splicing manual**
- Format RSA SPKI/PKCS#8 jalan di iOS (`SecRsa.kt` melakukan ASN.1 wrapping sendiri di atas SecKey yang PKCS#1-only)
- `platform.Security` (Keychain) **tidak butuh .def cinterop custom**
- ⚠️ `RSA.OAEP` digest default = **SHA512** → wajib pass `SHA256` eksplisit
- ⚠️ Apple provider hard-check `publicExponent == 65537`

---

## 4. Kontrak backend (terverifikasi dari source)

Nilai-nilai ini **bukan** dari doc, tapi dari kode yang ter-ship — dipakai sebagai default implementasi.

| Item | Nilai | Sumber |
|---|---|---|
| Container | `base64(chr(0x02) ‖ iv(12B) ‖ ct(N) ‖ tag(16B))`, min len `1+12+16 = 29` | `EncryptionHelper.php:61,74-84` |
| AAD | **Tidak ada** (`openssl_encrypt(..., $iv, $tag, '', 16)` — param ke-7 kosong) | `EncryptionHelper.php:55` |
| Base64 | **Standard + padded**, strict decode. Match `kotlin.io.encoding.Base64.Default` | `EncryptionHelper.php:61,73` |
| HKDF ikm | **String penuh dengan dash**: `'UANGKU-ABC123-DEF456-GHI78-JKL90-MNO12'` | `UserSeeder.php:42`, `AuthControllerTest.php:23` |
| HKDF salt (`authKey`) | `''` → RFC 5869 zeros(HashLen). **cryptography-kotlin `salt = null` match** ✅ | `EncryptionHelper.php:106-109` |
| HKDF salt (`kdfSecret`) | 🔴 **KONTRADIKSI** — lihat [`faq-backend.md`](faq-backend.md) #1 | — |
| `salt` transport | `base64(random_bytes(16))`; klien `base64_decode` dulu sebelum PBKDF2 | `UserSeeder.php:38,61`, `AuthControllerTest.php:22` |
| `public_key` | **`base64(PEM SPKI)`** — double-encoded | `UserSeeder.php:52,62` |
| `wrapped_private_key` | `aesGcmEncrypt(**PKCS#8 PEM string**, unlockKey)` → container yang sama. **PEM, bukan DER** | `UserSeeder.php:51,54` |
| `iterations` | Konstanta `600000`, dikirim via `/auth/salt`. Tidak ada kolom per-user | `AuthServiceImplement.php:176` |
| Validasi server | **Nihil** — `required|string` saja untuk `salt`/`auth_key`/`public_key`/`wrapped_private_key`. Semua opaque | `AuthController.php:100-103` |

### 4.1 Kontrak derivasi

```
kdfPass   = PBKDF2-HMAC-SHA256(NFC(password), salt, iterations, 32)
kdfSecret = HKDF-SHA256(ikm=secretKey, salt=???, info="uangku-secretkey-v1", L=32)   ← ??? = blocker
unlockKey = kdfPass XOR kdfSecret
authKey   = base64(HKDF-SHA256(ikm=unlockKey, salt='', info="uangku-auth-v1", L=32))
```

### 4.2 Family sharing — spec §9 mustahil, pakai hybrid envelope

`docs/encryption.md` §9 menulis `wrapped_own = RSA-OAEP(owner.pub, famPriv)`. Itu **mustahil secara
matematis**: RSA-4096 OAEP-SHA256 max plaintext = `512 − 2×32 − 2` = **446 byte**, sementara PKCS#8
PEM private key RSA-4096 ≈ **3.200 byte**.

Untungnya `FamilyServiceImplement` menyimpan `wrapped_private_key` **sepenuhnya opaque** — server
tidak pernah melakukan RSA sendiri. Jadi klien pakai **hybrid envelope** (§8) dan semuanya jalan.
Bukan blocker implementasi; hanya teks spec yang salah (sudah dilaporkan di `faq-backend.md`).

---

## 5. Yang dihapus

```
commonMain:  Platform.kt, crypto/AesCryptor.kt, crypto/Hasher.kt, crypto/CryptoKeyAlias.kt
androidMain: Platform.android.kt, crypto/AesCryptor.android.kt, crypto/Hasher.android.kt,
             di/cryptoModule.android.kt
iosMain:     Platform.ios.kt, crypto/AesCryptor.ios.kt (PureKotlinAes),
             crypto/Hasher.ios.kt (PureKotlinSha256/512/Hmac), di/cryptoModule.ios.kt,
             storage/SecureStorage.ios.kt
androidHostTest: crypto/AesCryptorTest.kt, crypto/HasherTest.kt, ExampleUnitTest.kt
catalog:     androidx-security-crypto (core_crypto satu-satunya consumer; API-nya juga deprecated)
```

**`Hasher` dihapus, bukan diganti** — cryptography-kotlin sudah menyubsumsi
(`provider.get(SHA256).hasher()`, `provider.get(HMAC)`), dan tidak ada di kontrak baru yang butuh API
hex-SHA publik. Karena modul `api`-expose provider-nya, consumer yang butuh hashing dapat versi
teraudit gratis. Menghapus ~580 LOC kripto hand-rolled dari iOS.

`EncodingUtils` dan `SecureStorageException` **dipertahankan apa adanya**.

---

## 6. Arsitektur baru

```
commonMain/.../core_crypto/
├── encoding/EncodingUtils.kt              KEEP
├── exception/CryptoException.kt           REWRITE
├── exception/SecureStorageException.kt    KEEP
├── key/{SecretBytes, UangkuSecretKey}.kt
├── text/TextNormalizer.kt                 internal expect fun String.normalizeNfc()
├── kdf/{KdfParameters, KdfConstants, KeyDerivationEngine,
│        Pbkdf2HkdfKeyDerivationEngine, DerivedCredentials}.kt
├── cipher/{SealedData, SymmetricCipher, AesGcmCipher}.kt
├── asymmetric/{RsaKeyEncoding, RsaKeyPairMaterial, AsymmetricCryptor, RsaOaepCryptor}.kt
├── envelope/{HybridEnvelope, HybridEnvelopeCodec, HybridEnvelopeCryptor}.kt
├── sharing/SharedKeyCryptor.kt            family — delegasi ke hybrid, BUKAN RSA langsung
├── account/{AccountCrypto, DefaultAccountCrypto, AccountMaterial, UnlockedSession}.kt
├── storage/SecureStorage.kt               REWRITE → interface polos (bukan expect class)
├── unlock/{UnlockKeyCache, SecureStorageUnlockKeyCache}.kt
└── di/{cryptoModule (val biasa), secureStorageModule (tetap expect)}.kt

androidMain: text/TextNormalizer.android.kt, storage/AndroidKeystoreSecureStorage.kt,
             di/secureStorageModule.android.kt
iosMain:     text/TextNormalizer.ios.kt, storage/KeychainSecureStorage.kt,
             di/secureStorageModule.ios.kt
commonTest:  support/, provider/, cipher/, key/, text/, kdf/, asymmetric/, envelope/,
             account/, unlock/    ← jalan di Android host DAN iOS simulator
```

Hanya **dua** pasang expect/actual tersisa:
- `normalizeNfc()` — Kotlin/Native tidak punya normalizer Unicode (Android: `java.text.Normalizer`; iOS: `NSString.precomposedStringWithCanonicalMapping`)
- `secureStorageModule` — Android butuh `androidContext()`

`cryptoModule` **berhenti** jadi expect/actual — kedua actual-nya sudah byte-identical hari ini, dan
sekarang tidak ada tipe kripto yang butuh konstruksi platform-specific.

`SecureStorage` jadi **interface**, bukan `expect class` — `expect class` tidak bisa di-fake di
`commonTest`, interface bisa.

### 6.1 API inti

```kotlin
// key/ — SecretBytes: zeroization best-effort + toString() yang TIDAK PERNAH bocorkan byte
class SecretBytes private constructor(private var value: ByteArray?) {
    fun copyBytes(): ByteArray; fun destroy()
    override fun toString() = "SecretBytes(size=$size, destroyed=$isDestroyed)"
    companion object { fun wrap(bytes: ByteArray): SecretBytes; fun copyOf(...): SecretBytes }
}

class UangkuSecretKey private constructor(val formatted: String) {
    override fun toString() = "UangkuSecretKey(****)"
    companion object { fun generate(): UangkuSecretKey; fun parse(v: String): UangkuSecretKey
                       val SEGMENT_LENGTHS = listOf(6, 6, 5, 5, 5) }
}

// kdf/ — iterations HIDUP DI KdfParameters, TIDAK PERNAH konstanta di jalur derivasi.
// Bikin test cepat DAN bikin kita jujur terhadap /auth/salt yang parameter-driven.
// (Backend AuthControllerTest sendiri melakukan ini: derive dengan iterations=1000.)
data class KdfParameters(val salt: ByteArray, val iterations: Int) {
    companion object { const val DEFAULT_ITERATIONS = 600_000; const val SALT_SIZE_BYTES = 16 }
}

interface KeyDerivationEngine {
    suspend fun deriveUnlockKey(password: String, secretKey: UangkuSecretKey, params: KdfParameters): SecretBytes
    suspend fun deriveAuthKey(unlockKey: SecretBytes): String              // base64, 44 char
    suspend fun deriveEncryptionKey(unlockKey: SecretBytes): SecretBytes   // uangku-enc-v1 — pending faq #3
    suspend fun deriveCredentials(...): DerivedCredentials
}

// cipher/ — container: ver(1)=0x02 | iv(12) | ct(N) | tag(16), base64. Byte-exact dgn backend.
class SealedData private constructor(val bytes: ByteArray) {
    companion object { const val VERSION: Byte = 0x02; const val OVERHEAD_BYTES = 29
                       fun fromRaw(b: ByteArray): SealedData; fun fromBase64(v: String): SealedData }
}

interface SymmetricCipher {
    suspend fun seal(key: SecretBytes, plaintext: ByteArray, aad: ByteArray? = null): SealedData
    suspend fun open(key: SecretBytes, sealed: SealedData, aad: ByteArray? = null): ByteArray
}

// asymmetric/ — default sudah dikunci ke temuan source:
//   public_key  = base64(PEM SPKI)            ← UserSeeder:52,62
//   privateKey  = PKCS#8 PEM, di-wrap AES-GCM ← UserSeeder:51,54
class RsaOaepCryptor(
    private val provider: CryptographyProvider = CryptographyProvider.Default,
    private val keySizeBits: Int = 4096,
    private val publicKeyEncoding: RsaPublicKeyEncoding = RsaPublicKeyEncoding.PEM_SPKI_BASE64,
    private val privateKeyEncoding: RsaPrivateKeyEncoding = RsaPrivateKeyEncoding.PEM_PKCS8,
) : AsymmetricCryptor {
    override val maxPlaintextSizeBytes: Int   // 446 — publik supaya caller tidak menabrak diam-diam
}

// envelope/ + sharing/ — family: RSA-OAEP TIDAK BISA wrap RSA private key (§4.2), harus hybrid.
data class HybridEnvelope(val version: Int, val encryptedKey: String, val ciphertext: String)
interface HybridEnvelopeCryptor {
    suspend fun seal(publicKey: String, plaintext: ByteArray): HybridEnvelope
    suspend fun open(privateKey: SecretBytes, envelope: HybridEnvelope): ByteArray
}
interface SharedKeyCryptor {                       // delegasi ke HybridEnvelopeCryptor
    suspend fun createSharedKeyPair(ownerPublicKey: String): SharedKeyMaterial
    suspend fun wrapForMember(memberPublicKey: String, sharedPrivateKey: SecretBytes): String
    suspend fun unwrap(memberPrivateKey: SecretBytes, wrapped: String): SecretBytes
    suspend fun rotate(memberPublicKeys: Map<String, String>): SharedKeyRotation
}

// account/ — facade. Login dua langkah: HTTP tetap di luar lib, tapi cocok dgn round-trip asli.
interface AccountCrypto {
    suspend fun prepareRegistration(password: String, secretKey: UangkuSecretKey? = null,
                                    iterations: Int = KdfParameters.DEFAULT_ITERATIONS): RegistrationMaterial
    suspend fun prepareLogin(password: String, secretKey: UangkuSecretKey, challenge: SaltChallenge): LoginPreparation
    suspend fun completeLogin(preparation: LoginPreparation, vault: EncryptedVault): UnlockedSession
    suspend fun changeCredentials(session: UnlockedSession, newPassword: String,
                                  newSecretKey: UangkuSecretKey, iterations: Int): CredentialChangeMaterial
    suspend fun resetCredentials(newPassword: String, newSecretKey: UangkuSecretKey,
                                 iterations: Int): CredentialResetMaterial
}

// storage/ + unlock/
interface SecureStorage {                          // interface, BUKAN expect class → bisa di-fake
    suspend fun put(key: String, value: String); suspend fun get(key: String): String?
    suspend fun remove(key: String); suspend fun clear(); suspend fun contains(key: String): Boolean
}
interface UnlockKeyCache {                         // hardening ada di SecureStorage, bukan di sini
    suspend fun store(unlockKey: SecretBytes); suspend fun load(): SecretBytes?
    suspend fun clear(); suspend fun isPresent(): Boolean
}
```

`RegistrationMaterial` mengembalikan `secretKey` + `salt` + `authKey` + `publicKey` +
`wrappedPrivateKey` + `session` sebagai **data class polos** — app yang memetakan ke DTO-nya sendiri.
Itu batas "tanpa HTTP"-nya.

### 6.2 Panggilan cryptography-kotlin

```kotlin
val kdfPass = provider.get(PBKDF2).secretDerivation(
    digest = SHA256, iterations = params.iterations, outputSize = 256.bits, salt = params.salt
).deriveSecretToByteArray(password.normalizeNfc().encodeToByteArray())

val kdfSecret = provider.get(HKDF).secretDerivation(
    digest = SHA256, outputSize = 256.bits,
    salt = KdfConstants.HKDF_SALT_SECRET_KEY.encodeToByteArray(),   // ← BLOCKER, lihat faq-backend #1
    info = KdfConstants.INFO_SECRET_KEY.encodeToByteArray()
).deriveSecretToByteArray(secretKey.formatted.encodeToByteArray())  // string penuh + dash ✅

val unlockKey = ByteArray(32) { kdfPass[it] xor kdfSecret[it] }

// salt = null → RFC 5869 zeros(HashLen) ≡ PHP hash_hkdf salt='' ✅
val authKey = EncodingUtils.encodeBase64(
    provider.get(HKDF).secretDerivation(digest = SHA256, outputSize = 256.bits, salt = null,
        info = KdfConstants.INFO_AUTH.encodeToByteArray()).deriveSecretToByteArray(unlockKey))

// AES-GCM → ver|iv|ct|tag tanpa splicing, tanpa AAD ✅
val aesKey = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, keyBytes)
val sealed = SealedData.fromRaw(byteArrayOf(SealedData.VERSION) + aesKey.cipher().encrypt(plaintext))

// RSA-OAEP — PERHATIAN: digest default SHA512, WAJIB pass SHA256 eksplisit.
val pair = provider.get(RSA.OAEP).keyPairGenerator(keySize = 4096.bits, digest = SHA256).generateKey()
```

### 6.3 Exception

```kotlin
sealed class CryptoException(message: String, cause: Throwable? = null) : Exception(message, cause) {
    data class EncryptionFailure(...); data class DecryptionFailure(...)
    data class KeyGenerationFailure(...); data class KeyDerivationFailure(...)   // NEW
    data class IntegrityCheckFailure(...)                                        // sekarang NYATA (GCM)
    data class InvalidSecretKeyFormat(val reason: String)   // NEW — reason saja, JANGAN nilai aslinya
    data class InvalidCiphertextFormat(val reason: String)  // NEW
    data class UnsupportedVersion(val version: Int)         // NEW
    data class NotAvailable(val algorithm: String? = null, val rootCause: Throwable? = null)
}
```

`KeyNotFound(alias)` **dihapus** — `CryptoKeyAlias` dan Keystore-managed alias sudah tidak ada.

`InvalidSecretKeyFormat` membawa `reason`, **bukan** nilai bermasalahnya — secret key di message
exception berakhir di Crashlytics.

`IntegrityCheckFailure` dilempar di `AesGcmCipher.open()`. Karena `SealedData.fromRaw` sudah
memvalidasi versi + panjang secara struktural, exception apapun dari `decrypt()` pada container
well-formed hampir pasti tag failure. **Yang harus jujur di KDoc:** kunci salah juga menghasilkan tag
failure — jangan mengimplikasikan deteksi tamper itu eksak.

**Aturan doc ke depan:** hanya dokumentasikan `@throws` untuk exception yang benar-benar dilempar,
dan satu test per exception yang didokumentasikan. KDoc sekarang mendokumentasikan
`KeyGenerationFailure`/`KeyNotFound` di method yang tidak mungkin melemparnya — itu asal-muasal fiksi
di dokumentasi.

### 6.4 Threading & performa

Semua op publik `suspend`, internal `withContext(Dispatchers.Default)`. `SecureStorage` tetap
`Dispatchers.IO` di Android (disk beneran).

**Tanpa progress API** — `deriveSecretToByteArray` atomik, tidak ada callback progress; API progress
apapun cuma timer palsu. App tampilkan spinner indeterminate.

**Catat di KDoc:** `withContext` **tidak** menginterupsi derivasi di tengah jalan; login yang
di-cancel tetap membakar 1–5 detiknya.

---

## 7. Fase

| Fase | Isi | Depends |
|---|---|---|
| **0** | ✅ Tulis `plan.md` + `faq-backend.md`. Kirim pertanyaan ke tim backend | — |
| **1** | **Spike & de-risk** — catalog + build wiring, throwaway test | paralel dgn 0 |
| **2** | `exception/`, `key/`, `text/` (+actuals), `cipher/`. **Hapus file lama di sini** (modul tetap compile) | 1 |
| **3** | **2SKD** — `kdf/`. **Gate: `authKey` byte-identical dgn PHP. Tidak ada yang ship sebelum hijau** | 2, **faq #1** |
| **4** | RSA + wrapped_private_key — `asymmetric/` | 2 |
| **5** | Hybrid envelope + family sharing — `envelope/`, `sharing/` | 4 |
| **6** | Storage + unlock cache — `storage/`, `unlock/`. **Paralel dgn 3/4/5** (cuma butuh `SealedData`) | 2 |
| **7** | Facade — `account/` | 3, 4, 5 |
| **8** | DI, docs, publish | 7 |

Critical path: 1 → 2 → 3 → 4 → 5 → 7 → 8. Fase 4 dan 6 free parallelism — sudah tidak diblokir
apapun karena kontraknya terverifikasi dari source.

### 7.1 Fase 1 — Spike (gate ke semua fase lain)

Throwaway `commonTest` yang membuktikan di **Android host DAN iOS simulator**:

- **`getOrNull(PBKDF2/HKDF/RSA.OAEP/AES.GCM) != null` di iOS** ← **gate utama**, memvalidasi tabel composite provider di §3.1
- `CryptographyProvider.Default.name`
- Layout lock GCM = `ver|iv|ct|tag` (lihat §8)
- **Wall-clock RSA-4096 keygen di device asli** — lihat risiko di bawah
- Limit OAEP = 446 (encrypt 446 ✓ / 447 ✗)
- Fixture RSA PEM lintas-platform Android↔iOS
- **Vector RFC 5869 + PBKDF2** — membuktikan *wiring kita* independen dari backend, jadi mismatch belakangan jelas-jelas *spec mereka*. **Bisa dikerjakan sekarang**, tanpa menunggu jawaban backend

**Belum terverifikasi → task Fase 1:** apakah DCE Kotlin/Native mempertahankan hook registrasi
`@EagerInitialization` provider di scope `implementation`. Mitigasi: pakai
`api(libs.bundles.cryptography)`, lalu assert `CryptographyProvider.Default.name` di test iOS simulator.

> ⚠️ **Risiko performa yang sebenarnya bukan PBKDF2 — tapi RSA-4096 keygen.**
> Di iOS, `SecKeyCreateRandomKey` 4096-bit adalah pencarian prima probabilistik: sangat bervariasi,
> bisa 5–30 detik+ di device lama, **kemungkinan lebih parah dari PBKDF2 600k**. Memukul flow
> register dan reset. Ukur di **iPhone asli dan Android kelas minSdk-24 asli** — simulator jalan di
> CPU host dan akan berbohong. Kalau tidak layak, fallback 3072-bit — dan itu **perubahan kontrak
> backend**, jadi cari tahu sedini mungkin.

### 7.2 Catalog

```toml
[versions]
cryptography = "0.6.0"

[libraries]
cryptography-core = { module = "dev.whyoleg.cryptography:cryptography-core", version.ref = "cryptography" }
cryptography-provider-optimal = { module = "dev.whyoleg.cryptography:cryptography-provider-optimal", version.ref = "cryptography" }
kotlinx-coroutines-test = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-test", version.ref = "kotlinx-coroutines" }

[bundles]
cryptography = ["cryptography-core", "cryptography-provider-optimal"]
```

`api(libs.bundles.cryptography)` di commonMain — `api`, bukan `implementation`, sebagai penjaga
terhadap DCE Kotlin/Native atas hook registrasi provider. `kotlinx-io` datang transitif via
`cryptography-core`.

---

## 8. Verifikasi

**`commonTest` jalan di kedua platform** — ini poin utamanya. `runBlocking` JVM-only → semua test
pakai `runTest` (`kotlinx-coroutines-test`, belum ada di catalog).

```bash
./gradlew :libs:core_crypto:testAndroidHostTest       # JVM/JCA
./gradlew :libs:core_crypto:iosSimulatorArm64Test     # CryptoKit + CommonCrypto  ← wajib
./gradlew :libs:core_crypto:allTests
./gradlew :libs:core_crypto:lint
./gradlew :libs:core_crypto:publishToMavenLocal       # smoke consumer
```

CI harus macOS untuk paruh iOS-nya.

**Test yang paling berharga (bukan sekadar round-trip):**

| Test | Yang ditangkap |
|---|---|
| `getOrNull(RSA.OAEP/PBKDF2) != null` di **iOS sim** | Asumsi composite-provider salah → seluruh pendekatan runtuh |
| `derive("café"_NFC) == derive("café"_NFD)` di **Android host + iOS sim** | Normalizer iOS rusak → user non-ASCII terkunci permanen. **Test paling bernilai di suite** |
| **Swap password ↔ secretKey → unlockKey berbeda** | Konstruksi 2SKD yang tidak sengaja simetris |
| `kdfPass != unlockKey && kdfSecret != unlockKey` | XOR jadi identity (satu faktor tidak berkontribusi) |
| **Enkripsi plaintext sama 2× → container berbeda** | IV non-CSPRNG. Persis kelas bug kode iOS lama (`kotlin.random.Random`) |
| Layout lock `ver\|iv\|ct\|tag` (di bawah) | Upgrade library mengubah layout diam-diam |
| Vector 600k `authKey` == referensi PHP | **Gate Fase 3.** Tanpa ini, semuanya spekulasi |

**Layout lock** — membuktikan layout *dan* menangkap regresi library:

```kotlin
@OptIn(DelicateCryptographyApi::class)
@Test fun container_layout_is_ver_iv_ct_tag() = runTest {
    val pt = "hello".encodeToByteArray()
    val sealed = cipher.seal(key, pt)
    assertEquals(pt.size + SealedData.OVERHEAD_BYTES, sealed.bytes.size)
    assertEquals(0x02, sealed.bytes[0])
    // cross-check: membuktikan iv|ct|tag dan BUKAN iv|tag|ct
    val aes = provider.get(AES.GCM).keyDecoder().decodeFromByteArray(AES.Key.Format.RAW, key.copyBytes())
    assertContentEquals(pt, aes.cipher().decryptWithIv(sealed.iv, sealed.ciphertextWithTag))
}
```

Fallback kalau gagal: `CryptographyRandom.nextBytes(12)` + `encryptWithIv` + splice manual.

**600k lambat → diselesaikan lewat parameterisasi.** Karena `iterations` hidup di `KdfParameters`:
- `KeyDerivationTest` — ~15 test di `iterations = 1_000`, milidetik. **Semua logika di sini.**
- `KeyDerivationVectorTest` — ~4 test di `600_000`, ~1–2s JVM / ~3–5s iOS sim. Default 60s `runTest` cukup.

**Tamper suite GCM:** bit-flip di ct/tag/iv → `IntegrityCheckFailure`; tag terpotong →
`InvalidCiphertextFormat`; kunci salah → `IntegrityCheckFailure`; version byte `0x01` →
`UnsupportedVersion`; AAD mismatch → `IntegrityCheckFailure`.

**Lainnya:** `UangkuSecretKey.generate()` × 1000 → semua unik + format valid; `SecretBytes.toString()`
tidak pernah mengandung hex; `HybridEnvelopeCodec` toleran whitespace / urutan field / field tak
dikenal, `v != 2` → `UnsupportedVersion`; `AccountCryptoFlowTest` register→login→change→reset melawan
fake, plus "password salah gagal unlock" dan "reset membuat ciphertext lama tak terbaca".

**Interop nyata (Fase 8, kalau memungkinkan):** jalankan backend lokal (`DB_CONNECTION=sqlite` +
`php artisan serve`), register dari test KMP, login balik. Ini satu-satunya bukti end-to-end bahwa
2SKD-nya benar — semua yang lain masih bisa self-consistent-tapi-salah, persis seperti test backend
hari ini (lihat `faq-backend.md` #1).

---

## 9. Dokumentasi (Fase 8)

- Tulis ulang `docs/core_crypto.md` — 410 baris yang mendokumentasikan API yang sudah dihapus
- **`encryption.md` di root sudah usang** — mendokumentasikan backend **lama** (AES-CBC, RSA-2048, derivasi XOR, bcrypt secret key) yang sudah disupersede total. Ganti dengan pointer ke `docs/encryption.md` backend, atau hapus
- `CLAUDE.md` — `:52` menunjuk `encryption.md` sebagai sumber kebenaran interop core_crypto; perbarui juga tabel maturity `:39` dan cross-cutting pattern `:87`
- `README.md` — sinkronkan deskripsi core-crypto

---

## 10. Critical files

| File | Peran |
|---|---|
| [`faq-backend.md`](faq-backend.md) | **Baca duluan.** Blocker + semua pertanyaan ke tim backend |
| [gradle/libs.versions.toml](gradle/libs.versions.toml) | Semua dep baru masuk sini, tidak pernah inline |
| [libs/core_crypto/build.gradle.kts](libs/core_crypto/build.gradle.kts) | `api(bundles.cryptography)`, buang `androidx.security.crypto`, wiring commonTest |
| `libs/core_crypto/.../kdf/Pbkdf2HkdfKeyDerivationEngine.kt` | **Gate byte-identity.** Titik paling berisiko di proyek |
| `libs/core_crypto/.../cipher/SealedData.kt` | Container wire `ver\|iv\|ct\|tag` |
| `libs/core_crypto/.../account/DefaultAccountCrypto.kt` | Facade — batas "tanpa HTTP" |
| [encryption.md](encryption.md) | **Usang** — mendokumentasikan kontrak backend yang sudah disupersede |
