# core_crypto

Toolkit **Zero-Knowledge (2SKD)** untuk Android & iOS. Menyediakan primitif + facade yang
mengembalikan data class polos — **tanpa HTTP**. App yang memanggil endpoint backend; lib ini hanya
mengurus kripto, supaya reusable ke whitelabel/multiproject.

Kontrak interop = `docs/encryption.md` di repo backend (`Uangku-BE`). Nilai-nilai di sini sudah
diverifikasi byte-identik terhadap test vector backend (`docs/test-vectors/kdf-vectors.json`).

## Engine

`cryptography-kotlin` 0.6.0 (`cryptography-provider-optimal`): resolusi **per-algoritma** —
`AES.GCM`/`HKDF` via CryptoKit di iOS, `PBKDF2`/`RSA.OAEP` fallback ke Apple/CommonCrypto (CryptoKit
tidak punya keduanya). Di Android semuanya JCA. Semua operasi `suspend`, internal
`withContext(Dispatchers.Default)`; `SecureStorage` (disk) pakai `Dispatchers.IO` di Android.

## Kontrak derivasi (2SKD)

```
rawSalt   = base64_decode(salt)                                       // 16B dari /auth/salt
kdfPass   = PBKDF2-HMAC-SHA256(NFC(password), rawSalt, iterations, 32)
kdfSecret = HKDF-SHA256(ikm=secretKey_full_with_dashes, salt=rawSalt, info="uangku-secretkey-v1", 32)
unlockKey = kdfPass XOR kdfSecret
authKey   = base64(HKDF-SHA256(ikm=unlockKey, salt=null, info="uangku-auth-v1", 32))   // 44 char
```

- **NFC wajib di klien.** PHP tidak menormalisasi; tanpa `normalizeNfc()` password non-ASCII gagal
  login permanen. `iterations` = parameter runtime dari `/auth/salt`, bukan konstanta.
- `salt=null` (HKDF authKey) ≡ RFC 5869 `zeros(HashLen)` ≡ PHP `hash_hkdf(salt="")`.

## Paket & API inti

| Paket | Tipe utama | Peran |
|---|---|---|
| `key/` | `SecretBytes`, `UangkuSecretKey` | Wrapper zeroization + `toString()` non-bocor; generate/parse Secret Key |
| `text/` | `String.normalizeNfc()` | expect/actual (Android `java.text.Normalizer`, iOS `precomposedStringWithCanonicalMapping`) |
| `kdf/` | `KeyDerivationEngine` / `Pbkdf2HkdfKeyDerivationEngine`, `KdfParameters` | 2SKD |
| `cipher/` | `SealedData`, `SymmetricCipher` / `AesGcmCipher` | Container `ver‖iv‖ct‖tag`, AES-256-GCM |
| `asymmetric/` | `AsymmetricCryptor` / `RsaOaepCryptor` | RSA-OAEP-SHA256 4096, `maxPlaintextSizeBytes=446` |
| `envelope/` | `HybridEnvelope`, `HybridEnvelopeCodec`, `HybridEnvelopeCryptor` | `{v,ek,ct}` — `ct` pakai ulang `SealedData` |
| `account/` | `AccountCrypto` / `DefaultAccountCrypto` | Facade register/login/change/reset → data class polos |
| `storage/` | `SecureStorage` (interface) | Android Keystore / iOS Keychain |
| `di/` | `cryptoModule` (val), `secureStorageModule` (expect) | Koin |

## Container AES-GCM

`base64( ver(0x02) ‖ iv(12B) ‖ ciphertext(N) ‖ tag(16B) )`, min 29 byte, tanpa AAD. `SealedData`
memvalidasi versi + panjang; versi ≠ 0x02 → `UnsupportedVersion`, terlalu pendek/base64 rusak →
`InvalidCiphertextFormat`. `AesGcmCipher.open` memetakan kegagalan tag → `IntegrityCheckFailure`
(catatan jujur: kunci salah menghasilkan kegagalan yang sama — bukan bukti tamper eksak).

## Hybrid envelope

`dataKey = random(32)` → `ct = base64(SealedData(AES-GCM(dataKey, plaintext)))`,
`ek = base64(RSA-OAEP(publicKey, dataKey))`. Dipakai untuk **semua** field finansial dan untuk
membungkus family private key (RSA langsung mustahil: PEM RSA-4096 ~3.2KB ≫ limit 446 byte).

## Facade (`AccountCrypto`)

- `prepareRegistration(password, secretKey?, iterations)` → `RegistrationMaterial`
  (`secretKey`, `saltBase64`, `authKey`, `publicKeyBase64`, `wrappedPrivateKeyBase64`, `session`).
- `prepareLogin(...)` → `LoginPreparation(authKey, …)`; kirim `authKey` ke server; lalu
  `completeLogin(prep, EncryptedVault)` → `UnlockedSession` (unlockKey + privateKey). Password salah →
  `IntegrityCheckFailure`.
- `changeCredentials(session, …)` — re-wrap private key lama, **data preserved**.
- `resetCredentials(…)` — keypair baru, **data lama tak terbaca** (batasan E2EE jujur).

## DI

```kotlin
startKoin {
    androidContext(app)                 // Android: SecureStorage resolve Context dari graph
    modules(secureStorageModule, cryptoModule)
}
```

## Test & verifikasi

`commonTest` jalan di Android host DAN iOS simulator (`runTest`, bukan `runBlocking`).

```bash
./gradlew :libs:core_crypto:testAndroidHostTest       # JCA
./gradlew :libs:core_crypto:iosSimulatorArm64Test     # CryptoKit + CommonCrypto (butuh iOS runtime)
./gradlew :libs:core_crypto:allTests
```

Fixture `KdfVectors.kt` (di-generate dari `kdf-vectors.json`) mengunci byte-identity Set A–E:
`unlockKey`/`authKey` @600k, konvergensi NFC/NFD, `salt=null`==`salt=""`, buka container/PEM/envelope
buatan PHP. Gate ini = penutup kelas bug "Blocker #1" (derivasi klien ≠ server).

> **Status iOS:** kode compile+link di Kotlin/Native; eksekusi test iOS-simulator menunggu iOS runtime
> terinstall. `KeychainSecureStorage` memanggil Keychain asli (bukan lagi HashMap) tapi perilaku
> on-device belum diverifikasi runtime — lihat `vector-report.md`.
