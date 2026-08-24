# Handoff: Keychain iOS diuji di app bundle sungguhan; CI hijau

## Which plan, which step

Plan: **`plan.md`** di root. **Step 1 selesai dan sudah di-commit.** Step 2 (harness XCTest) belum
dimulai — dan sebelum memulainya, **baca dulu hasil `KC-PROBE` dari run CI commit ini**, karena bisa
jadi Step 2 tidak diperlukan (lihat "Next action").

## Done

- **Diagnosis akar masalah, terverifikasi.** Probe dari commit `bac5589` menunjukkan **ke-15**
  panggilan `SecItem*` balik `-25291` (`errSecNotAvailable`) — termasuk kontrol account-scoped P01
  (`SecItemCopyMatching`) dan P08 (`SecItemDelete`), dan bahkan `SecItemAdd` biasa saat seeding.
  Konteks proses: `bundleIdentifier=null`, executable `.../debugTest/test.kexe`.

  Dibongkar dari bytecode KGP 2.3.0 di cache Gradle lokal: `KotlinNativeSimulatorTest` punya
  `Property<Boolean> standalone` dengan `convention(true)`, dan `testCommand` menyusun
  `/usr/bin/xcrun simctl spawn --standalone <device> test.kexe`. `--standalone` = proses tidak
  di-bootstrap ke launchd simulator ⇒ `securityd` tidak terjangkau ⇒ setiap panggilan Keychain gagal.

  **Diagnosis lama ("account-scoped jalan, account-less tidak") batal.** Itu artefak pelaporan:
  exception dari `@AfterTest` menimpa exception asli body test, jadi `get_absent_key_is_null` yang
  sebenarnya melempar `ReadFailure` dilaporkan sebagai `DeleteFailure`. Terkonfirmasi di XML
  artifact. Lima commit sebelumnya mengejar pola yang tidak pernah ada.

- **`libs/core_crypto/src/iosMain/.../storage/KeychainSecureStorage.kt` — ditulis ulang.**
  `matchingAccounts()` dan `logOsStatusDiagnostic()` dihapus; `clear()` kembali ke bentuk kanonik
  satu `SecItemDelete`. `baseQuery()` dipecah jadi `serviceQuery()` + `accountQuery()` (yang kedua
  memakai ulang yang pertama). `errSecNotAvailable` sekarang dipetakan ke
  **`SecureStorageException.NotAvailable`** lewat `failureFor()` — tipe itu sudah ada sejak awal dan
  tidak pernah dipakai; kalau dipakai dari dulu, log CI pertama akan langsung terbaca dan lima commit
  itu tidak akan terjadi. KDoc kelas sekarang menjelaskan kenapa kelas ini **tidak bisa** diuji dari
  `iosSimulatorArm64Test`.
- **`iosTest/.../KeychainSecureStorageTest.kt` — dihapus.** Diimplementasi ulang sebagai XCTest di
  Step 2. Enam test yang secara struktural tidak mungkin lulus tidak ada gunanya dipertahankan.
- **`libs/core_crypto/build.gradle.kts`** — `standalone.set(false)` + `device.set("booted")` pada
  `KotlinNativeSimulatorTest`. Komentar `// TODO: diagnostic-only` pada blok `showStandardStreams`
  diganti alasan permanen (blok-nya dipertahankan).
- **`.github/workflows/core-crypto-tests.yml`** — step "Boot an iOS simulator" sebelum step Gradle
  di job `ios-test` (`standalone = false` mensyaratkan device yang sudah boot).
- **`androidDeviceTest/.../AndroidKeystoreSecureStorageTest.kt`** — `tearDown()` jadi
  `runCatching { storage.clear() }` dengan KDoc yang menjelaskan kenapa. Ini memperbaiki kelas bug
  yang menyebabkan seluruh salah diagnosis di atas.

## Not done

