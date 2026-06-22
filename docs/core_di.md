# core_di

Modul KMP (Kotlin Multiplatform) yang menjadi placeholder dan dependency carrier untuk **Koin** (dependency injection framework) di seluruh project Uangku. Saat ini modul ini belum memiliki implementasi substantif — fungsinya adalah menjadi titik sentral untuk mengekspos Koin ke seluruh module lain.

---

## Daftar Isi
1. [Gambaran Umum](#gambaran-umum)
2. [Status Saat Ini](#status-saat-ini)
3. [Dependency](#dependency)
4. [Rencana Pengembangan](#rencana-pengembangan)

---

## Gambaran Umum

`core_di` adalah modul KMP yang ditujukan sebagai **fondasi DI layer** bagi seluruh modul di project ini. Dengan memisahkan setup Koin ke modul tersendiri, modul lain cukup depend pada `core_di` untuk mendapatkan akses Koin tanpa perlu masing-masing mendeklarasikan dependency Koin secara berulang.

```
App (androidApp / composeApp / iosApp)
      │
      ├── core_di           ← kumpulkan semua module Koin
      │     ├── core_crypto → cryptoModule, secureStorageModule
      │     ├── core_network → NetworkModule
      │     └── core_database → DatabaseModule
      └── (feature modules)
```

---

## Status Saat Ini

Modul ini saat ini hanya berisi file platform boilerplate (`Platform.kt`, `Platform.android.kt`, `Platform.ios.kt`) yang dihasilkan secara otomatis saat modul dibuat. Belum ada logika DI yang diimplementasikan.

Setiap modul (`core_crypto`, `core_network`, `core_database`) sudah memiliki module Koin masing-masing yang bisa langsung didaftarkan oleh consumer app. `core_di` diharapkan menjadi tempat **agregasi** module-module tersebut di masa depan.

---

## Dependency

```kotlin
// build.gradle.kts (core_di)
commonMain {
    dependencies {
        implementation(libs.kotlin.stdlib)
        api(libs.bundles.koin)   // koin-core
    }
}
```

---

## Rencana Pengembangan

Modul ini cocok untuk menampung:

1. **Aggregated module initializer** — satu fungsi yang mendaftarkan semua module core sekaligus ke Koin:
   ```kotlin
   fun initCoreModules(application: Application) {
       startKoin {
           androidContext(application)
           modules(
               cryptoModule,
               secureStorageModule,
               // networkModule, dll
           )
       }
   }
   ```

2. **Koin extension helpers** — extension functions untuk memudahkan inject di common code

3. **KoinComponent base classes** — jika ada pattern inject yang dipakai berulang di banyak tempat
