# FAQ & Temuan untuk Tim Backend — Kontrak Zero-Knowledge

**Dari:** tim Mobile (Uangku-Mobile-Core-Lib, KMP Android/iOS)
**Tentang:** `Uangku-BE` branch `main` + `docs/encryption.md`
**Tanggal:** 16 Juli 2026
**Kenapa dokumen ini ada:** kami sedang menulis ulang `libs/core_crypto` untuk mengimplementasikan
kontrak 2SKD. Kami **klien pertama** yang mengimplementasikan §4.2. Sebelum menulis kode, kami
membaca source backend langsung (bukan hanya doc) — dan menemukan beberapa hal yang harus diputuskan
tim backend dulu.

> **Ringkasan eksekutif**
> - 🔴 **1 blocker**: backend punya **dua derivasi 2SKD yang saling bertentangan** di repo yang sama. Kami tidak bisa menulis satu baris pun sebelum ini diputuskan.
> - 🟠 **2 temuan keamanan**: proteksi anti-enumeration `/auth/salt` tidak bekerja; `hashSecret()` berisiko bypass PIN.
> - 🟡 **5 pertanyaan** kontrak yang belum terjawab dari source.
> - 🔵 **5 koreksi dokumentasi**.
> - ✅ **12 pertanyaan sudah kami jawab sendiri** dari source — tidak perlu dijawab lagi, tapi mohon dikoreksi kalau ada yang salah baca (lihat Lampiran A).

---

## 🔴 BLOCKER #1 — Ada DUA derivasi 2SKD yang saling bertentangan

### Masalahnya

Tiga "sumber kebenaran" di `Uangku-BE`, **tiga jawaban berbeda** untuk HKDF salt pada `kdfSecret`:

| Sumber | HKDF salt |
|---|---|
| `docs/encryption.md` §4.2 (kontrak resmi untuk klien) | `'user-salt'` (literal) |
| `tests/Feature/Api/AuthControllerTest.php:23` | `'user-salt'` (literal) |
| **`database/seeders/UserSeeder.php:42`** | **`'admin@uangku.com'` — EMAIL user** |

```php
// database/seeders/UserSeeder.php:42
// ↑ jalur "production-like": benar-benar memanggil $authService->register()
$kdfSecret = EncryptionHelper::hkdf($secretKey, 'uangku-secretkey-v1', 32, 'admin@uangku.com');

// tests/Feature/Api/AuthControllerTest.php:23
// ↑ jalur test: dipakai semua test 2SKD termasuk test dua-faktor
$kdfSecret = EncryptionHelper::hkdf($secretKey, 'uangku-secretkey-v1', 32, 'user-salt');
```

Ingat signature-nya — arg ke-4 adalah `$salt`:
```php
// app/Helpers/EncryptionHelper.php:106
public static function hkdf(string $ikm, string $info, int $length = 32, string $salt = ''): string
```

### Kenapa CI tetap hijau padahal ada dua derivasi tak kompatibel?

Karena **keduanya loop tertutup yang self-consistent**:

- **Seeder**: derive `authKey` → `register()` → server `bcrypt()` apa saja yang diterima → selesai. Tidak pernah dibandingkan dengan apapun.
- **Test**: derive `authKey` → simpan via `User::factory()->withAuthKey($authKey)` → login dengan `authKey` yang sama → cocok. Tidak pernah dibandingkan dengan seeder.

Dua-duanya membuktikan "saya konsisten dengan diri saya sendiri", **bukan** "saya konsisten dengan
kontrak". Test suite bisa 100% hijau selamanya sambil memuat dua implementasi yang tidak akan pernah
bisa saling login.

`docs/encryption.md` §15 sebenarnya sudah menandai lubang ini:

> `[ ] Test vectors byte-identik lintas KMP/Vue — perlu diverifikasi begitu klien mengimplementasikan §4.2.`

**Bug ini hidup persis di checkbox yang belum dicentang itu.** Kami klien pertama — kami yang
menabraknya.

### Kenapa ini blocker, bukan bug biasa

`authKey` adalah **satu-satunya** hal yang membuktikan kepemilikan kredensial. Kalau kami pilih salah:
- `authKey` mobile ≠ `authKey` server → `Hash::check()` gagal → **tidak ada user yang bisa login. Selamanya.**
- Tidak bisa di-patch belakangan tanpa memaksa **semua** user reset kredensial (yang artinya kehilangan seluruh data finansial, karena reset mengganti keypair — §7.2).

