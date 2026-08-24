# Plan: Hentikan loop kegagalan CI `ios-test` di `core_crypto`

> **Status: in progress** (24 Agustus 2026). Step 1 & 2 sudah landing, menunggu hasil satu run CI
> diagnostik. Step 3 menunggu data probe.
>
> **Supersede:** dokumen ini menggantikan plan `core_crypto` → Zero-Knowledge (2SKD) yang sudah
> berstatus **selesai** (seluruh Fase 1–8 diimplementasi di commit `a79fa18`). Plan lama tidak
> dibatalkan — ia tuntas; yang tersisa dari pekerjaan itu adalah satu job CI yang tidak pernah hijau,
> dan itulah cakupan dokumen ini.

---

## 1. Context

Job **`ios-test`** di `.github/workflows/core-crypto-tests.yml` gagal terus sejak diperkenalkan
(commit `3f283c9`). Sudah **5 commit** dihabiskan menebak penyebabnya — `5cf7eee`, `70571e6`,
`f43d1f9`, `bc54b51`, `10a568d`, `342cf65`, `9a21c97`, `9ce84d5` — dan CI masih merah.

**Keadaan run terakhir** (`macos-26-arm64`, Kotlin/Native 2.3.0, `BUILD FAILED in 4m 49s`):

- `69 tests completed, 6 failed`. Yang gagal **hanya** 6 case `KeychainSecureStorageTest`.
- Bukan di body test — `put`/`get`/`remove`/`contains` lulus semua. Ke-6-nya gagal di
  `@AfterTest tearDown()` → `storage.clear()`.
- Titik gagalnya: `matchingAccounts()` di `KeychainSecureStorage.kt:157` — `SecItemCopyMatching`
  dengan `kSecMatchLimitAll` + `kSecReturnAttributes` **tanpa** `kSecAttrAccount`, balik
  `OSStatus=-25291` (`errSecNotAvailable`).

**Pola konsisten lintas 5 commit tebakan:**

| Bentuk query | Hasil |
|---|---|
| account-scoped (`class`+`service`+`account`) — Add / CopyMatching / Delete | selalu **sukses** |
| account-less `SecItemDelete` + `kSecMatchLimitAll` (`342cf65`) | `-25291` |
| account-less `SecItemDelete` + `kSecMatchLimitOne` (`9a21c97`) | `-25291` |
| account-less `SecItemCopyMatching` + `kSecMatchLimitAll` (`9ce84d5`) | `-25291` |

Commit terakhir **memindahkan** kegagalan dari operasi hapus ke operasi baca, bukan memperbaikinya.

**Hipotesis kerja:** binary `iosSimulatorArm64Test` jalan sebagai proses telanjang tanpa `.app`
bundle dan tanpa entitlement, sehingga keychain tidak melayani enumerasi lintas access group;
lookup langsung by-account tetap dilayani.

**Akar masalah proses** (ini yang bikin "ga selesai-selesai"):

1. Developer di Linux → tiap hipotesis harus lewat CI, ~5 menit per putaran.
2. HTML test report yang ditunjuk pesan error Gradle **tidak pernah keluar** dari runner — tidak ada
   `upload-artifact`.
3. Tiap putaran hanya menguji **satu** hipotesis.

**Target:** satu putaran CI diagnostik yang menguji seluruh ruang hipotesis sekaligus, lalu satu
commit fix berbasis fakta.

---

## 2. Steps

### Step 1 — Observability CI ✅ selesai

`.github/workflows/core-crypto-tests.yml`:

- `actions/upload-artifact@v4` di **ketiga** job (`if: ${{ !cancelled() }}`) — laporan test XML/HTML
  jadi bisa diunduh tanpa perlu menambah `println` dan me-run ulang.
- Cache `~/.konan` di job `ios-test` — toolchain Kotlin/Native (LLVM, sysroot, libffi) tidak lagi
  diunduh ulang tiap run; cache Gradle tidak mencakup direktori ini.

**Android:** `android-instrumented-test` juga mengunggah `build/reports/androidTests/**` dan
`build/outputs/androidTest-results/**` (jalur laporan connected-test berbeda dari unit test).
**iOS:** tidak ada perubahan kode.

### Step 2 — Probe matrix ✅ selesai

`libs/core_crypto/src/iosTest/.../storage/KeychainQueryProbeTest.kt` — satu `@Test` **tanpa
assertion**, jadi tidak menambah kegagalan dan seluruh probe tetap jalan walau
`KeychainSecureStorageTest` masih merah di run yang sama. Output di-prefix `KC-PROBE`.

Konteks proses yang di-print lebih dulu (`NSBundle.mainBundle.bundleIdentifier`, `.bundlePath`,
`.executablePath`) adalah yang memvalidasi/menggugurkan hipotesis "tanpa app bundle".

| # | Operasi | account | limit | atribut tambahan | Yang diisolasi |
|---|---|---|---|---|---|
| P01 | CopyMatching | ada | One | returnData, DPK | kontrol, harus `0` |
| P02 | CopyMatching | — | All | returnAttrs, DPK | bentuk yang sekarang gagal |
| P03 | CopyMatching | — | One | returnAttrs, DPK | `limitAll` atau account-less? |
| P04 | CopyMatching | — | All | returnAttrs, **tanpa DPK** | DPK penyebabnya? |
| P05 | CopyMatching | — | All | returnAttrs, DPK, `synchronizable=Any` | kandidat fix termurah |
| P06 | CopyMatching | — | All | returnData, DPK | `returnAttributes` penyebabnya? |
| P07 | CopyMatching | — | All | DPK saja | bentuk minimum |
| P08 | Delete | ada | — | DPK | kontrol, harus `0` |
| P09 | Delete | — | — | DPK | bentuk kanonik `clear()` |
| P10 | Delete | — | — | tanpa DPK | isolasi |
| P11 | CopyMatching | — | All | returnAttrs, DPK, service di-seed `kSecAttrAccessibleAfterFirstUnlock` | atribut accessible default? |

