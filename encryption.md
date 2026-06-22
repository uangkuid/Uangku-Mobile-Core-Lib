# Encryption Documentation — Uangku Backend

Dokumen ini menjelaskan seluruh mekanisme enkripsi yang digunakan di backend Uangku, mulai dari algoritma, secret key, cara encrypt/decrypt, hingga alur lengkap per fitur.

---

## Daftar Isi

1. [Ringkasan Algoritma](#1-ringkasan-algoritma)
2. [Environment Variables & Secret Keys](#2-environment-variables--secret-keys)
3. [AES-256-CBC (Symmetric Encryption)](#3-aes-256-cbc-symmetric-encryption)
4. [RSA-2048 (Asymmetric Encryption)](#4-rsa-2048-asymmetric-encryption)
5. [Bcrypt (Password & Secret Key Hashing)](#5-bcrypt-password--secret-key-hashing)
6. [JWT Token Signing](#6-jwt-token-signing)
7. [Key Derivation (getUsersEncryptKey & getFamilyEncryptionKey)](#7-key-derivation)
8. [Secret Key Format](#8-secret-key-format)
9. [Alur Enkripsi Per Fitur](#9-alur-enkripsi-per-fitur)
10. [Response Encryption (Opsional)](#10-response-encryption-opsional)
11. [Artisan Commands](#11-artisan-commands)
12. [Database Schema](#12-database-schema)
13. [Catatan Keamanan](#13-catatan-keamanan)

---

## 1. Ringkasan Algoritma

| Algoritma | Kegunaan | Key Size | Library |
|-----------|----------|----------|---------|
| AES-256-CBC | Enkripsi data sensitif (email, private key) | 256-bit | `ext-openssl` |
| RSA-2048 | Enkripsi data finansial per user (nama wallet, nominal, tanggal) | 2048-bit | `ext-openssl` |
| Bcrypt | Hash password, PIN, dan secret key | 12 rounds (default) | `Illuminate\Support\Facades\Hash` |
| HMAC-SHA256 (HS256) | JWT token signing | - | `php-open-source-saver/jwt-auth` |
| SHA256 | Key derivation (stretch key ke 32 byte) | 256-bit | `hash()` built-in PHP |
| XOR Cipher | Key derivation salt (bukan enkripsi utama) | 8 / 16 byte | Custom (`EncryptionHelper`) |

**File utama**: `app/Helpers/EncryptionHelper.php`

---

## 2. Environment Variables & Secret Keys

Semua secret key dikonfigurasi di file `.env`. Berikut variabel yang berkaitan dengan enkripsi:

| Variable | Kegunaan | Default (jika kosong) | Wajib di Produksi |
|----------|----------|----------------------|-------------------|
| `APP_KEY` | Laravel internal encryption | — | Ya |
| `MAIN_SECRET_KEY` | Kunci utama AES-256-CBC (enkripsi email, data sistem) | `'Password'` | **Ya** |
| `MAIN_SALT_KEY` | Salt untuk hashing secret key & PIN | `'Password'` | **Ya** |
| `MAIN_STATIC_IV` | Reserved (belum digunakan) | — | Tidak |
| `JWT_SECRET` | Kunci signing JWT (algoritma HS256) | — | Ya |
| `JWT_ALGO` | Algoritma JWT | `HS256` | Tidak |
| `JWT_BLACKLIST_ENABLED` | Aktifkan blacklist token (logout) | `true` | Tidak |
| `SERVER_PRIVATE_KEY` | RSA private key server (Base64) | — | Ya |
| `SERVER_PUBLIC_KEY` | RSA public key server (Base64) | — | Ya |
| `BCRYPT_ROUNDS` | Jumlah round bcrypt | `12` | Tidak |
| `IS_NEED_ENCRYPT` | Enkripsi seluruh response payload | `false` | Tidak |

> **Penting**: Nilai default `'Password'` pada `MAIN_SECRET_KEY` dan `MAIN_SALT_KEY` hanya ada sebagai fallback development. Di produksi, variabel ini **wajib** diisi dengan nilai acak yang kuat.

### System Secret Key

Fungsi `EncryptionHelper::getSystemSecretKey()` menggabungkan `MAIN_SECRET_KEY + MAIN_SALT_KEY` menjadi satu string yang digunakan sebagai kunci enkripsi email dan data sistem. Jika salah satu variabel tidak di-set, akan throw `EncryptionException`.

```php
// app/Helpers/EncryptionHelper.php:231
public static function getSystemSecretKey(): string
{
    $secretKey = env('MAIN_SECRET_KEY') ?? throw new EncryptionException(...);
    $saltKey    = env('MAIN_SALT_KEY')   ?? throw new EncryptionException(...);
    return $secretKey . $saltKey;
}
```

---

## 3. AES-256-CBC (Symmetric Encryption)

### Cara Kerja

1. Key input di-hash dengan SHA256 untuk mendapat 32 byte (256-bit).
2. IV acak 16 byte di-generate dengan `random_bytes()` setiap kali enkripsi.
3. Data dienkripsi menggunakan `openssl_encrypt()` dengan cipher `aes-256-cbc`.
4. Hasil IV dan ciphertext di-encode Base64.

### Method yang Tersedia

#### `encrypt(string $data, ?string $key): array`
Mengembalikan array dengan dua kunci terpisah.
```php
// Output: ['iv' => 'base64...', 'data' => 'base64...']
$result = EncryptionHelper::encrypt('data sensitif', $key);
```

#### `encryptAsString(string $data, ?string $key): string`
Mengembalikan satu string dengan format `base64_iv.base64_data` (dipisah titik).
```php
// Output: "base64_iv.base64_ciphertext"
$result = EncryptionHelper::encryptAsString('data sensitif', $key);
```
Digunakan untuk: enkripsi email, enkripsi nama keluarga, enkripsi private key.

#### `decrypt(string $encryptedData, string $iv, ?string $key): string`
Mendekripsi dari dua input terpisah (iv dan data).

#### `decryptFromString(string $encryptedData, ?string $key): string`
Mendekripsi dari format dot-separated `"base64_iv.base64_data"`.

### Flow Internal
```
plaintext
   │
   ▼
hash('sha256', $key, true) → ambil 32 byte pertama
   │
   ▼
random_bytes(16) → IV baru setiap enkripsi
   │
   ▼
openssl_encrypt($data, 'aes-256-cbc', $key32, 0, $iv)
   │
   ▼
base64_encode(iv) . '.' . base64_encode(ciphertext)
```

---

## 4. RSA-2048 (Asymmetric Encryption)

### Cara Kerja

- **Enkripsi**: menggunakan public key → `openssl_public_encrypt()`
- **Dekripsi**: menggunakan private key → `openssl_private_decrypt()`
- Hasil enkripsi di-encode Base64.
- Key size: **2048-bit**, digest: **SHA256** (saat generate).

### Method yang Tersedia

#### `encryptAsymmetric(string $data, string $publicKey): string`
```php
// $publicKey: PEM format (raw, bukan Base64)
$encrypted = EncryptionHelper::encryptAsymmetric('nama wallet', $publicKeyPem);
```

#### `decryptAsymmetric(string $encryptedData, string $privateKey): string`
```php
// $encryptedData: Base64 string hasil encrypt
// $privateKey: PEM format
$plaintext = EncryptionHelper::decryptAsymmetric($encrypted, $privateKeyPem);
```

#### `generateAsymmetricKey(): array`
```php
// Output: ['private' => 'base64_pem...', 'public' => 'base64_pem...']
$keys = EncryptionHelper::generateAsymmetricKey();
```

### Key Storage

| Entity | Private Key | Public Key |
|--------|------------|------------|
| User | Dienkripsi AES-256-CBC, disimpan di `user_keys.private_key` | Plain, disimpan di `user_keys.public_key` |
| Family | Dienkripsi AES-256-CBC, disimpan di `family_keys.private_key` | Plain, disimpan di `family_keys.public_key` |
| Staff | Dienkripsi AES-256-CBC, disimpan di `staff_keys.private_key` | Plain, disimpan di `staff_keys.public_key` |

Private key dienkripsi sebelum disimpan menggunakan kunci turunan dari secret key user/family (lihat [Key Derivation](#7-key-derivation)).

---

## 5. Bcrypt (Password & Secret Key Hashing)

### Password Hashing

- **Algoritma**: Bcrypt
- **Rounds**: 12 (default), dikonfigurasi via `BCRYPT_ROUNDS`
- **Library**: `Illuminate\Support\Facades\Hash`

```php
// Hash saat register / ubah password
$hashed = bcrypt($password);             // atau Hash::make($password)

// Verifikasi
Hash::check($inputPassword, $hashed);
```

Digunakan di `app/Services/Auth/AuthServiceImplement.php` baris 127, 370, 447.

### Secret Key & PIN Hashing

Secret key dan PIN **tidak** disimpan plain. Sebelum di-hash, dibalut dengan salt:

```
Hash::make(MAIN_SALT_KEY + secretKey + MAIN_SALT_KEY)
```

```php
// app/Helpers/EncryptionHelper.php:105
public static function hashSecretKey(string $secretKey): string
{
    $salt = env('MAIN_SALT_KEY', 'Password');
    return Hash::make($salt . $secretKey . $salt);
}

// Validasi
public static function validateSecretKey(string $inputKey, string $hashedData): bool
{
    $salt = env('MAIN_SALT_KEY', 'Password');
    return Hash::check($salt . $inputKey . $salt, $hashedData);
}
```

Hash disimpan di kolom `hashed_key` (secret key) dan `hashed_pin` (PIN) pada tabel `user_keys`, `family_keys`, `staff_keys`.

### Konfigurasi Hash

File: `config/hashing.php`

```php
'driver'  => env('HASH_DRIVER', 'bcrypt'),  // bisa diganti 'argon2id'
'bcrypt'  => ['rounds' => env('BCRYPT_ROUNDS', 12)],
'argon'   => ['memory' => 65536, 'threads' => 1, 'time' => 4],
```

---

## 6. JWT Token Signing

### Konfigurasi

File: `config/jwt.php`

| Setting | Value | Env Variable |
|---------|-------|-------------|
| Algoritma default | HS256 | `JWT_ALGO` |
| Secret (HS) | — | `JWT_SECRET` |
| Public key (RS/ES) | — | `JWT_PUBLIC_KEY` |
| Private key (RS/ES) | — | `JWT_PRIVATE_KEY` |
| TTL access token | 60 menit | — |
| TTL refresh token | 20160 menit (14 hari) | — |
| Blacklist | Enabled | `JWT_BLACKLIST_ENABLED` |

### Algoritma yang Didukung

- **Symmetric**: `HS256`, `HS384`, `HS512`
- **Asymmetric**: `RS256`, `RS384`, `RS512`, `ES256`, `ES384`, `ES512`

Default yang digunakan: **HS256** (HMAC-SHA256 dengan `JWT_SECRET`).

### Required Claims

Setiap JWT mengandung: `iss`, `iat`, `exp`, `nbf`, `sub`, `jti`.

### Refresh Token

```php
// app/Helpers/TokenHelper.php:18
$customClaims = [...]; // tambahan claims, expiry 7 hari
$token = JWTAuth::claims($customClaims)->fromUser($user);
```

### Auth Guard

```php
// config/auth.php:44
'api' => ['driver' => 'jwt', 'provider' => 'users']
```

---

## 7. Key Derivation

Sebelum mengenkripsi private key dengan AES-256-CBC, sistem men-derive encryption key dari secret key user/family. Proses ini menggunakan XOR cipher sederhana — bukan untuk keamanan kriptografis, melainkan untuk mencampur komponen kunci menjadi string yang unik per user.

### User Encryption Key

```
getUsersEncryptKey(secretKey, password):
  1. salt = getUsersSalt(secretKey)
       → ambil bagian [1] + "-" + [0] + "-" + last dari secretKey
       → XOR dengan key 16
  2. sanitize = hilangkan "-" dari secretKey
  3. combined = salt + password + sanitize
  4. result = XOR(combined, 8)
```

Kunci ini digunakan untuk mengenkripsi RSA private key user sebelum disimpan ke database.

### Family Encryption Key

```
getFamilyEncryptionKey(secretKey):
  1. salt = getUsersSalt(secretKey)
  2. segment[1] = bagian kedua dari secretKey
  3. sanitize = hilangkan "-" dari secretKey
  4. result = salt + segment[1] + sanitize
```

### XOR Function

```php
// app/Helpers/EncryptionHelper.php:256
public static function xorString(string $string, int $key): string
{
    $result = '';
    for ($i = 0; $i < strlen($string); $i++) {
        $result .= chr(ord($string[$i]) ^ $key);
    }
    return $result;
}
```

> Fungsi XOR ini hanya digunakan untuk derivasi kunci (mencampur komponen), bukan sebagai algoritma enkripsi utama.

---

## 8. Secret Key Format

Secret key yang diberikan kepada user memiliki format:

```
UANGKU-XXXXXX-XXXXXX-XXXXX-XXXXX-XXXXX
```

### Cara Generate

```php
// app/Helpers/EncryptionHelper.php:211
$randomBytes = random_bytes(128);   // 128 byte acak
$base32Key   = strtoupper(str_replace(['=', '+', '/'], '', base64_encode($randomBytes)));

return "UANGKU-"
    . substr($base32Key, 0,  6) . '-'   // segment 0
    . substr($base32Key, 6,  6) . '-'   // segment 1
    . substr($base32Key, 12, 5) . '-'   // segment 2
    . substr($base32Key, 17, 5) . '-'   // segment 3
    . substr($base32Key, 22, 5);        // segment 4
```

Secret key ini:
- **Tidak disimpan plain** di database — hanya hash bcrypt-nya (`hashed_key`).
- Digunakan oleh user sebagai master key untuk men-decrypt private key RSA mereka.
- Jika hilang, private key tidak bisa didekripsi → data finansial tidak bisa diakses.

---

## 9. Alur Enkripsi Per Fitur

### 9.1 Registrasi User

```
User input: name, email, password, secretKey (opsional)
                │
                ▼
1. Email       → AES-256-CBC encrypt
                  key = MAIN_SECRET_KEY + MAIN_SALT_KEY
                  output: "base64_iv.base64_cipher"
                  disimpan di users.email
                │
2. RSA keypair → generateAsymmetricKey()
                  2048-bit, SHA256
                │
3. Name        → RSA encrypt dengan public key user
                  disimpan di users.name
                │
4. Password    → bcrypt hash (12 rounds)
                  disimpan di users.password
                │
5. Private key → AES-256-CBC encrypt
                  key = getUsersEncryptKey(secretKey, password)
                  disimpan di user_keys.private_key
                │
6. Secret key  → bcrypt hash dengan salt
                  Hash::make(SALT + secretKey + SALT)
                  disimpan di user_keys.hashed_key
```

### 9.2 Login

```
1. Email dari input → AES encrypt → cari di database
2. Password → Hash::check() vs users.password
3. JWT access token + refresh token di-generate
4. Private key dari DB di-decrypt dengan getUsersEncryptKey(secretKey, password)
   → dikembalikan ke client (terenkripsi dalam transport HTTPS)
```

### 9.3 Data Wallet / Transaksi

```
Data (nama wallet, nominal, tanggal)
   │
   ▼
RSA encrypt dengan public key user / family
   │
   ▼
Disimpan di database (tidak bisa dibaca tanpa private key)
   │
   ▼
Saat read: private key di-decrypt user → RSA decrypt data
```

### 9.4 Keluarga (Family)

```
1. Family name  → RSA encrypt dengan public key family
2. Private key  → AES encrypt
                  key = getFamilyEncryptionKey(familySecretKey)
3. Secret key   → bcrypt hash dengan salt
```

### 9.5 PIN

```
PIN input
   │
   ▼
hashSecretKey(pin) → Hash::make(SALT + pin + SALT) → bcrypt hash
   │
   ▼
disimpan di user_keys.hashed_pin / staff_keys.hashed_pin

Validasi: validateSecretKey(inputPin, hashed_pin)
```

---

## 10. Response Encryption (Opsional)

Seluruh response payload API dapat dienkripsi dengan mengaktifkan `IS_NEED_ENCRYPT=true`.

```php
// app/Http/Resources/BaseResponse.php:23
if (env('IS_NEED_ENCRYPT', false)) {
    $key = env('MAIN_SECRET_KEY') . env('MAIN_SALT_KEY');
    $encrypted = EncryptionHelper::encrypt(json_encode($data), $key);
    // output: ['iv' => '...', 'data' => '...']
}
```

- **Key**: `MAIN_SECRET_KEY + MAIN_SALT_KEY`
- **Output format**: `{ "iv": "base64...", "data": "base64..." }`
- Default: **disabled** (`false`) — hanya diaktifkan jika client mendukung dekripsi.

---

## 11. Artisan Commands

| Command | File | Fungsi |
|---------|------|--------|
| `php artisan uangku:generate-keys` | `app/Console/Commands/GenerateRSAKeys.php` | Generate RSA 2048-bit keypair, output Base64 |
| `php artisan uangku:generate-secret-key` | `app/Console/Commands/GenerateSecretKey.php` | Generate user secret key format `UANGKU-...` |
| `php artisan encrypt:asymmetric {text} {publicKey}` | `app/Console/Commands/EncryptAsymmetricCommand.php` | Enkripsi teks dengan RSA public key |

### Contoh Generate RSA Key

```bash
php artisan uangku:generate-keys
# Output:
# Private Key (Base64): <paste ke SERVER_PRIVATE_KEY>
# Public Key  (Base64): <paste ke SERVER_PUBLIC_KEY>
```

---

## 12. Database Schema

### `user_keys`

| Kolom | Tipe | Isi |
|-------|------|-----|
| `id` | UUID | Primary key |
| `users` | FK | Relasi ke `users` |
| `private_key` | TEXT | RSA private key terenkripsi AES-256-CBC |
| `public_key` | TEXT | RSA public key (plain PEM) |
| `hashed_key` | VARCHAR(255) | Bcrypt hash dari secret key |
| `hashed_pin` | VARCHAR(255) nullable | Bcrypt hash dari PIN |

### `family_keys`

| Kolom | Tipe | Isi |
|-------|------|-----|
| `id` | UUID | Primary key |
| `family` | FK | Relasi ke `families` |
| `private_key` | TEXT | RSA private key terenkripsi AES-256-CBC |
| `public_key` | TEXT | RSA public key (plain PEM) |
| `hashed_key` | VARCHAR(255) | Bcrypt hash dari secret key |

### `staff_keys`

| Kolom | Tipe | Isi |
|-------|------|-----|
| `id` | UUID | Primary key |
| `staffs` | FK | Relasi ke `staff_accounts` |
| `private_key` | VARCHAR | RSA private key terenkripsi AES-256-CBC |
| `public_key` | VARCHAR | RSA public key (plain PEM) |
| `hashed_key` | VARCHAR | Bcrypt hash dari secret key |
| `hashed_pin` | VARCHAR | Bcrypt hash dari PIN |

---

## 13. Catatan Keamanan

### Hal yang Sudah Baik

- IV di-generate acak setiap enkripsi → mencegah ciphertext yang sama untuk plaintext yang sama.
- Private key tidak pernah disimpan plain di database.
- Secret key tidak pernah disimpan plain — hanya bcrypt hash-nya.
- JWT blacklist diaktifkan untuk mendukung logout dan revokasi token.
- Password di-hash dengan bcrypt (bukan MD5/SHA).

### Yang Perlu Diperhatikan

- **Default fallback `'Password'`** pada `MAIN_SECRET_KEY` dan `MAIN_SALT_KEY` harus dipastikan tidak aktif di produksi. Sistem akan tetap berjalan dengan nilai default ini tanpa error, yang berarti data terenkripsi dengan kunci yang lemah.
- **RSA 2048-bit** masih aman untuk standar saat ini (NIST merekomendasikan minimal 2048-bit hingga 2030). Pertimbangkan upgrade ke 4096-bit untuk data yang perlu bertahan jangka panjang.
- **XOR cipher** yang digunakan untuk key derivation (`getUsersSalt`, `getUsersEncryptKey`) bersifat deterministik dan reversibel. Kekuatan keamanannya bergantung pada entropi secret key yang digunakan, bukan pada algoritma XOR itu sendiri.
- **Secret key yang hilang** = private key tidak bisa didekripsi = data finansial user tidak bisa diakses permanen. Pastikan ada mekanisme recovery yang aman.
- **`IS_NEED_ENCRYPT=true`** mengenkripsi seluruh payload response. Pastikan client dapat mendekripsi sebelum mengaktifkan di produksi.