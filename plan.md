# Plan: Keychain iOS diuji di app bundle sungguhan (harness XCTest), CI hijau

> **Status: in progress** (24 Agustus 2026). Step 1 selesai & landing; Step 2 menunggu pembacaan
> `KC-PROBE` dari run Step 1 (lihat `handoff.md` → Next action).
>
> **Supersede:** dokumen ini menggantikan plan probe sebelumnya. Plan itu **sudah selesai dan
> berhasil** — Step 1 (artifact + cache) dan Step 2 (probe matrix) landing di `bac5589`, dan
> hasilnya menjawab pertanyaannya. Yang berubah: jawaban probe membatalkan diagnosis yang mendasari
> Step 3-nya, jadi Step 3–5 lama tidak lagi relevan dan diganti isi dokumen ini.

---

## 1. Context

### Apa yang dibuktikan run probe

Ke-15 panggilan `SecItem*` — termasuk kedua kontrol account-scoped — balik dengan status yang sama:

```
KC-PROBE context bundleIdentifier=null
KC-PROBE context executablePath=.../build/bin/iosSimulatorArm64/debugTest/test.kexe
KC-PROBE seed  account=a accessible=false -> status=-25291 errSecNotAvailable   (SecItemAdd)
KC-PROBE P01 copyMatching account,limitOne,returnData,DPK -> -25291   (kontrol, harusnya 0)
KC-PROBE P08 SecItemDelete account,DPK                    -> -25291   (kontrol, harusnya 0)
KC-PROBE P02..P07, P09..P11                               -> -25291   (semuanya)
```

**Diagnosis lama batal.** Anggapan "query account-scoped jalan, account-less tidak" adalah
**artefak pelaporan**: exception dari `@AfterTest` menimpa exception asli dari body test di runner
kotlin.test, jadi `get_absent_key_is_null` — yang sebenarnya melempar `ReadFailure` dari `get()` —
dilaporkan sebagai `DeleteFailure`. Terkonfirmasi di XML artifact: ke-6 test melaporkan
`DeleteFailure(rootCause=null)` meski jalur kodenya berbeda-beda. Lima commit sebelum ini mengejar
pola yang tidak pernah ada.

### Akar masalah sebenarnya (diverifikasi, bukan ditebak)

Dibongkar dari bytecode KGP 2.3.0 di cache Gradle lokal:

- `KotlinNativeSimulatorTest` punya `Property<Boolean> standalone`, dan factory-nya memanggil
  `standalone.convention(true)`.
- Perintah yang dibangun `KotlinNativeSimulatorTest$testCommand$1`:
  `/usr/bin/xcrun simctl spawn --standalone <device> <test.kexe> -- <args>`.

`simctl spawn --standalone` menjalankan proses **tanpa** di-bootstrap ke launchd simulator, jadi
`securityd` tidak terjangkau — setiap permintaan Keychain balik `errSecNotAvailable`. Ditambah
`bundleIdentifier=null` dan executable berupa Mach-O telanjang (`test.kexe`, bukan `.app`), proses
itu juga tidak punya entitlement `application-identifier`/`keychain-access-groups`.

KGP sendiri sudah mengantisipasi ini; ada string error di dalam jar-nya: *"The problem can be that
you have not booted the required device or have configured the task to a different simulator."*

**Kesimpulan:** binary test Kotlin/Native bukan tempat untuk menguji Keychain. Tidak ada bentuk query
yang bisa memperbaikinya.

### Target

1. `iosSimulatorArm64Test` hijau, berisi 63 test murni-komputasi yang memang tidak butuh Keychain.
2. `KeychainSecureStorage` diuji di dalam **app bundle iOS sungguhan** lewat target XCTest yang
   di-host `iosApp` — satu-satunya konteks yang punya entitlement Keychain.
3. `KeychainSecureStorage.clear()` dikembalikan ke bentuk kanonik; desain enumerate-lalu-hapus yang
   ada sekarang dibangun di atas premis yang sudah terbukti salah.

### Kendala yang membentuk plan ini

**Tidak ada akses macOS sama sekali.** Xcode tidak bisa dibuka, `xcodebuild` tidak bisa dijalankan,
`project.pbxproj` harus diedit buta dan hanya bisa divalidasi di CI. Ini risiko utama Step 2 dan
sudah dimitigasi eksplisit di bawah (gate cepat + jalan keluar XcodeGen).

---

## 2. Steps