Ini menentukan byte pertama yang kami tulis. Kami **tidak akan** menebak.

### 🔺 Peringatan penting: JANGAN pakai email

Kalau HKDF salt = email, maka:

```
ganti email → kdfSecret berubah → unlockKey berubah
            → wrapped_private_key TIDAK BISA dibuka lagi
            → SELURUH data finansial user hilang permanen
```

Tanpa peringatan, tanpa recovery, tanpa cara server membantu (server memang tidak punya kuncinya).
Kalau Uangku punya — atau akan punya — fitur ganti email, `UserSeeder.php:42` adalah ranjau aktif.

### Rekomendasi kami (urut terbaik → terburuk)

| # | Opsi | Penilaian |
|---|---|---|
| **1** | **Pakai per-user `salt`** (16B yang sudah ada, dari `/auth/salt`) sebagai HKDF salt | ✅ **Rekomendasi utama.** User-specific, stabil selamanya, sudah ditransportasikan ke klien, tidak terikat PII. Paling benar secara kriptografis. Perubahan: seeder + test + doc |
| **2** | **`'user-salt'` literal** | ✅ Aman. Cocok dgn doc + test (2 dari 3). Salt konstan lintas-user diizinkan RFC 5869 dan tidak berbahaya di sini — ikm (secret key) sudah high-entropy ~140 bit, yang justru fungsi utama salt HKDF. Perubahan paling kecil: **perbaiki seeder saja** |
| 3 | **Email** | ❌ **Jangan.** Rapuh terhadap ganti email (= data loss permanen), mengikat kunci enkripsi ke PII yang bisa berubah |

**Apapun yang dipilih:** seeder, test, dan doc **harus dibuat identik**, dan test vector lintas-klien
harus di-generate dari satu implementasi itu.

**❓ PERTANYAAN 1: Mana yang kanonik?** Kalau bisa, sekalian jelaskan maksud asli nama `'user-salt'`
— apakah memang literal, atau placeholder yang dimaksudkan sebagai "salt milik user"? Itu akan
menjelaskan kenapa seeder menaruh email di sana.

---

## 🟠 TEMUAN KEAMANAN

Dua hal ini di luar scope mobile, tapi kami temukan saat membaca source dan rasanya perlu kalian tahu.

### 🟠 Temuan A — Decoy salt `/auth/salt` bisa dibedakan → anti-enumeration tidak bekerja

```php
// app/Services/Auth/AuthServiceImplement.php:171
$salt = base64_encode(substr(
    hash_hmac('sha256', "decoy-salt:{$blindIndex}", env('MAIN_BLIND_INDEX_KEY', '')),
    0, 16
));
```

**Bug-nya:** `hash_hmac()` **tanpa** argumen ke-4 `$binary = true` mengembalikan **string hex**. Jadi
`substr(..., 0, 16)` mengambil **16 karakter hex ASCII** (`[0-9a-f]`), bukan 16 byte acak.

Bandingkan:

| | Isi sebelum base64 | Setelah base64 |
|---|---|---|
| Salt **asli** | 16 byte acak, `0x00`–`0xFF` | 24 char |
| Salt **decoy** | 16 char ASCII hex, hanya `[0-9a-f]` | 24 char |

Panjangnya identik — jadi tidak ketahuan sekilas. Tapi penyerang tinggal `base64_decode` hasilnya:

- Semua byte jatuh di charset hex ASCII → **decoy** → email tidak terdaftar
- Ada byte di luar itu → **asli** → email terdaftar

Peluang 16 byte acak kebetulan **semuanya** hex-ASCII = `(16/256)^16` = **2⁻⁶⁴**. Praktis nol.
Artinya: **enumerasi email tetap 100% bisa dilakukan**, persis yang §6 dan §15 klaim sudah dicegah.

**Fix — satu argumen:**
```php
$salt = base64_encode(substr(
    hash_hmac('sha256', "decoy-salt:{$blindIndex}", env('MAIN_BLIND_INDEX_KEY', ''), true),
    //                                                                              ^^^^ raw binary
    0, 16
));
```

