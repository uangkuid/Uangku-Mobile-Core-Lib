# Mobile → Backend: Hasil Validasi Test Vector 2SKD

**Dari:** tim Mobile (Uangku-Mobile-Core-Lib, KMP Android/iOS)
**Untuk:** tim Backend (Uangku-BE)
**Tanggal:** 17 Juli 2026
**Menjawab:** `response.md` §8 ("Yang kami harapkan dari kalian")

---

## TL;DR

> 🟢 **Set A byte-identik — cocok semua.** `unlockKey` DAN `authKey` KMP == referensi PHP, di 600.000
> iterasi asli, untuk semua kasus normalization-stable (ascii, emoji+spasi, 200-char, NFC).
> 🟢 **NFC/NFD terbukti.** Klien menormalisasi NFC; password NFD konvergen ke vektor `nfc_cafe`.
> `nfc_cafe.authKey != nfd_cafe.authKey` — konfirmasi PHP tidak menormalisasi.
> 🟢 **Set B/C/D/E cocok.** Container PHP dibuka klien, `wrapped_private_key` → PEM PKCS#8 valid,
> envelope `ct` → plaintext.
> ✅ **Checkbox terakhir §15 sekarang boleh kalian centang** — tapi lihat catatan iOS di bawah.

Implementasi ada di `libs/core_crypto` (`Pbkdf2HkdfKeyDerivationEngine`, `AesGcmCipher`,
`Pbkdf2HkdfKeyDerivationEngine`), engine cryptography-kotlin 0.6.0 (JCA di Android, CryptoKit +
Apple/CommonCrypto di iOS). Kontrak §4.2 yang kalian kirim dipakai apa adanya.

---

## Hasil per Set

| Set | Yang diuji | Android host (JCA) | Catatan |
|---|---|---|---|
| **A** | `unlockKey` + `authKey` byte-identik @600k | ✅ cocok (4 kasus stable) | `ascii_baseline`, `emoji_padded`, `long_200_chars`, `nfc_cafe` |
| **A (NFC/NFD)** | NFD konvergen ke `nfc_cafe`; `nfc≠nfd` | ✅ | Klien normalize NFC sebelum PBKDF2 |
| **B** | HKDF `salt=null` == `salt=""` | ✅ `salt_omitted_okm_hex` cocok | Konfirmasi asumsi RFC 5869 zeros untuk `authKey` |
| **C** | Buka container fixed-IV buatan PHP | ✅ plaintext kembali | Cross-impl AES-GCM (arah decrypt) |
| **D** | Buka `wrapped_private_key` pakai `unlock_key_hex` | ✅ → PEM PKCS#8 valid | Cross-impl AES-GCM + handling PEM |
| **E** | Buka `envelope.ct` pakai `data_key_hex` | ✅ → `"15000000"` | Sub-container `ct` = format `ver‖iv‖ct‖tag` yang sama |

**Tidak ada mismatch.** Jadi tidak ada `kdf_pass_hex`/`kdf_secret_hex` yang perlu kami kirim balik —
`unlockKey` cocok berarti seluruh rantai PBKDF2→HKDF→XOR benar, dan `authKey` cocok berarti langkah
HKDF terakhir juga benar.

## Soal `nfd_cafe`

Kami **tidak** mereproduksi `nfd_cafe.auth_key` — itu memang **sesuai desain**. Klien kami normalize
ke NFC sebelum PBKDF2, jadi input NFD menghasilkan `authKey` yang sama dengan `nfc_cafe`, bukan
`nfd_cafe`. Vektor `nfd_cafe` kami pakai justru untuk **membuktikan** PHP tidak normalize
(`nfc ≠ nfd`), yang mengunci kenapa normalisasi wajib di klien. Ini konsisten dengan §13.3.

---

## ⚠️ Satu kejujuran soal iOS — belum kami centang sepenuhnya

Kontrak §4.2 kami tulis lintas-platform (Android + iOS) dari satu `commonTest` yang sama. Tapi di mesin
tempat kami develop, **iOS simulator runtime belum terinstall** (Xcode 26.1.1 ada, runtime iOS-nya
belum di-download), jadi:

- ✅ Kode iOS **compile + link** bersih (`linkDebugTestIosSimulatorArm64` sukses) — artinya klib
  cryptography-kotlin resolve di Kotlin/Native, dan asumsi composite-provider (CryptoKit tidak punya
  RSA/PBKDF2 → fallback ke Apple/CommonCrypto) tidak menabrak masalah link.
- ⏳ Test iOS **belum benar-benar dieksekusi** di simulator — jadi byte-identity di jalur
  CryptoKit/CommonCrypto belum kami buktikan runtime.

Test-nya identik dengan yang hijau di Android host (satu `commonTest`), jadi risikonya kecil, tapi
sesuai semangat §15 kalian sendiri ("centang manual tanpa validasi klien nyata sama saja"), kami
laporkan apa adanya. **Begitu runtime iOS tersedia dan test jalan hijau, kami kabari lagi** dan itu
baru bukti penuh lintas-platform.

**Rekomendasi centang §15:** baris terakhir aman dicentang untuk **Android/JCA** sekarang; untuk klaim
"lintas KMP (Android+iOS)" penuh, tunggu konfirmasi iOS-sim kami.

---

## Dua nit kecil di `docs/encryption.md` §8 (bukan blocker)

Sesuai ajakan kalian ("kalau ada yang terbaca ambigu, kemungkinan besar itu memang ambigu"):

1. **§8 masih kontradiksi sendiri soal minor-unit.** Satu bullet: *"rupiah tidak punya sen ... minor
   unit = rupiah itu sendiri"*. Bullet berikutnya: *"Rp150.000,00 → `"15000000"` bila minor-unit sen;
   ... kalau rupiah utuh, `"150000"`"*. Jadi Rp150.000 itu `"150000"` atau `"15000000"`? **Tidak
   memblokir kami** — lib kami terima/keluarkan `ByteArray`, app yang format (itu `Long.toString()`).
   Tapi ini kelas ambiguitas yang sama dengan `'user-salt'` dulu: Vue nanti bisa baca beda → salah-baca
   saldo, bukan error yang kelihatan. Usul: tetapkan satu, mis. "rupiah utuh, tanpa sen".
2. **§8 menunjuk Set A untuk contoh literal numerik — harusnya Set E.** Set A tidak punya field
   plaintext numerik; `"15000000"` ada di `setE_hybrid_envelope.plaintext`. Dan nilai itu sendiri tidak
   menjawab nit #1 (vector tidak menyebut Rp berapa yang diwakilinya).

---

## Yang sedang kami kerjakan lanjut

`core_crypto` masih dalam pengerjaan (Fase 4+ di plan kami): RSA-OAEP-4096, hybrid envelope, storage
(Keychain/Keystore), facade. Vektor Set A–E ini sudah kami jadikan fixture test permanen (di-hardcode
dari `kdf-vectors.json`, dengan provenance), jadi regresi apa pun ke depan langsung ketahuan.

Terima kasih vektornya — nilai antara (`kdf_pass_hex` dst.) yang kalian sertakan bikin validasi ini
sekali jalan, tanpa debugging buta. 🙏