### Step 1 — Bikin CI hijau dulu, sambil ambil satu fakta gratis

Satu push, tidak bergantung pada Step 2. Setelah step ini ketiga job harus hijau.

**a. `libs/core_crypto/src/iosMain/.../storage/KeychainSecureStorage.kt`**

- Buang `matchingAccounts()` seluruhnya dan kembalikan `clear()` ke bentuk kanonik satu panggilan
  (`SecItemDelete` dengan `class`+`service`, tanpa account). Alasannya sekarang jelas: bukan bentuk
  query yang bermasalah, jadi tidak ada gunanya mempertahankan jalur enumerasi yang lebih rumit.
  Hapus juga blok komentar `clear()` di `:112-120` yang mendokumentasikan kesimpulan salah, dan
  KDoc kelas `:57-68` yang mengklaim `kSecUseDataProtectionKeychain` membuat `SecItem*` jalan di
  binary test — klaim itu terbantah.
- Petakan `errSecNotAvailable` (-25291) ke **`SecureStorageException.NotAvailable`**, bukan
  `ReadFailure`/`DeleteFailure`. Tipe itu sudah ada di
  `commonMain/.../exception/SecureStorageException.kt:57` dan sampai sekarang tidak pernah dipakai —
  KDoc-nya harfiah menyebut *"iOS: Keychain services unavailable"*. Kalau ini sudah ada sejak awal,
  log CI pertama akan langsung membaca `NotAvailable` dan tidak ada satupun dari lima commit itu
  terjadi.
- Hapus `logOsStatusDiagnostic()` (`:185-197`) beserta pemanggilnya. Teksnya hardcode
  `"SecItemDelete failed"` padahal juga dipanggil dari jalur baca — pesan itu sendiri ikut
  menyesatkan.

**b. `libs/core_crypto/src/iosTest/.../storage/KeychainSecureStorageTest.kt` — hapus.**
Diimplementasi ulang sebagai XCTest di Step 2. Membiarkannya di sini berarti mempertahankan 6 test
yang secara struktural tidak mungkin lulus.

**c. `libs/core_crypto/build.gradle.kts` — fact-check yang nebeng.**

```kotlin
tasks.withType<org.jetbrains.kotlin.gradle.targets.native.tasks.KotlinNativeSimulatorTest>()
    .configureEach {
        standalone.set(false)   // default true -> `simctl spawn --standalone`, tanpa akses securityd
        device.set("booted")
    }
```

Plus boot simulator di job `ios-test` sebelum step Gradle:

```yaml
- name: Boot an iOS simulator
  run: |
    UDID=$(xcrun simctl list devices available -j \
      | python3 -c "import json,sys;d=json.load(sys.stdin)['devices'];print(next(x['udid'] for v in d.values() for x in v if 'iPhone' in x['name']))")
    xcrun simctl boot "$UDID"
    xcrun simctl bootstatus "$UDID"
```

**Ini bukan pengganti Step 2 dan bukan taruhan** — `KeychainSecureStorageTest` sudah dihapus, jadi
CI hijau apa pun hasilnya. Tapi `KeychainQueryProbeTest` masih ada di run ini, dan kalau P01/P08
berubah jadi `status=0`, artinya `--standalone` memang satu-satunya penghalang dan **harness Step 2
mungkin tidak diperlukan sama sekali**. Itu keputusanmu setelah lihat angkanya, bukan keputusan yang
diam-diam saya ambil. Kalau tetap -25291, Step 2 terkonfirmasi wajib.

**d. Perbaiki masking exception** — akar dari salah diagnosis ini. Di test manapun yang punya
teardown destruktif, teardown tidak boleh melempar dan menutupi kegagalan asli:

```kotlin
@AfterTest fun tearDown() = runTest { runCatching { storage.clear() } }
```

Berlaku untuk `androidDeviceTest/.../AndroidKeystoreSecureStorageTest.kt` yang polanya sama, dan
untuk XCTest baru di Step 2.

**Implikasi platform:** (a),(b),(c) iOS saja. (d) menyentuh test Android juga. Tidak ada perubahan
kode produksi Android.

---

### Step 2 — Harness XCTest di dalam app bundle sungguhan

Ini yang memberi coverage Keychain nyata. Empat bagian.

**a. Ekspor `core_crypto` lewat framework `ComposeApp` yang sudah ada** —
`composeApp/build.gradle.kts`:

```kotlin
iosTarget.binaries.framework {
    baseName = "ComposeApp"
    isStatic = true
    export(projects.libs.coreCrypto)      // butuh api(), bukan implementation()
}
// dan di sourceSets:
commonMain.dependencies { api(projects.libs.coreCrypto) }
```

Dipilih karena **memakai ulang jalur yang sudah jalan**: `iosApp.xcodeproj` sudah punya build phase
`"Compile Kotlin Framework"` yang memanggil
`./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`. Tidak ada mesin baru.

Alternatif "beri `core_crypto` framework sendiri" ditolak: framework-nya saat ini dideklarasikan
`baseName = "libs:core_cryptoKit"` (`libs/core_crypto/build.gradle.kts:36`) — mengandung titik dua,
nama framework tidak valid, boilerplate template yang belum pernah dipakai. Memakainya berarti
memperbaiki itu **dan** menulis build phase embed baru dari nol.

**b. Target test + shared scheme di `iosApp/iosApp.xcodeproj/project.pbxproj`**

- Target baru `CoreCryptoTests`, `productType = com.apple.product-type.bundle.unit-test`,
  `TEST_HOST` = target `iosApp`, `BUNDLE_LOADER = $(TEST_HOST)`. Host aplikasi inilah yang memberi
  proses test `application-identifier` dan akses Keychain — inti dari seluruh latihan ini.
- Shared scheme baru `iosApp/iosApp.xcodeproj/xcshareddata/xcschemes/iosApp.xcscheme` dengan
  `CoreCryptoTests` terdaftar di `<TestAction>`. **Saat ini tidak ada shared scheme sama sekali**
  (dicek: `find iosApp -name "*.xcscheme"` kosong), dan tanpa itu `xcodebuild -scheme` gagal di CI.
  `.gitignore:15` sudah meng-whitelist `!*.xcodeproj/xcshareddata/`, jadi tinggal ditambahkan.

**c. `iosApp/CoreCryptoTests/KeychainSecureStorageTests.swift`**

Enam kasus yang sama dengan `SecureStorageContractTest` (`commonTest/.../SecureStorageContractTest.kt`)
— pakai itu sebagai daftar kasus, jangan mengarang ulang: `put_get_round_trip`,
`get_absent_key_is_null`, `put_overwrites`, `contains_reflects_presence`, `remove_deletes_entry`,
`clear_removes_everything`. `service` unik per instance, `tearDown` non-throwing.

`SecureStorage` semuanya `suspend`, yang diekspor ke ObjC sebagai varian
`...completionHandler:`. Rencana: tulis test sebagai `func testX() async throws` dan andalkan
bridging async/await Swift. **Fallback kalau bridging tidak muncul seperti yang diharapkan:** pakai
bentuk completion-handler langsung dengan `XCTestExpectation` — lebih bertele-tele tapi nol asumsi.
Putuskan dari pesan compiler run pertama, jangan menebak dua kali.

**d. Job CI baru di `.github/workflows/core-crypto-tests.yml`**

```yaml
ios-keychain-test:
  name: iOS Keychain Test (app bundle)
  runs-on: macos-latest
  steps:
    # checkout, setup-java, cache ~/.konan  — sama seperti job ios-test
    - name: Validate Xcode project            # gate cepat, gagal dalam ~30 detik
      run: xcodebuild -list -project iosApp/iosApp.xcodeproj
    - name: Run Keychain tests
      run: |
        xcodebuild test \
          -project iosApp/iosApp.xcodeproj \
          -scheme iosApp \
          -destination 'platform=iOS Simulator,name=iPhone 17' \
          -only-testing:CoreCryptoTests
    # upload-artifact untuk .xcresult saat gagal
```

Step **"Validate Xcode project"** itu mitigasi utama untuk pengeditan `pbxproj` secara buta:
`pbxproj` yang rusak ketahuan dalam puluhan detik, bukan setelah build framework KMP penuh
(~5 menit). Sertakan `.xcresult` di upload-artifact — itu padanan Step 1 plan sebelumnya untuk dunia
xcodebuild, dan tanpanya kegagalan `xcodebuild` juga tidak terbaca dari luar runner.

**Jalan keluar kalau `pbxproj` terbukti rewel:** kalau setelah **dua** run gate `xcodebuild -list`
masih menolak project-nya, berhenti mengedit tangan — pindah ke **XcodeGen** (`iosApp/project.yml`,
`xcodegen generate` di CI). Spec YAML jauh lebih mungkin ditulis benar tanpa Xcode daripada plist
gaya lama. Ini plan B yang sadar, bukan improvisasi saat sudah buntu.

