# Handoff: hentikan loop kegagalan CI `ios-test` di `core_crypto`

## Which plan, which step

Plan: **`plan.md`** di root (baru ditulis, men-supersede plan 2SKD yang sudah selesai).
Step 1 dan Step 2 **selesai dan sudah di-commit**. Step 3 **menunggu hasil satu run CI diagnostik** —
tidak ada yang bisa dikerjakan sampai output `KC-PROBE` terbaca.

## Done

- **Step 1 — observability CI.** `.github/workflows/core-crypto-tests.yml`:
  - `actions/upload-artifact@v4` di ketiga job (`unit-test`, `android-instrumented-test`,
    `ios-test`), `if: ${{ !cancelled() }}`, `if-no-files-found: warn`. Laporan test
    (`build/reports/tests/**`, `build/test-results/**`, plus `reports/androidTests/**` dan
    `outputs/androidTest-results/**` untuk job Android) sekarang bisa diunduh dari run yang gagal.
    Sebelumnya pesan Gradle menunjuk ke file HTML di dalam runner yang tidak pernah bisa diakses.
  - Cache `~/.konan` di job `ios-test` (`actions/cache@v4`, key dari hash `gradle/libs.versions.toml`).
  - Tidak ada perubahan lain di workflow; `runs-on: macos-latest` sengaja dibiarkan (sudah
    `macos-26-arm64`, cocok untuk target `iosSimulatorArm64`).
- **Step 2 — probe matrix.** File baru
  `libs/core_crypto/src/iosTest/kotlin/com/oratakashi/uangku/core/libs/core_crypto/storage/KeychainQueryProbeTest.kt`.
  Satu `@Test` tanpa assertion, 11 probe (P01–P11) + 3 baris konteks proses, semua di-print dengan
  prefix `KC-PROBE`. Rinciannya di `plan.md` Step 2.
- **Verifikasi compile lokal** (lihat "Decisions made" di bawah) — probe sudah di-type-check terhadap
  klib platform `ios_simulator_arm64` yang asli, bersih; hanya 2 warning `this cast can never succeed`
  yang identik dengan yang sudah ada di kode produksi.

## Not done

- **Step 3 (fix sebenarnya)** — belum, dan sengaja belum: butuh data probe. Tiga cabang keputusan
  (A/B/C) sudah ditulis di `plan.md` supaya agent berikutnya tidak perlu merencanakan ulang.
- **Step 4 (bersihkan diagnostik)** — `KeychainQueryProbeTest.kt` dan `logOsStatusDiagnostic()` masih
  ada. Baru dihapus setelah CI hijau.
- **Step 5 (tiga warning `as NSString`)** — sengaja tidak disentuh supaya tidak mencampur perubahan ke
  dalam putaran diagnostik.
- **`KeychainSecureStorage.kt` tidak diubah sama sekali** di commit ini.

## State of the tree

- **Android / JVM host:** tidak tersentuh. `unit-test` dan `android-instrumented-test` hijau sebelum
  perubahan ini dan tidak ada perubahan kode yang mempengaruhinya; kalau salah satunya berubah merah,
  itu regresi dari step upload-artifact.
- **iOS:** **masih merah, dan itu memang yang diharapkan pada run ini.**
  `KeychainSecureStorageTest` (6 case) masih gagal di `tearDown()` karena fix-nya belum ditulis.
  Kriteria sukses run ini bukan CI hijau, melainkan munculnya 11 baris `KC-PROBE Pxx` + 3 baris
  `KC-PROBE context`. Jangan salah baca job merah ini sebagai "step gagal".
- Probe sudah dipastikan compile — kegagalan compile bukan risiko yang tersisa.

## Decisions made

1. **Probe-first, bukan tebakan keenam.** Lima commit sebelumnya (`5cf7eee` … `9ce84d5`) masing-masing
   menguji satu hipotesis per run CI 5 menit. Commit terakhir (`9ce84d5`) bahkan tidak memperbaiki
   apa pun — ia hanya memindahkan kegagalan `-25291` dari `SecItemDelete` ke `SecItemCopyMatching`,
   karena yang menentukan bukan operasinya melainkan ada/tidaknya `kSecAttrAccount` di query.
   Satu run yang menguji 11 bentuk sekaligus menghentikan pola itu.
2. **Probe tidak meng-assert apa pun.** Kalau ia gagal, sisa probe tidak jalan dan run terbuang.
3. **Cek "hasilnya array atau bukan" dibuat statis, bukan dinamis.** Rencana awal memakai `CFGetTypeID`;
   ternyata **fungsi itu tidak ada di binding CoreFoundation Kotlin/Native** (diverifikasi terhadap
   klib, bukan diasumsikan). Diganti flag `expectArray` per probe. `CFArrayGetCount` pada
   `CFDictionary` yang dikembalikan `kSecMatchLimitOne` adalah undefined behaviour yang bisa
   menjatuhkan seluruh proses probe.
4. **`showStandardStreams` dipertahankan permanen** (menyimpang dari TODO aslinya yang menyuruh hapus):
   stdout test yang tertangkap berguna untuk semua target, bukan cuma investigasi ini. Komentarnya
   akan ditulis ulang di Step 4.
5. **TEMUAN PALING PENTING — `iosMain`/`iosTest` bisa di-type-check dari Linux, tanpa macOS.**
   Distribusi Kotlin/Native prebuilt untuk Linux di `~/.konan/kotlin-native-prebuilt-linux-x86_64-2.3.0`
   **sudah menyertakan klib platform iOS** (`klib/platform/ios_simulator_arm64/`, termasuk
   `Security`, `CoreFoundation`, `Foundation`):

   ```bash
   K=~/.konan/kotlin-native-prebuilt-linux-x86_64-2.3.0
   # type-check satu file terhadap binding iOS asli
   $K/bin/konanc -target ios_simulator_arm64 -p library -o out file.kt
   # cek sebuah simbol memang ada sebelum dipakai
   $K/bin/klib dump-metadata $K/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Security \
     | grep 'val kSecUseDataProtectionKeychain'
   ```

   Yang **tidak** bisa dilakukan dari Linux cuma link + run binary Apple. Artinya seluruh kelas
   kegagalan "salah nama konstanta / salah tipe, baru ketahuan 5 menit kemudian di CI" bisa
   dihilangkan sepenuhnya. Pakai ini sebelum push, selalu.

## Next action

**Satu hal, konkret:** buka run CI untuk commit ini, ambil log job `iOS Simulator Test`, jalankan

```bash
gh run view --log --job "iOS Simulator Test" | grep KC-PROBE
```

lalu cocokkan hasilnya dengan cabang A / B / C di `plan.md` Step 3 dan terapkan cabang yang sesuai.
**`gh` belum terpasang di mesin dev ini** (`gh: command not found`), jadi sampai itu dipasang,
ambil lognya lewat UI GitHub Actions atau unduh artifact `test-reports-ios` — output `println` juga
tersimpan di `build/test-results/**` sebagai `<system-out>` di dalam XML JUnit.