**Kenapa test tidak menangkapnya:** `test_salt_returns_deterministic_decoy_for_unknown_email` hanya
mengecek **determinisme** (dua panggilan → salt sama), bukan **indistinguishability** (decoy tidak
bisa dibedakan dari asli). Test hijau, properti keamanannya bocor. **Kelas bug yang sama persis
dengan Blocker #1**: test yang membuktikan konsistensi-diri, bukan properti yang dijanjikan.

**Saran test tambahan:** ambil salt untuk email terdaftar & tak terdaftar → `base64_decode` keduanya
→ assert distribusi byte-nya tidak bisa dibedakan (mis. keduanya punya byte di luar `[0x30-0x39, 0x61-0x66]`).

**Catatan kecil:** `env('MAIN_BLIND_INDEX_KEY', '')` di sini memakai default string kosong, sementara
`EncryptionHelper::blindIndex()` (`:126-129`) **throw** kalau key kosong. Inkonsisten — meski dalam
praktiknya `blindIndex()` dipanggil duluan di `getSalt()` jadi akan throw lebih dulu.

---

### 🟠 Temuan B — `hashSecret()` berisiko bypass PIN karena truncation bcrypt 72-byte

```php
// app/Helpers/EncryptionHelper.php:191-199
public static function hashSecret(string $secret): string
{
    $pepper = env('MAIN_SALT_KEY');
    if (empty($pepper)) { throw new EncryptionException('MAIN_SALT_KEY is not configured.'); }
    return Hash::make($pepper.$secret.$pepper);
}
```

**bcrypt memotong input di 72 byte — diam-diam, tanpa error.** Kami sudah verifikasi pemakaiannya:

| Pemakai | File | Aman? |
|---|---|---|
| **PIN** | `PinServiceImplement.php:133, 244` (write), `:267` (validate) | ⚠️ **berisiko** |
| `authKey` | — **tidak memakai `hashSecret()`**. `AuthServiceImplement.php:92` pakai `Hash::make($authKey)` polos | ✅ aman |

Untuk PIN 6 digit dengan `MAIN_SALT_KEY` sepanjang **P** byte, input bcrypt = `2P + 6`:

| Panjang `MAIN_SALT_KEY` | Akibat |
|---|---|
| P ≤ 66 | ✅ PIN utuh ter-hash. Aman |
| P = 67–71 | ⚠️ PIN terpotong sebagian — hanya `72 − P` digit pertama yang dicek |
| **P ≥ 72** | 🔴 **PIN diabaikan TOTAL. PIN apapun akan tervalidasi.** Bypass otentikasi penuh |

**Kenapa ini realistis:** `.env.example:79` mengosongkan `MAIN_SALT_KEY` **tanpa panduan panjang**:
```
MAIN_SALT_KEY=
```
Kalau ada yang mengisinya dengan `openssl rand -base64 64` (→ 88 char) — pilihan yang terlihat
*lebih aman* — PIN-nya mati total. Semakin kuat pepper yang dipilih, semakin rusak sistemnya. Itu
kombinasi yang berbahaya.

Nilai umum: `openssl rand -base64 32` → 44 char → aman. `openssl rand -hex 32` → 64 char → aman
(PIN utuh; trailing pepper terpotong tapi tidak berdampak). `openssl rand -base64 64` → 88 char →
**bypass**.

**Fix — pre-hash supaya panjang input selalu tetap:**
```php
public static function hashSecret(string $secret): string
{
    $pepper = env('MAIN_SALT_KEY');
    if (empty($pepper)) { throw new EncryptionException('MAIN_SALT_KEY is not configured.'); }
    // HMAC dulu → selalu 44 char, mustahil kena truncation, pepper tetap berfungsi
    return Hash::make(base64_encode(hash_hmac('sha256', $secret, $pepper, true)));
}
```
`validateSecret()` diubah simetris. **Perlu migrasi** untuk `hashed_pin` yang sudah ada (atau
re-enroll PIN), jadi mohon dipertimbangkan sebelum ada user produksi.

**Minimal, kalau tidak mau ubah skema:** tambahkan validasi panjang `MAIN_SALT_KEY` saat boot, dan
beri panduan di `.env.example` (mis. `# WAJIB ≤ 32 karakter — bcrypt memotong di 72 byte`).