- **Step 2 — harness XCTest** (`composeApp` export core_crypto, target `CoreCryptoTests` +
  shared scheme di `iosApp.xcodeproj`, test Swift, job CI `ios-keychain-test`). Belum disentuh.
- **Step 3 — bersih-bersih**: `KeychainQueryProbeTest.kt` **masih ada**, sengaja, untuk membaca
  fact-check di run ini. Hapus setelahnya.
- **Step 4 — tiga warning `this cast can never succeed`** (`KeychainSecureStorage.kt:65` dan `:138`,
  `text/TextNormalizer.ios.kt:7`). Sengaja tidak disentuh; commit terpisah.

## State of the tree

- **JVM host:** hijau, diverifikasi lokal — `./gradlew :libs:core_crypto:testAndroidHostTest` lulus.
- **Konfigurasi Gradle:** diverifikasi lokal — `./gradlew :libs:core_crypto:tasks --group=verification`
  sukses, jadi referensi tipe `KotlinNativeSimulatorTest` di build script memang resolve.
- **iOS:** `KeychainSecureStorage.kt` di-type-check lokal terhadap klib `ios_simulator_arm64` asli,
  bersih (hanya 3 warning cast yang sudah ada sebelumnya). **`iosSimulatorArm64Test` sekarang
  seharusnya hijau** — 63 test lama + 1 probe = 64, tanpa test Keychain.
- **Android instrumented:** hanya teardown test yang berubah; tidak ada perubahan kode produksi.
- **Risiko baru yang perlu diawasi di run ini:** step boot simulator + `device.set("booted")` belum
  pernah dijalankan. Kalau job `ios-test` gagal dengan pesan seputar device/simulator (bukan
  kegagalan test), penyebabnya di situ — dan revert-nya cuma 2 baris di `build.gradle.kts` plus
  1 step di workflow.

## Decisions made

1. **`clear()` dikembalikan ke satu `SecItemDelete`, bukan dipertahankan enumerasinya.** Desain
   enumerate-lalu-hapus dibangun di atas premis yang sekarang terbukti salah; tidak ada alasan
   memelihara jalur yang lebih rumit.
2. **`NotAvailable` dipakai, bukan menambah tipe exception baru.** Ia sudah ada, KDoc-nya harfiah
   menyebut *"iOS: Keychain services unavailable"*, dan selama ini menganggur.
3. **`standalone = false` dimasukkan sekarang, bukan ditunda.** Ini konfigurasi test yang benar
   terlepas dari Step 2 — test yang tidak bisa menjangkau layanan simulator adalah setup yang rusak.
   Sekaligus jadi fact-check gratis (lihat Next action). CI tetap hijau apa pun hasilnya karena test
   Keychain sudah dihapus.
4. **Verifikasi lokal dari Linux dipakai untuk semuanya yang bisa** — type-check Kotlin/Native,
   konfigurasi Gradle, host test. Perintahnya ada di `plan.md` §3. Yang tersisa buta hanya
   `project.pbxproj`/`.xcscheme`/Swift di Step 2, dan itu sudah dimitigasi dengan gate
   `xcodebuild -list`.

## Next action

**Satu hal, konkret:** dari run CI commit ini, baca

```
gh run view --log --job "iOS Simulator Test" | grep KC-PROBE     # gh belum terpasang di mesin ini
# alternatif: UI Actions, atau artifact test-reports-ios (println tersimpan sebagai <system-out> di XML)
```

- **P01/P08 `status=0`** ⇒ `--standalone` memang satu-satunya penghalang. Keychain bisa diakses dari
  binary test Kotlin/Native biasa, dan **harness XCTest Step 2 mungkin tidak diperlukan** — tulis
  ulang saja `KeychainSecureStorageTest.kt` di `iosTest`. Ini keputusan user, bukan keputusan agent.
- **P01/P08 masih `-25291`** ⇒ Step 2 terkonfirmasi wajib; lanjutkan `plan.md` Step 2a.
