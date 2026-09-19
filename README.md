<div align="center">

# 📖 Novel Reader Android

**A clean, fast, and modern native Android web-novel & light-novel reader built with Jetpack Compose — architected following the Kotatsu app flow.**

[![Release](https://img.shields.io/github/v/release/AgentLigger1292/Novel-Reader?color=blue&logo=github)](https://github.com/AgentLigger1292/Novel-Reader/releases)
[![Android CI](https://github.com/AgentLigger1292/Novel-Reader/actions/workflows/android.yml/badge.svg)](https://github.com/AgentLigger1292/Novel-Reader/actions)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple.svg?logo=kotlin)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%208.0%2B%20(API%2026%2B)-green.svg?logo=android)](https://android.com)
[![License](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[Fitur Utama](#-fitur-utama) • [Sumber Novel](#-sumber-novel-didukung) • [Terjemahan Mesin & AI](#-mesin-terjemahan-mtl--ai) • [Arsitektur](#-arsitektur-kotatsu-style) • [Panduan Build](#️-cara-build--install)

</div>

---

## 🌟 Fitur Utama

- **🚀 Native, Responsif & Ringan**: Murni **Kotlin + Jetpack Compose**, arsitektur MVVM (ViewModel per layar, Repository, manual DI).
- **📚 Multi-Source Web Novel**: Jelajahi dan cari novel dari 6+ sumber web novel terkemuka dengan **auto-pagination** sekuensial dan pencarian ter-debounce.
- **🏷️ Filter Genre Interaktif**: Filter novel berdasarkan genre favorit langsung dari bilah pencarian Explore untuk setiap sumber.
- **🌐 Machine Translation (MTL) & AI Streaming**: Terjemahkan bab berbahasa asing langsung ke Bahasa Indonesia (atau bahasa lain) menggunakan **Google Translate gratis (tanpa API key)** maupun model AI canggih (**Gemini** & **OpenAI-compatible endpoints**).
- **📱 Tata Letak Adaptif (Tablet & Ponsel)**: Grid adaptif otomatis menyesuaikan ukuran layar — tetap rapi dan proporsional baik dalam mode portrait ponsel maupun landscape/tablet.
- **🖼️ Smart Cover Caching**: Pemuatan sampul buku cepat dan tahan Cloudflare menggunakan headless WebView canvas rendering, deduplikasi unduhan, dan cache disk PNG otomatis.
- **📖 Impor EPUB Lokal**: Impor file ebook `.epub` dari penyimpanan lokal langsung ke perpustakaan.
- **🔊 Text-to-Speech (TTS) Bawaan**: Dengarkan bab novel dibacakan per-paragraf dengan auto-scroll layar otomatis mengikuti bacaan.
- **📥 Unduh Offline Background**: Unduh seluruh bab via **WorkManager** — unduhan tetap berlanjut di latar belakang lengkap dengan notifikasi kemajuan.
- **⏯️ Lanjut Baca Presisi**: Posisi baca (scroll & persentase bab) tersimpan otomatis ke Room database dan dilanjutkan saat dibuka kembali dari History atau Library.
- **🛡️ Bypass Cloudflare Manual**: WebView internal untuk menyelesaikan challenge verifikasi sekali klik dengan sinkronisasi cookie sesi ke seluruh aplikasi.
- **🎨 Pengaturan Reader Lengkap**: Tema Dark / OLED / Nordic / Sepia / Light, pilihan font, ukuran teks, spasi baris, dan perataan teks tersimpan permanen.

---

## 🌐 Sumber Novel Didukung

| Sumber | URL Situs | Tipe Engine | Fitur & Status |
|---|---|---|---|
| **WoopRead** | `https://woopread.com` | Next.js App Router + REST API | ✅ Browse, Search, 35 Genre, API Chapters |
| **WTR-Lab** | `https://wtr-lab.com` | Next.js Page Router + JSON Data | ✅ Popular, Search, Genre, Token Replacement |
| **Baca Light Novel** | `https://bacalightnovel.co` | Themesia / WordPress | ✅ Browse, Search, Genre, 1000+ Chapters |
| **Sakura Novel** | `https://sakuranovel.id` | ZNovel / WordPress | ✅ Browse, Search, Genre, Multi-markup |
| **Mistmint Haven** | `https://mistminthaven.com` | Next.js + REST API | ✅ Browse, Search, Category Filter, S3 Covers |
| **Sonic MTL** | `https://sonicmtl.com` | Madara / WordPress | ✅ Browse, Search, Genre, Ajax Chapters |
| **Local EPUB** | *Offline Storage* | EPUB ZIP Parser | ✅ Impor lokal, membaca offline tanpa internet |

Semua parser dilindungi oleh unit test berbasis fixture HTML/JSON nyata (**87 unit test** lulus uji).

---

## 🤖 Mesin Terjemahan (MTL & AI)

Novel Reader menyediakan 3 mode penerjemahan bab di dalam reader:

1. **Google MTL (Gratis / Siap Pakai)**:
   - Terjemahan mesin instan menggunakan public Google Translate engine.
   - **Tanpa perlu API key, registrasi, atau konfigurasi khusus**. Cukup 1 klik untuk langsung membaca dalam Bahasa Indonesia.
2. **Google Gemini API**:
   - Terjemahan kontekstual berkualitas tinggi menggunakan model Gemini (misal `gemini-2.0-flash`, `gemini-2.5`, dll.).
   - Mendukung streaming Server-Sent Events (SSE) paragraf demi paragraf secara live.
3. **OpenAI-Compatible Endpoint**:
   - Terhubung ke OpenAI, OpenRouter, Groq, Ollama, LM Studio, vLLM, atau model mandiri lainnya.
   - Dilengkapi validasi keamanan URL dan pencegahan akses ke subnet privat.

Hasil terjemahan disimpan di database lokal Room (`TranslationEntity`) sehingga bab yang sudah diterjemahkan dapat dibuka kembali secara instan tanpa menggunakan kuota/request ulang.

---

## 📱 Cara Penggunaan

1. **Eksplorasi & Filter**: Buka tab **Explore** → pilih sumber di bagian atas → ketik kata kunci pencarian atau klik chip **Genre** ("Semua", "Fantasy", "Action", "Romance", dll.).
2. **Cloudflare**: Jika sumber meminta verifikasi Cloudflare, tekan tombol **Shield (CF)** → selesaikan captcha di WebView → tekan **Done**.
3. **Membaca & Terjemah**:
   - Buka novel dan pilih bab yang diinginkan.
   - Tekan ikon **Translate** di bilah atas untuk menerjemahkan bab (pilih Google MTL untuk terjemahan instan gratis).
   - Tekan ikon **TTS (Speaker)** untuk mendengarkan bacaan audio.
4. **Koleksi & Offline**:
   - Tekan ikon ❤️ untuk menyimpan novel ke tab **Library (Koleksi)**.
   - Tekan ikon ⬇️ untuk mengunduh seluruh bab ke penyimpanan offline.
   - Tekan **Impor EPUB** untuk menambahkan file buku lokal ke koleksi.

---

## 🏗️ Arsitektur (Kotatsu-style)

Single Gradle module (`:app`), **package-by-feature**, MVVM + Repository, dan manual Dependency Injection:

```
com.novelreader/
├── core/
│   ├── db/          # Room: novels, chapters, history, favourites, sources, translations
│   ├── parser/      # PagedNovelParser, WordPressNovelParser, SourcesRepository
│   ├── prefs/       # AppSettings (SharedPreferences ter-tipe)
│   ├── migration/   # Migrasi legacy JSON → Room
│   └── AppContainer # Manual DI container
├── source/          # Parser website (WoopRead, WtrLab, BacaLightNovel, SakuraNovel, etc.)
│   └── wp/          # WordPress/Themesia/Madara parser rules (WpParse, ChapterRules)
├── network/         # HttpClient (rate-limit + retry), SessionWebView, CoverLoader, Deduper
├── translate/       # AiTranslationApi (Gemini, OpenAI, Google MTL), StreamParser, Repository
├── work/            # DownloadWorker (background offline downloader)
├── data/            # DownloadStore (offline files), NovelCache (memory cache)
└── ui/
    ├── AppNav       # Navigasi tab: Library · History · Explore
    ├── explore/     # ExploreScreen, ExploreViewModel, ExplorePagination, SourceGrid
    ├── details/     # DetailsScreen, DetailsViewModel
    ├── reader/      # ReaderScreen, ReaderViewModel, ReaderTheme, ReaderTts
    └── lists/       # FeedScreen (Koleksi), HistoryScreen, DownloadsScreen, ListUi
```

---

## 🛠️ Cara Build & Install

### Prasyarat
- **JDK 17** (Eclipse Adoptium OpenJDK 17)
- **Android SDK** — `compileSdk 36`, `minSdk 26`
- **ADB** (opsional untuk install ke emulator/perangkat fisik)

### Menjalankan Verifikasi & Unit Test
Gunakan script verifikasi bawaan repo:

```bash
# Git Bash / Linux / macOS
bash scripts/verify.sh          # Compile + 87 unit tests + build debug APK
bash scripts/verify.sh --quick  # Compile + unit tests cepat
```

### Build Manual dengan Gradle
```bash
# Unit test
./gradlew testDebugUnitTest

# Build APK Debug
./gradlew assembleDebug

# Build APK Release (memerlukan keystore.properties)
./gradlew assembleRelease
```

File APK yang dihasilkan:
- **Debug**: `app/build/outputs/apk/debug/app-debug.apk`
- **Release**: `app/build/outputs/apk/release/app-release.apk`

---

## 📄 Lisensi & Ketentuan

- Proyek ini dikembangkan untuk tujuan edukasi dan penggunaan personal.
- Tidak berafiliasi dengan penyedia web novel manapun; konten diambil dari web publik — harap menghormati hak cipta dan ToS masing-masing situs.
- Didistribusikan di bawah [MIT License](LICENSE).