**Catatan tambahan:** desain `$pepper . $secret . $pepper` — trailing pepper sebenarnya **tidak
menambah keamanan apapun** dibanding `$pepper . $secret` saja, tapi menggandakan konsumsi budget
72-byte. Kalau tetap pakai pola string-concat, buang yang trailing.

---

## 🟡 PERTANYAAN YANG BELUM TERJAWAB

Kami sudah menjawab 12 dari source (Lampiran A). Sisa 5 ini benar-benar tidak bisa kami simpulkan
sendiri.

### ❓ PERTANYAAN 2 — NFC: apakah SEMUA klien menormalisasi password?

`docs/encryption.md` §13.3 mewajibkan: *"Encoding password: UTF-8 NFC"*. Tapi:

- **PHP** `hash_pbkdf2()` **tidak** menormalisasi apapun — byte password masuk apa adanya
- **Vue/WebCrypto** juga tidak, kecuali kode secara eksplisit memanggil `.normalize('NFC')`
- **KMP** perlu `expect/actual` (Kotlin/Native tidak punya normalizer Unicode bawaan)

**Skenario rusak:** user daftar di web dengan password `café`. Keyboard macOS menghasilkan bentuk
**NFD** (`e` + combining acute, U+0065 U+0301). Vue tidak normalize → PBKDF2 atas byte NFD →
`authKey` tersimpan berdasar NFD. Lalu user login di mobile, mengetik password yang sama, tapi
keyboard Android menghasilkan **NFC** (U+00E9) → kami normalize (atau tidak) → **byte berbeda →
authKey berbeda → login gagal permanen.**

Ini tidak akan pernah muncul di test — hanya kena user asli dengan password non-ASCII, dan mereka
tidak akan bisa mendiagnosisnya.

**Pertanyaan:**
1. Apakah Vue memanggil `.normalize('NFC')` sebelum PBKDF2? (Kalau belum tahu, mohon dicek — ini menentukan jawaban kami.)
2. Kalau **tidak ada** klien yang normalize → kami hapus `normalizeNfc()` dan §13.3 perlu dikoreksi.
3. Kalau **iya** → kami implement, dan Vue harus dipastikan sama. Harus **all-or-nothing**.

**Rekomendasi kami:** tetap NFC (sesuai §13.3), pastikan Vue melakukannya, dan **test vector wajib
menyertakan kasus NFD** (lihat Lampiran B) supaya bisa dibuktikan, bukan diasumsikan.

---

### ❓ PERTANYAAN 3 — `uangku-enc-v1` dipakai di mana?

`docs/encryption.md` §4 mendaftarkannya sebagai nilai `info` HKDF yang valid:

> `info` = `uangku-secretkey-v1` / `uangku-auth-v1` / **`uangku-enc-v1`**

Tapi kami **tidak menemukan satupun referensi** ke `uangku-enc-v1` di seluruh source backend, dan
tidak ada flow di §5–§10 yang menyebutnya.

**Pertanyaan:** apakah ia (a) direncanakan untuk enkripsi DB lokal klien (derive dari `unlockKey`,
tidak pernah dikirim ke server), (b) reserved untuk nanti, atau (c) **dead** — sisa dari draft?

Kalau (c) → kami buang `deriveEncryptionKey()` dari API kami dan §4 perlu dikoreksi.
Kalau (a) → mohon spesifikasikan salt & panjang output-nya supaya Vue dan mobile konsisten.

---

### ❓ PERTANYAAN 4 — Serialisasi kanonik `wallet_amount` (dan field numerik lain)

Data finansial dienkripsi **di klien** sebelum dikirim. Artinya Vue dan mobile harus setuju
**byte-for-byte** tentang bentuk plaintext-nya sebelum enkripsi — kalau tidak, angka yang sama akan
menghasilkan plaintext berbeda, dan yang lebih penting: **hasil dekripsi di klien lain akan
di-parse berbeda**.

Nilai `150000` bisa jadi: `"150000"` / `"150000.00"` / `"150000.0"` / `150000` (JSON number) /
`"1.5e5"` / `"Rp150.000"`.

`UserSeeder.php:81` memakai `'amount' => '0'` (string, tanpa desimal) — tapi itu jalur seeder yang
komentarnya sendiri bilang plaintext, jadi tidak mengikat.