Probe baca dijalankan sebelum probe hapus, dan tiap probe hapus di-seed ulang lebih dulu.

### Step 3 — ⏳ Terapkan fix sesuai hasil probe

Cabang keputusan ditentukan di muka supaya tidak perlu merencanakan ulang:

- **Cabang A — ada bentuk account-less yang balik `0`.** Pakai bentuk itu. Kalau `SecItemDelete`
  account-less-nya jalan (P09/P10), buang `matchingAccounts()` seluruhnya dan kembalikan `clear()`
  ke bentuk kanonik satu panggilan. Hapus juga blok komentar `clear()` di `:112-120` yang
  mendokumentasikan kesimpulan yang salah.
- **Cabang B — semua account-less gagal, P01/P08 sukses** (hipotesis app-bundle terkonfirmasi).
  Kode produksi tetap kanonik (satu `SecItemDelete` account-less — benar di app tertanda tangan;
  jangan mendesain ulang kelas security-critical demi harness test). Test yang menyesuaikan:
  `tearDown()` pakai `storage.remove(...)` per key, dan `clear_removes_everything` di-`@Ignore`
  dengan alasan tertulis menunjuk nomor probe + OSStatus. Catat di `handoff.md` bahwa `clear()` iOS
  jadi tidak tercakup CI.
- **Cabang C — bahkan P01/P08 gagal.** Diagnosis keliru total; jangan tempel fix, laporkan.

Di cabang mana pun: perbaiki `logOsStatusDiagnostic` (`:195`) yang teksnya hardcode
`"SecItemDelete failed"` padahal juga dipanggil dari `clear-enumerate`, sebuah operasi **baca** —
pesan itu sendiri ikut menyesatkan investigasi sebelumnya.

### Step 4 — ⏳ Bersihkan diagnostik (setelah CI hijau)

- Hapus `KeychainQueryProbeTest.kt`.
- Hapus `logOsStatusDiagnostic()` beserta pemanggilnya.
- Blok `showStandardStreams` di `build.gradle.kts:105-114`: **pertahankan**, ganti komentar
  `// TODO: diagnostic-only...` dengan alasan permanen.
- **Jangan** hapus upload-artifact & cache konan — itu perbaikan permanen.

### Step 5 — ⏳ (Terpisah) Tiga warning `This cast can never succeed`

`KeychainSecureStorage.kt:75` (`put()`), `:210` (`cfString()`, jalur panas tiap query), dan
`text/TextNormalizer.ios.kt:7`. Commit `9ce84d5` membuktikan cast bergaya begini bisa melempar
`TypeCastException` saat runtime meski compile bersih. Sekarang ketiganya kebetulan jalan, jadi
**bukan** bagian dari fix ini — kerjakan sebagai commit terpisah setelah CI hijau.

---

## 3. Verification

**Verifikasi compile bisa dilakukan lokal dari Linux** (temuan baru, lihat `handoff.md`):

```bash
~/.konan/kotlin-native-prebuilt-linux-x86_64-2.3.0/bin/konanc \
  -target ios_simulator_arm64 -p library -o out <file>.kt
```

Distribusi Kotlin/Native prebuilt untuk Linux **sudah menyertakan klib platform iOS**, jadi seluruh
`iosMain`/`iosTest` bisa di-type-check tanpa macOS — yang tidak bisa cuma link + run binary Apple.
Ini menghapus seluruh kelas kegagalan "compile error ketahuan 5 menit kemudian di CI".

**Lewat CI:**

1. Push branch → workflow ter-trigger (`paths:` mencakup `libs/core_crypto/**` dan file workflow).
2. `gh run watch`, lalu `gh run view --log --job "iOS Simulator Test" | grep KC-PROBE`.
3. Kriteria sukses Step 2 **bukan** CI hijau — `ios-test` masih akan merah karena
   `KeychainSecureStorageTest` belum diperbaiki. Kriterianya: **11 baris probe + 3 baris context**
   muncul lengkap, dan artifact `test-reports-ios` bisa diunduh.
4. Setelah Step 3: `iosSimulatorArm64Test` → `0 failed`, ketiga job hijau, tidak ada regresi di
   `unit-test` / `android-instrumented-test` (keduanya hijau sebelum perubahan ini).

---

## 4. Open questions

- **Hipotesis "tanpa `.app` bundle" belum terverifikasi** — baru inferensi dari pola account-scoped
  vs account-less. `NSBundle.mainBundle` yang di-print Step 2 yang memutuskan.
- **`clear()` belum pernah dites di device sungguhan.** `handoff.md` menyebut iOS baru "compile+link
  bersih". Kalau Cabang B yang kepakai, ini utang verifikasi sebelum modul dipakai app konsumen.
- **`matchingAccounts()` bocor CF-object** — `result.value` tidak pernah di-`CFBridgingRelease`.
  Hilang sendiri kalau Cabang A/B menghapus fungsinya.
