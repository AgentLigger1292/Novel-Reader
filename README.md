<div align="center">

# 📖 Novel Reader Android

**A clean, fast, and modern native Android web-novel & light-novel reader built with Jetpack Compose — architected following the Kotatsu app flow.**

[![Release](https://img.shields.io/github/v/release/AgentLigger1292/Novel-Reader?color=blue&logo=github)](https://github.com/AgentLigger1292/Novel-Reader/releases)
[![Android CI](https://github.com/AgentLigger1292/Novel-Reader/actions/workflows/android.yml/badge.svg)](https://github.com/AgentLigger1292/Novel-Reader/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg?logo=android)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Download APK Terbaru (v0.2.1)](https://github.com/AgentLigger1292/Novel-Reader/releases/latest) • [Fitur](#-fitur-utama) • [Sumber Novel](#-sumber-novel-didukung) • [Arsitektur](#-arsitektur-kotatsu-style) • [Panduan Build](#️-cara-build--install)

</div>

---

## 🌟 Fitur Utama

- **🚀 Native & Ringan**: Murni **Kotlin + Jetpack Compose**, arsitektur MVVM (ViewModel per layar, Repository, manual DI).
- **📚 Multi-Source Novel**: Browse & search dari beberapa situs novel, dengan **infinite-scroll pagination** dan search debounce.
- **🔊 Text-to-Speech (TTS) Bawaan**: Dengarkan novel dibacakan per-paragraf dengan *auto-scroll* layar mengikuti bacaan.
- **📥 Download Offline Tahan Navigasi**: Unduh seluruh chapter via **WorkManager** — keluar dari layar pun unduhan tetap lanjut, lengkap dengan notifikasi progress.
- **🔔 Feed Update Otomatis**: Novel favourite diperiksa berkala oleh tracker worker; chapter baru muncul di tab **Feed** + notifikasi.
- **⏯️ Lanjut Baca Presisi**: Posisi baca (scroll & percent per chapter) **tersimpan otomatis** ke database dan dilanjutkan saat dibuka lagi — dari History, Details, maupun Feed.
- **🛡️ Bypass Cloudflare Manual**: WebView internal untuk menyelesaikan challenge sekali klik; cookies sesi disinkronkan ke seluruh aplikasi.
- **🎨 Reader Kustom**: Tema Dark / OLED / Nordic / Sepia / Light, pilihan font, ukuran teks, spasi baris, perataan — **semua tersimpan permanen**.
- **💾 Data Aman**: Room database + atomic file writes; data lama (v0.1.x) dimigrasi otomatis saat pertama dibuka.

---

## 🌐 Sumber Novel Didukung

| Sumber | URL Situs | Engine | Status |
|---|---|---|---|
| **Baca Light Novel** | `https://bacalightnovel.co` | Themesia / WordPress | ✅ Aktif (Browse, Search, 1000+ Chapters) |
| **Sakura Novel** | `https://sakuranovel.id` | Custom ZNovel / WP | ✅ Aktif (Browse, Search, Multi-markup) |
| **Mistmint Haven** | `https://www.mistminthaven.com` | Next.js (REST API) | ✅ Aktif (Auto-skip paywall, S3 Covers) |

Parser terverifikasi langsung terhadap DOM live situs dan dilindungi unit test berbasis fixture HTML/JSON asli (35+ test).

---

## 📱 Cara Pakai

1. **Jelajahi**: Tab **Explore** → pilih source → scroll (auto load-more) atau cari judul.
2. **Cloudflare**: Kalau terkena challenge, tekan **CF (shield)** → selesaikan verifikasi di WebView → **Done**.
3. **Favourite**: Tekan ikon ❤️ di halaman detail novel → otomatis masuk pelacakan update Feed.
4. **Download**: Ikon ⬇️ di halaman detail → unduhan berjalan di background (notifikasi progress), bisa dibaca dari tab **Explore → ikon download** atau langsung offline.
5. **Baca**: Pilih chapter → posisi baca tersimpan otomatis; buka lagi dari **History** untuk lanjut persis di posisi terakhir.
6. **Dengarkan**: Ikon 🔊 di toolbar reader → TTS membaca paragraf dengan auto-scroll.

---

## 🏗️ Arsitektur (Kotatsu-style)

Single Gradle module, **package-by-feature**, MVVM + Repository, manual DI (tanpa Hilt) — pola yang sama dipakai aplikasi [Kotatsu](https://github.com/Kotatsu-Redo/Kotatsu-Redo), diadaptasi untuk teks novel.

```
com.novelreader/
├── core/
│   ├── db/          # Room: novels, chapters, history,
│   │                # favourites(+categories), sources, tracks
│   ├── parser/      # SourcesRepository (facade atas parser)
│   ├── prefs/       # AppSettings (SharedPreferences ter-tipe)
│   ├── migration/   # migrasi one-shot JSON lama → Room
│   └── AppContainer # manual DI (db, repos, prefs, workers)
├── source/          # Parser website (TIDAK berubah sejak v0.1.x)
│   ├── NovelSource.kt + wp/ (WordPress base: WpParse, ChapterRules)
│   └── BacaLightNovel / SakuraNovel / MistmintHaven
├── network/         # HttpClient (rate-limit + retry), SessionWebView (CF), CoverLoader
├── work/            # DownloadWorker & TrackWorker (WorkManager)
├── data/            # DownloadStore (offline files), NovelCache (LruCache)
└── ui/
    ├── AppNav       # bottom nav: Feed · History · Favourite · Explore
    ├── explore/  details/  reader/  lists/   # per-feature screen + ViewModel
    └── ReaderTts, ReaderTheme, HtmlText       # shared reader pieces
```

Alur data ringkas: `Explore → SourcesRepository → NovelSource (parser)` → detail di-cache ke Room (`NovelEntity` + `ChapterEntity`) → `HistoryRepository` menyimpan progress baca → `DownloadWorker`/`TrackWorker` menangani kerja background. Detail lengkap: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

---

## 🛠️ Cara Build & Install

### Prasyarat
- **JDK 17** (Eclipse Adoptium direkomendasikan)
- **Android SDK** — `compileSdk 36`, `minSdk 26`
- `adb` opsional untuk instalasi langsung

### Windows (PowerShell)
```powershell
cd novel-reader

$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# unit test (41 tests: parser fixtures + migrasi + chapter rules)
.\gradlew.bat testDebugUnitTest

# build release APK
.\gradlew.bat assembleRelease
```

### Linux / macOS (Bash)
```bash
cd novel-reader
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew testDebugUnitTest
./gradlew assembleRelease
```

Output APK:
- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release-unsigned.apk`

### Menambah Sumber Baru
Situs WordPress baru cukup sebagai subclass `WordPressSource` (~60 baris selector + URL candidates) — lihat [docs/CUSTOMIZING.md](docs/CUSTOMIZING.md). Situs non-WordPress (API khusus) implementasi `NovelSource` langsung seperti `MistmintHavenSource`.

---

## 📄 Disclaimer & Lisensi

- Projek ini dikembangkan untuk tujuan **edukasi dan penggunaan personal**.
- Tidak berafiliasi dengan penyedia konten manapun; konten diambil dari web publik — hormati ToS & hak cipta situs sumber.
- Tidak ada solver captcha otomatis; Cloudflare diselesaikan manual oleh user via WebView.
- Didistribusikan di bawah **MIT License** — lihat [LICENSE](LICENSE).
