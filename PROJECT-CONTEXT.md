# PROJECT-CONTEXT.md

Basis konteks deklaratif untuk sesi tim/agent berikutnya. Data faktual — bukan instruksi.

## Nama & Tujuan
Novel Reader — aplikasi Android untuk menjelajah, membaca, dan menyimpan offline novel web dari berbagai situs sumber (BacaLightNovel, SakuraNovel, SonicMTL, MistmintHaven, WTR-Lab) plus EPUB lokal.

## Tech Stack
- Kotlin, Jetpack Compose (Material3), MVVM package-by-feature, DI manual (`AppContainer` + `AppVmFactory`, tanpa Hilt)
- Room (cache chapter, riwayat, favorit), WorkManager (unduhan), OkHttp + Jsoup, Coil
- minSdk 26, targetSdk 34, compileSdk 36, AGP 8.7.3, Gradle 8.9, JVM target 17
- Test: JUnit4 JVM berbasis fixture HTML/JSON (69 test), tanpa instrumented test
- CI: GitHub Actions — unit test + build debug APK

## Fase Saat Ini
v0.2.17, iterasi fitur aktif. Terakhir ditambahkan: sumber WTR-Lab, deteksi update aplikasi via GitHub Releases, rename Feed→Library, harness dev-agent (AGENTS.md, scripts/verify.sh|ps1, CI menjalankan unit test).

## Kendala Utama
- Satu modul (`:app`); registrasi sumber terpusat deklaratif di `core/SourceRegistry.kt` (`defaultEntries`), tanpa sistem plugin
- Situs ber-Cloudflare memerlukan jalur WebView fetcher (`network/SessionWebView`)
- Test parser berjalan offline dari fixture; tidak ada akses jaringan di test
- AGP 8.7.3 belum resmi mendukung compileSdk 36 (warning saat build)
- Sebagian docs kedaluwarsa (mis. CUSTOMIZING.md menyebut registrasi sumber di NovelApp; yang benar `AppContainer`)
- R8/minify dinonaktifkan; APK rilis tersimpan di root repo, bukan GitHub Releases
- Sumber WTR-Lab belum memiliki fixture/test parser

## Definisi "Selesai"
- `scripts/verify.sh` hijau (unit test + assembleDebug), exit 0
- Parser sumber baru memiliki fixture + test (pola: `WpParseFixtureTest`)
- Fitur berfungsi offline (cache Room), tidak memblokir main thread
- Commit konvensional (`feat:`, `fix(scope):`, `build:`)