**Pertanyaan:** apa bentuk kanoniknya? Sekalian:
- Ada desimal (sen) atau selalu integer rupiah?
- Nilai negatif → prefix `-`?
- Batas presisi/besaran? (`Long`? `BigDecimal`? berapa digit?)
- Bagaimana dengan `start_date_month` dan field tanggal lain — ISO-8601? epoch? dienkripsi juga?

**Rekomendasi:** tetapkan satu bentuk eksplisit di §8 (usul: **string desimal, tanpa pemisah ribuan,
tanpa simbol mata uang, minor unit sebagai integer** — mis. `"15000000"` untuk Rp150.000,00), lalu
sertakan satu test vector.

---

### ❓ PERTANYAAN 5 — `wallet_name` / `wallet_amount` saat register: RSA langsung atau hybrid envelope?

§11 menyebut `POST /auth/register` menerima `wallet_name?`, `wallet_amount?` sebagai ciphertext, dan
§8 mendefinisikan hybrid envelope `{v, ek, ct}` untuk data finansial. Tapi register payload tidak
menyebut yang mana.

Keduanya *bisa* jalan (server menyimpan opaque), tapi Vue dan mobile harus sama — kalau tidak, data
yang ditulis satu klien tidak terbaca klien lain.

**Pertanyaan:** hybrid envelope `{v,ek,ct}` (konsisten dgn §8), atau RSA-OAEP langsung (muat, karena
`"0"` jauh di bawah limit 446 byte)?

**Rekomendasi:** **hybrid envelope, selalu, untuk semua data finansial** — konsisten, dan tidak ada
field yang tiba-tiba menabrak limit 446 byte saat isinya bertambah panjang (nama wallet panjang +
emoji bisa mendekati). Aturan "semua field finansial = envelope, tanpa pengecualian" jauh lebih sulit
disalahimplementasikan daripada "envelope kecuali yang ini".

---

### ❓ PERTANYAAN 6 (non-blocking) — `iterations` tidak disimpan per-user

```php
// app/Services/Auth/AuthServiceImplement.php:176
return ['salt' => $salt, 'iterations' => EncryptionHelper::PBKDF2_ITERATIONS];
```

`/auth/salt` selalu mengembalikan konstanta `600000`. Tidak ada kolom `iterations` di `user_keys`,
dan `POST /auth/register` tidak mengirim nilai yang dipakai klien.

Komentar di `EncryptionHelper.php:36-37` menyatakan niatnya:
> *"Returned to clients via /auth/salt so it can be raised later without breaking old clients."*

**Tapi mekanismenya belum ada.** Karena nilainya konstanta global, menaikkan `PBKDF2_ITERATIONS` ke
1.200.000 nanti (mengikuti OWASP) akan membuat **semua akun lama** menderivasi `kdfPass` berbeda →
`authKey` berbeda → **semua user ter-lockout serentak**. Kebalikan dari niat komentarnya.

**Rekomendasi:**
1. Tambah kolom `user_keys.iterations` (int, default 600000)
2. `POST /auth/register` + `change-password` + `reset-password` mengirim `iterations` yang dipakai klien
3. `/auth/salt` mengembalikan **nilai milik user itu**, bukan konstanta
4. Naikkan default hanya untuk akun baru; akun lama ikut naik saat mereka ganti password

**Catatan:** desain kami sudah siap — kami memperlakukan `iterations` sebagai **parameter runtime
dari `/auth/salt`**, tidak pernah konstanta di jalur derivasi. Jadi begitu backend mengirim nilai
per-user, mobile langsung jalan tanpa perubahan.

---

## 🔵 KOREKSI DOKUMENTASI (`docs/encryption.md`)

### 1. §9 — `RSA-OAEP(pub, famPriv)` mustahil secara matematis

```
wrapped_own        = RSA-OAEP(owner.pub, famPriv)
wrapped_for_member = RSA-OAEP(member.pub, famPriv)
```

RSA-4096 OAEP-SHA256 **max plaintext = `512 − 2×32 − 2` = 446 byte**. PKCS#8 PEM private key
RSA-4096 ≈ **3.200 byte** (DER ≈ 2.350). Selisih ~7×. Ini akan gagal di **semua** bahasa — bukan
keterbatasan KMP.

