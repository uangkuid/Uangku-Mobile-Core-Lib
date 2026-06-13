# core_navigation

Modul KMP (Kotlin Multiplatform) yang menjadi placeholder untuk sistem **navigasi** di project Uangku. Saat ini modul ini belum memiliki implementasi — strukturnya disiapkan sebagai fondasi untuk navigation layer yang akan dikembangkan.

---

## Daftar Isi
1. [Gambaran Umum](#gambaran-umum)
2. [Status Saat Ini](#status-saat-ini)
3. [Dependency](#dependency)
4. [Rencana Pengembangan](#rencana-pengembangan)

---

## Gambaran Umum

`core_navigation` direncanakan sebagai modul yang menyediakan abstraksi navigasi yang dapat digunakan bersama oleh semua feature module di Android dan iOS — tanpa setiap feature module harus bergantung langsung pada framework navigasi platform tertentu.

---

## Status Saat Ini

Modul ini hanya berisi file platform boilerplate (`Platform.kt`, `Platform.android.kt`, `Platform.ios.kt`) yang dihasilkan secara otomatis. Belum ada logika navigasi yang diimplementasikan.

**Konfigurasi modul:**
- Namespace Android: `com.oratakashi.uangku.core.core_navigation`
- XCFramework name: `libs:core_navigationKit`
- Target platform: Android (minSdk 24), iOS (x64, Arm64, SimulatorArm64)

---

## Dependency

```kotlin
// build.gradle.kts (core_navigation)
commonMain {
    dependencies {
        implementation(libs.kotlin.stdlib)
        // Belum ada library navigasi
    }
}
```

---

## Rencana Pengembangan

Modul ini cocok untuk menampung:

1. **Navigation contract / interface** — definisi route dan action navigasi yang platform-agnostik:
   ```kotlin
   interface AppNavigator {
       fun navigateTo(route: AppRoute)
       fun navigateBack()
       fun navigateBackTo(route: AppRoute, inclusive: Boolean = false)
   }

   sealed class AppRoute {
       object Home : AppRoute()
       object Login : AppRoute()
       data class TransactionDetail(val id: String) : AppRoute()
   }
   ```

2. **Deep link handler** — parsing dan dispatch deep link dari notifikasi atau external intent

3. **Back stack management** — abstraksi atas `NavController` (Android) / coordinator pattern (iOS)

4. **Navigation extensions** — helper function untuk navigasi umum (e.g., `navigateToHome()`, `clearAndNavigateTo()`)

### Opsi implementasi yang bisa dipertimbangkan:
- **Compose Multiplatform Navigation** — jika menggunakan Compose di kedua platform
- **Decompose** (arkivanov) — library navigasi KMP yang mature, mendukung back stack lintas platform
- **Custom coordinator** — pattern sederhana berbasis interface + expect/actual