---

### Step 3 — Bersih-bersih (setelah Step 2 hijau)

- Hapus `libs/core_crypto/src/iosTest/.../KeychainQueryProbeTest.kt`.
- Blok `standalone.set(false)` + step boot simulator: **pertahankan kalau** fact-check Step 1
  menunjukkan itu memperbaiki akses Keychain (berarti konfigurasi test yang benar); hapus kalau
  tidak berpengaruh, supaya tidak meninggalkan konfigurasi cargo-cult.
- Blok `showStandardStreams` di `libs/core_crypto/build.gradle.kts:105-114`: pertahankan, ganti
  komentar `// TODO: diagnostic-only...` dengan alasan permanen.
- Perbarui `docs/` + `README` modul kalau menyebut cakupan test iOS.

---

### Step 4 — (Terpisah) Tiga warning `This cast can never succeed`

`KeychainSecureStorage.kt:75` (`put()`), `:210` (`cfString()`, jalur panas tiap query),
`text/TextNormalizer.ios.kt:7`. Commit `9ce84d5` membuktikan cast begini bisa melempar
`TypeCastException` saat runtime meski compile bersih. Sekarang ketiganya jalan, jadi **bukan**
bagian dari fix ini — commit terpisah setelah CI hijau.

---

## 3. Verification

### Lokal, dari Linux — pakai ini sebelum tiap push

Distribusi Kotlin/Native prebuilt Linux menyertakan klib platform iOS, jadi seluruh
`iosMain`/`iosTest` bisa di-type-check tanpa macOS:

```bash
K=~/.konan/kotlin-native-prebuilt-linux-x86_64-2.3.0
$K/bin/konanc -target ios_simulator_arm64 -p library -o out <file>.kt
$K/bin/klib dump-metadata $K/klib/platform/ios_simulator_arm64/org.jetbrains.kotlin.native.platform.Security \
  | grep 'val kSecUseDataProtectionKeychain'
```

Ini yang menangkap `CFGetTypeID` tidak ada di binding sebelum menghabiskan satu run CI. Yang **tidak**
bisa diverifikasi lokal: `project.pbxproj`, `.xcscheme`, dan kode Swift — semuanya hanya lewat CI,
karena itulah gate `xcodebuild -list` ada.

### Lewat CI

**Setelah Step 1** — kriterianya CI **hijau**, ketiganya: `unit-test`, `android-instrumented-test`,
`ios-test`. `iosSimulatorArm64Test` harus `64 tests completed, 0 failed` (63 test lama + probe).
Lalu baca fakta gratisnya:

```
grep KC-PROBE  → P01/P08 status=0    ⇒ --standalone memang penghalangnya; timbang ulang perlu-tidaknya Step 2
               → P01/P08 masih -25291 ⇒ Step 2 terkonfirmasi wajib
```

**Setelah Step 2** — job `ios-keychain-test` hijau, 6 test `CoreCryptoTests` lulus melawan Keychain
sungguhan. Kalau gagal, unduh artifact `.xcresult`.

**Regresi yang harus diawasi:** `composeApp` sekarang punya `api(projects.libs.coreCrypto)` —
pastikan `:composeApp:assembleDebug` dan `:androidApp` masih build. Ini satu-satunya perubahan Step 2
yang menyentuh Android.

---

## 4. Open questions

- **Apakah `standalone=false` saja sudah cukup?** Terjawab oleh run Step 1. Kalau ya, Step 2 jadi
  pilihan (coverage lebih baik), bukan keharusan.
- **Bridging suspend → Swift `async`.** Diputuskan dari pesan compiler run pertama Step 2; fallback
  completion-handler sudah disiapkan.
- **`baseName = "libs:core_cryptoKit"`** di `libs/core_crypto/build.gradle.kts:36` mengandung titik
  dua dan tidak mungkin menghasilkan framework valid. Tidak dipakai plan ini, tapi itu ranjau untuk
  siapa pun yang nanti mau meng-embed `core_crypto` langsung.
- **`vector-report.md`** menyebut eksekusi iOS-sim masih menunggu runtime. Setelah Step 2 hijau,
  status itu bisa ditutup untuk `SecureStorage` — tapi tidak untuk sisa kontrak 2SKD.