**Untungnya tidak memblokir implementasi**: `FamilyServiceImplement` menyimpan `wrapped_private_key`
**sepenuhnya opaque** dan tidak pernah melakukan RSA sendiri. Jadi klien tinggal pakai **hybrid
envelope §8** — `RSA-OAEP(member.pub, dataKey)` + `AES-GCM(dataKey, famPriv)` — dan semuanya jalan.
Strukturnya sama, jadi helper-nya tipis.

**Yang perlu:** perbaiki teks §9 supaya Vue (dan siapapun yang mengikuti §9 harfiah) tidak buang
waktu menabrak tembok yang sama. Usul:
```
wrapped_own        = HybridEnvelope(owner.pub, famPriv)      // {v, ek, ct} — lihat §8
wrapped_for_member = HybridEnvelope(member.pub, famPriv)
```
Sekalian tambahkan catatan limit 446 byte di §4, supaya jelas kapan RSA langsung boleh dipakai.

### 2. §4.2 + §4 — kontradiksi `'user-salt'` (lihat Blocker #1)

Setelah diputuskan, samakan **doc + `UserSeeder.php:42` + `AuthControllerTest.php:23`**.

### 3. §4 tabel "Verifier hash" — salah

> | Verifier hash | bcrypt | pepper = `MAIN_SALT_KEY` | Server (`EncryptionHelper::hashSecret/validateSecret()` — **authKey & PIN**) |

Kenyataannya `authKey` **tidak** lewat `hashSecret()`. `AuthServiceImplement.php:92, 294, 368` semua
pakai `Hash::make($authKey)` — **bcrypt polos, tanpa pepper**. `hashSecret()` hanya dipakai PIN
(`PinServiceImplement.php:133, 244, 267`).

Tidak berdampak ke klien (kami kirim `authKey` mentah either way), tapi doc-nya menyesatkan — dan
menyembunyikan fakta bahwa pepper **tidak pernah dipakai untuk authKey**.

**Pertanyaan bonus:** apakah `authKey` memang sengaja tidak di-pepper? Kalau DB bocor tapi `.env`
tidak, pepper akan memberi lapisan tambahan pada verifier. (Data finansial tetap aman tanpa itu —
`unlockKey` tidak bisa diturunkan dari `authKey` — jadi ini bukan hal mendesak, tapi sepertinya
tidak disengaja mengingat doc mengklaim sebaliknya.)

### 4. §15 — checklist memberi rasa aman palsu

> - [x] Unit test `EncryptionHelper` (16/16 pass): ... **2SKD dual-factor requirement** ...
> - [x] Test dua-faktor: salah password saja ATAU salah secret key saja → login gagal
> - [x] `/auth/salt` mengembalikan salt deterministik untuk email tak dikenal (anti user-enumeration)

Ketiganya centang, dan ketiganya **tidak membuktikan apa yang tersirat**:

