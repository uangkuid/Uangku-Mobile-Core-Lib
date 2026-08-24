# Handoff: Keychain iOS diuji di app bundle sungguhan; CI hijau

## Which plan, which step

Plan: **`plan.md`** di root.

- **Step 1 — selesai, landing di `c68e2df`, CI hijau.** Fact-check yang dinebengkan di step itu
  **sudah dibaca**; hasilnya di `plan.md` §1 "Run lanjutan `c68e2df`".
- **Step 3 (bersih-bersih) — sebagian ditarik maju dan selesai** di working tree saat ini: probe
  dihapus, tiga tempat yang menyimpan diagnosis usang dikoreksi. Belum di-commit.
- **Step 2 (harness XCTest) — belum dimulai, dan sekarang terkonfirmasi wajib.** Alasannya berubah:
  bukan launchd, tapi entitlement.

## Done

- **Diagnosis akar masalah, dua lapis, keduanya terverifikasi dari log CI.**

  Probe `bac5589` (`standalone = true`): ke-15 `SecItem*` balik `-25291 errSecNotAvailable` —
  termasuk kontrol account-scoped P01/P08 dan `SecItemAdd` biasa. Sebabnya dibongkar dari bytecode
  KGP 2.3.0: `KotlinNativeSimulatorTest.standalone` ber-`convention(true)` dan membangun
  `xcrun simctl spawn --standalone <device> test.kexe`; proses tak di-bootstrap ke launchd ⇒
  `securityd` tak terjangkau.

  Probe `c68e2df` (`standalone = false` + simulator di-boot): ke-15 panggilan berubah jadi
  **`-34018 errSecMissingEntitlement`**. Ini bukan salah satu dari dua cabang yang diprediksi
  handoff sebelumnya (`0` atau tetap `-25291`), dan bacanya dua bagian:
  1. `standalone = false` **bekerja** — `-34018` hanya bisa datang dari `securityd` yang benar-benar
     memeriksa entitlement pemanggil, jadi prosesnya sekarang sampai ke sana.
  2. Penghalang sisanya **entitlement**: `bundleIdentifier=null`, `test.kexe` Mach-O telanjang tanpa
     `application-identifier`. Seeding dengan dan tanpa `kSecAttrAccessible` gagal identik ⇒ juga
     bukan soal atribut query.

  ⇒ Harness XCTest wajib. Tidak ada konfigurasi Gradle yang bisa menggantikannya.

- **Diagnosis lama ("account-scoped jalan, account-less tidak") batal** — artefak pelaporan:
  exception dari `@AfterTest` menimpa exception asli body test, jadi `get_absent_key_is_null` yang
  sebenarnya melempar `ReadFailure` dilaporkan `DeleteFailure`. Lima commit mengejar pola yang tidak
  pernah ada.

### Landing di `c68e2df`

- `iosMain/.../storage/KeychainSecureStorage.kt` ditulis ulang — `matchingAccounts()` dan
  `logOsStatusDiagnostic()` dihapus, `clear()` kembali ke satu `SecItemDelete`, `baseQuery()` dipecah
  jadi `serviceQuery()` + `accountQuery()`, `errSecNotAvailable` dipetakan ke
  `SecureStorageException.NotAvailable` lewat `failureFor()`.
- `iosTest/.../KeychainSecureStorageTest.kt` dihapus (diimplementasi ulang sebagai XCTest di Step 2).
- `libs/core_crypto/build.gradle.kts` — `standalone.set(false)` + `device.set("booted")`.
- `.github/workflows/core-crypto-tests.yml` — step "Boot an iOS simulator" di job `ios-test`.
- `androidDeviceTest/.../AndroidKeystoreSecureStorageTest.kt` — `tearDown()` jadi non-throwing.

### Di working tree, belum di-commit

- **`iosTest/.../KeychainQueryProbeTest.kt` dihapus.** Sudah menjawab pertanyaannya, dan ia **tidak
  punya assertion sama sekali** — lulus tanpa syarat, termasuk saat ke-15 panggilannya gagal. Source
  set `iosTest` ikut kosong dan hilang.
- **`KeychainSecureStorage.kt` KDoc kelas dikoreksi.** Sebelumnya mengklaim setiap `SecItem*` balik
  `errSecNotAvailable` karena tidak di-bootstrap ke launchd — sudah tidak benar. Sekarang: launchd
  beres, entitlement yang tidak. KDoc `failureFor()` dapat paragraf kenapa `-34018` sengaja **tidak**
  dapat tipe exception sendiri (kondisi harness, tak pernah terlihat aplikasi sungguhan; menambah
  tipe = memperlebar API surface untuk state yang tak bisa dicapai consumer).
