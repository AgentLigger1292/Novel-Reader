# Novel Reader

Native Android light-novel reader (Kotlin + Jetpack Compose). Browse online sources, read offline after download, library/history, and a manual Cloudflare (CF) pass-through via WebView.

> **Not affiliated** with any novel website. Content is fetched from the public web; respect site ToS and copyright. This app does **not** auto-bypass captchas — the user clears CF challenges manually in an in-app WebView.

---

## Features

| Feature | Description |
|--------|-------------|
| **Browse / Search** | Grid of novel covers + titles |
| **Sources** | Pluggable `NovelSource` (Dummy + Baca Light Novel example) |
| **Reader** | Native text (serif, justify), themes Dark / Sepia / Light, font & line spacing |
| **Library** | Favorites (JSON on disk) |
| **History** | Last chapters read |
| **Offline** | Download all chapters of a novel; read without network |
| **CF (manual)** | Shield / CF button → WebView → user completes challenge → **Done** → session reused |
| **Covers** | Coil + OkHttp with WebView cookies / disk cache |

---

## Requirements

- **JDK 17**
- **Android SDK** (API 34 recommended; `minSdk 26`)
- **Gradle** wrapper included (`gradlew` / `gradlew.bat`)
- Device or emulator for install (`adb`)

Optional: Android Studio (project opens as a normal Gradle Android app).

---

## Quick start (CLI)

### Windows (PowerShell)

```powershell
cd novel-reader

# Point to your JDK 17 and Android SDK
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.19.10-hotspot"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:Path = "$env:JAVA_HOME\bin;$env:ANDROID_HOME\platform-tools;$env:Path"

# Create local.properties if missing
@"
sdk.dir=$($env:ANDROID_HOME -replace '\\','\\')
"@ | Set-Content -Encoding ascii local.properties

.\gradlew.bat assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### Linux / macOS

```bash
cd novel-reader
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=$HOME/Android/Sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties

./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

**APK output:** `app/build/outputs/apk/debug/app-debug.apk`

**Tests:**

```bash
./gradlew test
```

---

## First run (online sources)

1. Open the app → source **Baca Light Novel** (or your custom source).
2. If the site is behind Cloudflare:
   - Tap **CF** / shield.
   - Wait until the **real site title** appears (not “Tunggu sebentar…” / “Just a moment…”).
   - Tap **Done**.
3. Browse / search; open a novel → chapters → read.
4. Optional: on novel detail, tap **Download** for offline reading (tab **Offline**).

---

## Project structure

```
novel-reader/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/novelreader/
│       │   ├── NovelApp.kt              # DI-lite: sources, store, downloads
│       │   ├── MainActivity.kt
│       │   ├── model/                   # Novel, Chapter, NovelDetail
│       │   ├── source/                  # NovelSource + implementations
│       │   ├── network/                 # HttpClient, SessionWebView, CoverLoader
│       │   ├── data/                    # AppStore, DownloadStore (JSON + files)
│       │   └── ui/                      # Compose screens, reader theme
│       └── res/                         # strings, themes, launcher icons
├── docs/
│   ├── CUSTOMIZING.md                   # Add sources, rebrand, change UI
│   └── ARCHITECTURE.md                  # Data flow & CF session model
├── gradle/wrapper/
├── README.md
└── LICENSE
```

---

## Documentation for customizers

| Doc | Audience |
|-----|----------|
| **[docs/CUSTOMIZING.md](docs/CUSTOMIZING.md)** | Add a new website source, rename app, change package/icons, tweak reader UI |
| **[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md)** | How browse/search/download/CF/covers work |

---

## Debug (adb)

```bash
# App logs (tag BLN)
adb logcat -s BLN

# Clear corrupted cover cache (if covers show book icon only)
adb shell run-as com.novelreader rm -rf cache/covers
```

Useful log lines:

- `covers parsed N/N` — HTML parse found cover URLs  
- `cover ok …` / `coil success` — image download OK  
- `cover HTTP 403` — CF cookie missing; complete CF → Done again  
- `SessionWebView adopted` — CF WebView session ready  

---

## Disclaimer

- This project is a **personal / educational** reader shell with example parsers.
- Site HTML and anti-bot rules change often; sources may break and need selector updates.
- Do **not** use this to redistribute copyrighted content.
- Cloudflare: **manual** user interaction only. No automated captcha/CF solvers.

---

## License

MIT — see [LICENSE](LICENSE).
