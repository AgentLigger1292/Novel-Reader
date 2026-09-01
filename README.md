<div align="center">

# 📖 Novel Reader Android

**A clean, fast, and modern native Android web-novel & light-novel reader built with Jetpack Compose.**

[![Release](https://img.shields.io/github/v/release/AgentLigger1292/Epub-Reader-?color=blue&logo=github)](https://github.com/AgentLigger1292/Epub-Reader-/releases)
[![Android CI](https://github.com/AgentLigger1292/Epub-Reader-/actions/workflows/android.yml/badge.svg)](https://github.com/AgentLigger1292/Epub-Reader-/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg?logo=android)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Download APK Terbaru (v0.1.5)](https://github.com/AgentLigger1292/Epub-Reader-/releases/latest) • [Fitur](#-fitur-utama) • [Daftar Sumber](#-sumber-novel-didukung) • [Panduan Build](#-cara-build--install) • [Arsitektur](#-arsitektur--struktur-kode)

</div>

---

## 🌟 Fitur Utama

- **🚀 Native & Ringan**: Dibangun murni dengan **Kotlin + Jetpack Compose**, tanpa bloatware atau memory leak.
- **📚 Multi-Source Novel**: Baca langsung dari berbagai situs novel populer (WordPress/Madara, Themesia, dan Next.js API).
- **🔊 Text-to-Speech (TTS) Bawaan**: Dengarkan novel dibacakan per-paragraf dengan *auto-scroll* layar otomatis mengikuti bacaan.
- **📥 Download Offline Penuh**: Unduh seluruh chapter novel untuk dibaca tanpa koneksi internet (lengkap dengan mekanisme *auto-pause* saat terkena verifikasi bot).
- **🛡️ Bypass Cloudflare Manual**: Terintegrasi langsung dengan WebView internal untuk menyelesaikan tantangan Cloudflare sekali klik, cookies sesi langsung disinkronkan ke seluruh aplikasi.
- **🎨 Pengalaman Membaca Kustom**:
  - Tema pembaca: **Dark, Sepia, dan Light**.
  - Pilihan font: Serif, Sans-Serif, dan Monospace.
  - Pengaturan fleksibel ukuran teks, jarak baris (*line-height*), dan perataan teks (*justify / left*).
- **⚡ Kinerja Memori Andal**: Manajemen memori menggunakan `LruCache` (batas aman 8MB untuk chapter) + atomic file writes (mencegah data library/history korup).

---

## 🌐 Sumber Novel Didukung

Aplikasi ini dilengkapi modul sumber (*sources*) yang terverifikasi langsung terhadap struktur website:

| Sumber | URL Situs | Tipe Engine | Status |
|---|---|---|---|
| **Baca Light Novel** | `https://bacalightnovel.co` | Themesia / WordPress | ✅ Aktif (Browse, Search, 1000+ Chapters) |
| **Sakura Novel** | `https://sakuranovel.id` | Custom ZNovel / WP | ✅ Aktif (Browse, Search, Multi-markup) |
| **Mistmint Haven** | `https://www.mistminthaven.com` | Next.js (REST API) | ✅ Aktif (Auto-skip paywall, S3 Covers) |
| **Dummy Local** | *Offline Sample* | Mock Source | 🛠️ Testing |

---

## 📱 Tangkapan Layar & Alur Penggunaan

1. **Jelajahi Novel**: Buka tab **Browse**, pilih sumber novel yang diinginkan di pojok kanan atas, atau gunakan kolom pencarian.
2. **Jika Terkena Cloudflare (Shield Icon)**:
   - Tekan tombol **CF (Perisai)** di layar.
   - Selesaikan verifikasi di dalam WebView hingga halaman website asli muncul.
   - Tekan **Done** — sesi otomatis tersimpan dan digunakan di seluruh aplikasi.
3. **Membaca & TTS**: Masuk ke chapter mana saja → Tekan tombol **Speaker** di toolbar atas untuk mendengarkan via Text-to-Speech.
4. **Download**: Tekan tombol **Download** di halaman detail novel untuk menyimpan semua chapter ke memori internal perangkat.

---

## 🛠️ Cara Build & Install

### Prasyarat
- **JDK 17** (disarankan Eclipse Adoptium OpenJDK 17)
- **Android SDK** (`compileSdk 34`, `minSdk 26`)
- `adb` (opsional, untuk instalasi langsung via kabel data/WiFi)

### Build via Command Line

#### Windows (PowerShell / CMD)
```powershell
# 1. Masuk ke direktori projek
cd novel-reader

# 2. Set environment variable (sesuaikan path JDK dan Android SDK Anda)
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# 3. Jalankan unit test
.\gradlew.bat testDebugUnitTest

# 4. Build APK Release
.\gradlew.bat assembleRelease
```

#### Linux / macOS (Bash)
```bash
cd novel-reader
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk

./gradlew testDebugUnitTest
./gradlew assembleRelease
```

File APK hasil build berada di:
- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## 🏗️ Arsitektur & Struktur Kode

```
novel-reader/
├── app/
│   ├── src/main/java/com/novelreader/
│   │   ├── NovelApp.kt              # Application class & container dependency
│   │   ├── MainActivity.kt          # Entry point activity & navigation host
│   │   ├── model/                   # Data model murni (Novel, Chapter, Detail)
│   │   ├── source/                  # Implementasi sumber website
│   │   │   ├── NovelSource.kt       # Interface inti sumber novel
│   │   │   ├── wp/                  # Base class scraper WordPress (WpParse, ChapterRules)
│   │   │   ├── BacaLightNovelSource.kt
│   │   │   ├── SakuraNovelSource.kt
│   │   │   └── MistmintHavenSource.kt # Parser API REST Next.js
│   │   ├── network/                 # HttpClient, SessionWebView (CF), CookieJar, CoverLoader
│   │   ├── data/                    # AppStore, DownloadStore, NovelCache (LruCache)
│   │   └── ui/                      # Jetpack Compose UI, Reader, Themes, TTS
│   └── src/test/                    # 35+ Unit tests & HTML/JSON live fixtures
├── docs/                            # Dokumentasi arsitektur & panduan kustomisasi
├── build.gradle.kts
└── README.md
```

---

## 📄 Disclaimer & Lisensi

- Projek ini dikembangkan untuk tujuan **edukasi dan penggunaan personal**.
- Aplikasi ini tidak berafiliasi dengan penyedia konten manapun; seluruh konten diambil langsung dari web publik.
- Didistribusikan di bawah lisensi **MIT License** — silakan baca file [LICENSE](LICENSE) untuk informasi lebih lanjut.