- **Komentar `standalone` di `build.gradle.kts`** tidak lagi mengklaim `-25291` sebagai sebab akhir;
  sekarang menyebut alasan ke depan + catatan eksplisit bahwa ini **tidak** memberi akses Keychain.
- **`plan.md`** — status Step 1 jadi selesai, hasil probe `c68e2df` masuk sebagai fakta di §1,
  Step 1c/Step 3/Step 4/§3/§4 disinkronkan.

## Not done

- **Step 2 — harness XCTest** (`composeApp` export `core_crypto`, target `CoreCryptoTests` + shared
  scheme di `iosApp.xcodeproj`, test Swift, job CI `ios-keychain-test`). Belum disentuh. Sampai ini
  jalan, **`KeychainSecureStorage` nol coverage CI** — dan itu sekarang tercatat jujur, bukan
  tertutup test yang selalu hijau.
- **Step 4 — tiga warning `This cast can never succeed`** (`KeychainSecureStorage.kt:65`, `:138`,
  `text/TextNormalizer.ios.kt:7`). Prioritas turun: probe `c68e2df` mengeksekusi cast identik di
  `:141`/`:201` dan lolos sampai menghasilkan OSStatus ⇒ **false-positive**, jadi ini kosmetik
  (bungkam warning), bukan perbaikan bug seperti yang diduga dari `9ce84d5`.
- **Apakah `ios-test` termasuk required check** di branch protection — belum diperiksa, butuh akses
  setelan repo GitHub. Kalau tidak wajib, hijau/merahnya tidak menahan merge sama sekali.

## State of the tree

- **JVM host:** hijau, diverifikasi lokal barusan — `./gradlew :libs:core_crypto:testAndroidHostTest
  --rerun` ⇒ `BUILD SUCCESSFUL`, **63 test, 0 failure, 0 error**.
- **Konfigurasi Gradle:** diverifikasi lokal — `./gradlew :libs:core_crypto:tasks --group=verification`
  sukses setelah komentar `standalone` diedit.
- **iOS:** run `c68e2df` hijau di CI — `BUILD SUCCESSFUL in 12m 44s`, 64 test, 0 failure, 0 skipped.
  Setelah probe dihapus, run berikutnya harus **63** test dan **tidak ada baris `KC-PROBE`** di log.
  Kalau bukan 63, ada test lain yang ikut terbawa hilang. Tidak ada baris Kotlin iOS yang berubah di
  working tree ini — hanya KDoc — jadi type-check `konanc` tidak dijalankan ulang.
- **Android instrumented:** tidak tersentuh sejak `c68e2df`.

## Decisions made

1. **Probe dihapus sekarang, tidak menunggu Step 2 hijau** (`plan.md` menaruhnya di Step 3). Test
   tanpa assertion selalu hijau; menahannya sampai Step 2 selesai berarti berminggu-minggu CI hijau
   yang artinya lebih kecil dari yang terlihat. Ia sudah menjawab pertanyaannya — jawabannya sekarang
   tersimpan di `plan.md`, bukan di test yang harus dijalankan ulang untuk dibaca.
2. **`standalone = false` + boot simulator dipertahankan**, bukan di-revert. Fact-check membuktikan
   ia memperbaiki keterjangkauan `securityd` (`-25291` → `-34018`); itu konfigurasi test yang benar
   terlepas dari Step 2. Bukan cargo-cult.
3. **Tidak ada tipe exception baru untuk `-34018`.** Lihat KDoc `failureFor()`.
4. **Harness XCTest tetap jalur yang dipilih, bukan trik `codesign`** atas `test.kexe` dengan
   entitlements plist. Sekalipun berhasil, ia menguji `KeychainSecureStorage` di bawah konfigurasi
   entitlement yang tidak pernah dikirim ke produksi. Keystore/Keychain adalah integration test —
   nilainya justru dari dijalankan di konteks proses yang sama dengan produksi, persis seperti
   `connectedAndroidDeviceTest` Android yang sudah jalan.

## Next action

**`plan.md` Step 2a:** ekspor `core_crypto` lewat framework `ComposeApp` yang sudah ada —
`api(projects.libs.coreCrypto)` di `composeApp/build.gradle.kts` `commonMain` plus
`export(projects.libs.coreCrypto)` di blok `binaries.framework`. Ini memakai ulang build phase
`"Compile Kotlin Framework"` yang sudah ada di `iosApp.xcodeproj`, jadi tidak ada mesin baru.

Regresi yang harus dicek di step itu: `:composeApp:assembleDebug` dan `:androidApp` masih build —
satu-satunya bagian Step 2 yang menyentuh Android.