- Test dua-faktor membuktikan derivasi **konsisten dengan dirinya sendiri**, bukan konsisten dengan kontrak (Blocker #1 — seeder memakai derivasi lain, test tetap hijau)
- Test `/auth/salt` mengecek **determinisme**, bukan **indistinguishability** — properti yang sebenarnya dijanjikan justru bocor (Temuan A)

**Saran:** tandai ulang jadi "self-consistency" dan tambahkan checkbox terpisah untuk properti
lintas-implementasi. Checkbox terakhir yang belum dicentang —

> `[ ] Test vectors byte-identik lintas KMP/Vue`

— adalah **satu-satunya** yang benar-benar akan menangkap Blocker #1. Mohon jangan dicentang sebelum
ada klien nyata yang memvalidasinya.

### 5. §11 — `iterations` per-user (lihat Pertanyaan 6)

---

## 📋 PERMINTAAN: Test Vector

Kami butuh ini untuk membuktikan implementasi KMP byte-identical. **Hanya relevan setelah Blocker #1
diputuskan** — vector yang dibuat sekarang akan basi kalau derivasinya berubah.

### Kenapa harus termasuk nilai antara

Kalau kami hanya diberi `authKey` dan hasilnya tidak cocok, kami tidak tahu **langkah mana** yang
rusak — PBKDF2? HKDF? XOR? encoding? Dengan nilai antara, mismatch langsung menunjuk baris yang
salah. Ini menghemat berhari-hari debugging buta.

### Set A — 2SKD (paling kritis), ~4 kasus

```json
{
  "password":        "...",
  "password_hex":    "<byte PERSIS yang di-hash PHP>",
  "secret_key":      "UANGKU-ABC123-DEF456-GHI78-JKL90-MNO12",
  "hkdf_ikm_hex":    "<byte ikm PERSIS yang masuk ke hash_hkdf>",
  "hkdf_salt_hex":   "<salt yang MENANG di Blocker #1>",
  "salt_b64":        "<base64, 16B>",
  "salt_hex":        "<16B>",
  "iterations":      600000,
  "kdf_pass_hex":    "<32B>",
  "kdf_secret_hex":  "<32B>",
  "unlock_key_hex":  "<32B>",
  "auth_key":        "<base64, 44 char>"
}
```

- `password_hex` → menyelesaikan pertanyaan NFC secara definitif
- `hkdf_ikm_hex` → membuktikan dash di-strip atau tidak, tanpa ambiguitas
- `hkdf_salt_hex` → mengunci Blocker #1

**Kasus yang diminta:**

| # | Password | Membuktikan |
|---|---|---|
| 1 | ASCII biasa, mis. `CorrectHorse123!` | Baseline |
| 2 | `café` — **kirim DUA versi**: NFC (`63 61 66 c3a9`) dan NFD (`63 61 66 65 cc81`) | Apakah PHP normalize sama sekali. **Kasus paling penting** |
| 3 | Emoji + spasi depan & belakang, mis. `" 🔐pass "` | Tidak ada trimming tersembunyi |
| 4 | 200 karakter | Tidak ada truncation di jalur manapun |

### Set B — Isolasi HKDF (menyelesaikan Blocker #1 + memvalidasi wrapper)

Dengan `ikm_hex` tetap, jalankan `EncryptionHelper::hkdf()` dengan salt: **(a) diomit**, **(b) `''`**,
**(c) `'user-salt'`**, **(d) sebuah email** → kembalikan keempat `okm_hex`.

Ini sekaligus mengonfirmasi asumsi kami bahwa `hash_hkdf` dengan salt kosong ≡ RFC 5869
`zeros(HashLen)` — yang kami andalkan untuk `authKey` (di mana `hkdf()` dipanggil tanpa arg salt).

### Set C — Container AES-GCM, **IV tetap**

```json
{ "key_hex": "<32B>", "iv_hex": "<12B>", "plaintext_hex": "...", "container_b64": "<base64(ver‖iv‖ct‖tag)>" }
```

GCM itu randomized — **vector ber-IV tetap satu-satunya cara** meng-assert byte-exactness. Mohon
konfirmasi juga dua arah: container kalian bisa dibuka klien, container klien bisa dibuka
`aesGcmDecrypt()`.

### Set D — RSA (dari dev DB, bukan dikarang)

- Satu nilai kolom `user_keys.public_key` **asli** → mengonfirmasi `base64(PEM)` (dugaan kami) secara empiris
- Satu `user_keys.private_key` (= `wrapped_private_key`) **beserta `unlock_key_hex`** yang membukanya

### Set E — Hybrid envelope

Satu `{v, ek, ct}` lengkap dari implementasi referensi + private key-nya.

---

## 💡 Usulan proses (opsional, tapi sangat membantu)

**Satu implementasi referensi, satu file vector.** Yang menyebabkan Blocker #1 bukan kecerobohan —
tapi karena derivasi 2SKD ditulis **tiga kali** (doc, seeder, test) tanpa ada yang mengecek satu sama
lain. Selama itu masih tiga tempat, mereka akan menyimpang lagi.

Usul konkret:
1. Satu fungsi kanonik, mis. `EncryptionHelper::deriveAuthKey($password, $secretKey, $salt, $iterations)`
2. **Seeder dan test memanggil fungsi itu** — bukan menulis ulang langkah-langkahnya
3. Satu artisan command, mis. `php artisan uangku:kdf-vectors` → dump JSON test vector
4. File JSON itu di-commit ke repo backend; Vue dan KMP memakainya sebagai fixture test

Dengan begitu "byte-identical lintas klien" jadi sesuatu yang di-**test CI**, bukan diverifikasi
manual sekali lalu perlahan menyimpang. Kami senang memakai fixture itu apa adanya di test KMP kami.

---

## Lampiran A — Sudah kami jawab sendiri dari source ✅

Tidak perlu dijawab. Dicantumkan supaya kalian bisa **mengoreksi kalau kami salah baca** — kami
memakai ini sebagai default implementasi.

| Pertanyaan | Kesimpulan kami | Bukti |
|---|---|---|
| HKDF ikm — string penuh atau strip dash? | **String penuh dengan dash**, apa adanya | `UserSeeder.php:42` & `AuthControllerTest.php:23` sama-sama pass `$secretKey` mentah |
| Default salt wrapper `hkdf()` | `$salt = ''` → `hash_hkdf(..., '')` → RFC 5869 `zeros(HashLen)`. Match `salt=null` di library kami | `EncryptionHelper.php:106-109` |
| Format container | `base64(chr(0x02) ‖ iv(12B) ‖ ct(N) ‖ tag(16B))`, min len 29, reject versi ≠ 0x02. Tidak ada v1 | `EncryptionHelper.php:61, 74-84` |
| AAD? | **Tidak ada** — param ke-7 `openssl_encrypt` = `''` | `EncryptionHelper.php:55` |
| Varian base64 | Standard + padded, strict decode | `EncryptionHelper.php:61, 73` |
| `salt` transport | `base64(random_bytes(16))`; di-`base64_decode` dulu sebelum PBKDF2 | `UserSeeder.php:38, 61`; `AuthControllerTest.php:22` |
| Format `public_key` | **`base64(PEM SPKI)`** — double-encoded | `UserSeeder.php:52, 62` |
| Format `wrapped_private_key` | `aesGcmEncrypt(PKCS#8 **PEM**, unlockKey)` → container yang sama. **PEM, bukan DER** | `UserSeeder.php:51, 54` |
| `auth_key` at rest | `Hash::make($authKey)` — bcrypt polos, tanpa pepper | `AuthServiceImplement.php:92` |
| Validasi server-side atas material kripto | **Nihil** — `required\|string` saja; semua opaque | `AuthController.php:100-103` |
| Change-credentials boleh rotasi secret key? | **Ya** — tidak ada `new_public_key`, keypair dipertahankan; server tidak peduli faktor mana yang berubah | `AuthServiceImplement.php:253-304` |
| Reset — row lama dihapus? | **Tidak** — hanya menimpa `public_key`/`private_key`/`salt`; row lama jadi sampah tak terbaca | `AuthServiceImplement.php:329-388` |

**Dua hal yang kami perhatikan dan setujui:**
- `AuthControllerTest.php:22` memakai `iterations = 1000` untuk kecepatan test. Kami melakukan hal yang sama — `iterations` adalah **parameter**, bukan konstanta, di jalur derivasi kami. Logika di-test pada 1.000 iterasi; vector di-test pada 600.000.
- `/auth/salt` throttle 20/menit, `/auth/login` 10/menit (`routes/api.php:45-46`) — sesuai doc ✅

## Lampiran B — Konteks: apa yang sedang kami bangun

`libs/core_crypto` (KMP, Android + iOS) sedang ditulis ulang total untuk kontrak ZK. Implementasi
lama (AES-256-CBC, `SHA-256(secretKey)` tanpa salt) **dihapus seluruhnya** — tidak ada backward-compat,
sejalan dengan pendekatan greenfield kalian di §14.

Kami akan expose:
- 2SKD (PBKDF2 600k + HKDF + XOR) → `unlockKey`, `authKey`
- Generator Secret Key `UANGKU-XXXXXX-XXXXXX-XXXXX-XXXXX-XXXXX` (client-side, CSPRNG)
- AES-256-GCM dengan container `ver‖iv‖ct‖tag` kalian
- RSA-OAEP-4096 + wrap/unwrap private key
- Hybrid envelope `{v, ek, ct}`
- Android Keystore / iOS Keychain untuk cache `unlockKey`

**Yang TIDAK kami lakukan:** HTTP. Lib ini murni toolkit — ia mengembalikan data class polos
(`RegistrationMaterial`, `LoginPreparation`, dst.) dan app yang memanggil endpoint kalian. Jadi
pertanyaan kami semua tentang **format data & derivasi**, bukan tentang bentuk API.

Setiap hal yang kami tanyakan adalah hal yang, kalau kami tebak salah, akan menghasilkan **kegagalan
senyap dan permanen** untuk user asli — bukan error yang kelihatan saat development. Itu sebabnya
dokumen ini sedetail ini. 🙏
