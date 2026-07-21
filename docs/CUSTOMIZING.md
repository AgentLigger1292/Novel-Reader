# Customizing Novel Reader

Guide for forking / adapting this app: new sources, branding, package name, UI.

---

## 1. Add a new novel website source

Sources implement `NovelSource` and are registered in `NovelApp`.

### 1.1 Interface

```kotlin
// app/src/main/java/com/novelreader/source/NovelSource.kt
interface NovelSource {
    val id: String          // unique, e.g. "mysite"
    val name: String        // shown in UI
    val siteUrl: String?    // base URL for CF WebView; null if offline-only

    suspend fun getPopular(page: Int): List<Novel>
    suspend fun search(query: String, page: Int): List<Novel>
    suspend fun getNovel(path: String): NovelDetail   // meta + chapter list
    suspend fun getChapterContent(path: String): String  // HTML fragment of chapter body
}
```

### 1.2 Models

```kotlin
// model/Models.kt
data class Novel(
    val sourceId: String,
    val path: String,       // relative path preferred, e.g. "/series/foo/"
    val title: String,
    val coverUrl: String? = null,
    val author: String? = null,
    val description: String? = null,
)

data class Chapter(
    val path: String,
    val name: String,
    val number: Float? = null,
)

data class NovelDetail(
    val novel: Novel,
    val chapters: List<Chapter>,
)
```

### 1.3 Skeleton implementation

Create `app/src/main/java/com/novelreader/source/MySiteSource.kt`:

```kotlin
package com.novelreader.source

import com.novelreader.model.*
import com.novelreader.network.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

class MySiteSource(
    private val http: HttpClient,
) : NovelSource {
    override val id = "mysite"
    override val name = "My Site"
    override val siteUrl = "https://example.com"

    override suspend fun getPopular(page: Int): List<Novel> = withContext(Dispatchers.IO) {
        val url = if (page <= 1) "$siteUrl/" else "$siteUrl/page/$page/"
        val doc = http.getDocument(url)
        // TODO: parse list with Jsoup selectors → List<Novel>
        emptyList()
    }

    override suspend fun search(query: String, page: Int): List<Novel> = withContext(Dispatchers.IO) {
        // TODO: search URL + parse
        emptyList()
    }

    override suspend fun getNovel(path: String): NovelDetail = withContext(Dispatchers.IO) {
        val doc = http.getDocument(abs(path))
        // TODO: title, author, cover, chapters
        NovelDetail(
            Novel(id, path, "Untitled"),
            chapters = emptyList(),
        )
    }

    override suspend fun getChapterContent(path: String): String = withContext(Dispatchers.IO) {
        val doc = http.getDocument(abs(path))
        // TODO: select chapter body only; strip ads/scripts
        doc.selectFirst("div.entry-content")?.html() ?: "<p>Empty</p>"
    }

    private fun abs(path: String) =
        if (path.startsWith("http")) path else siteUrl!!.trimEnd('/') + path
}
```

### 1.4 Register the source

Edit `NovelApp.kt`:

```kotlin
val sources: Map<String, NovelSource> by lazy {
    val dummy = DummySource()
    val baca = BacaLightNovelSource(http)
    val mine = MySiteSource(http)
    mapOf(
        dummy.id to dummy,
        baca.id to baca,
        mine.id to mine,
    )
}

// optional default
var selectedSourceId: String = "mysite"
```

### 1.5 Tips for parsers

| Topic | Advice |
|-------|--------|
| HTML fetch | Prefer `http.getDocument(url)` — uses SessionWebView after CF, falls back to OkHttp |
| Selectors | Inspect site in browser DevTools; sites change often |
| Chapter list | Many Madara sites need AJAX (`manga_get_chapters`) — see `BacaLightNovelSource` |
| Paths | Keep relative paths consistent; `parseNovel` path must match list item path |
| Covers | Prefer absolute `https://…` URLs; lazy attrs: `data-src`, `srcset` |
| CF | Set `siteUrl`; user clears challenge once per session |

Reference implementation: `source/BacaLightNovelSource.kt`.

### 1.6 Offline-only source

Set `siteUrl = null` and return static or local data (like `DummySource`). No CF button for that source.

---

## 2. Rebrand the app

### App name

`app/src/main/res/values/strings.xml`:

```xml
<string name="app_name">Your Reader Name</string>
```

### Launcher icon

| Asset | Path |
|-------|------|
| Adaptive foreground | `res/drawable/ic_launcher_foreground.xml` |
| Adaptive / mono | `res/mipmap-anydpi-v26/ic_launcher.xml` |
| PNG densities | `res/mipmap-mdpi` … `mipmap-xxxhdpi` (`ic_launcher.png`, `ic_launcher_round.png`) |
| Background color | `res/values/colors.xml` → `ic_launcher_background` |

Manifest already references `@mipmap/ic_launcher` and `@mipmap/ic_launcher_round`.

### Application ID (package)

`app/build.gradle.kts`:

```kotlin
android {
    namespace = "com.yourname.reader"
    defaultConfig {
        applicationId = "com.yourname.reader"
        // ...
    }
}
```

Then rename Kotlin package folders from `com/novelreader` → `com/yourname/reader` and update imports (Android Studio: **Refactor → Rename** is easiest).

---

## 3. Reader UI

| What | Where |
|------|--------|
| Themes (Dark/Sepia/Light) | `ui/ReaderTheme.kt` |
| Reader screen (font, progress, settings sheet) | `ui/Screens.kt` → `ReaderScreen` |
| HTML → paragraphs | `ui/HtmlText.kt` → `htmlToParagraphs` |
| Novel grid cards | `ui/Screens.kt` → `NovelGridCard` |
| Navigation tabs | `ui/AppNav.kt` |

Reader settings are in-memory per session (font size, line height, theme). To persist them, store values in `SharedPreferences` or extend `AppStore`.

---

## 4. Network & Cloudflare

| Class | Role |
|-------|------|
| `SessionWebView` | One long-lived WebView after user clears CF; serial HTML fetch |
| `HttpClient` | `getHtml` / `getDocument` / `postForm`; CF detection |
| `CfWebViewScreen` | UI for manual CF; **Done** calls `SessionWebView.adopt` |
| `CoverLoader` | Coil + OkHttp with WebView cookies; disk cache under `cache/covers` |

**Do not** add automated captcha/CF solvers. Manual WebView only.

If covers fail after CF:

```bash
adb shell run-as com.your.package rm -rf cache/covers
adb logcat -s BLN
```

---

## 5. Offline downloads

| Class | Role |
|-------|------|
| `DownloadStore` | `files/downloads/` — `meta.json` + per-chapter HTML |
| Detail screen | Download icon → `downloadAll` |
| Tab **Offline** | List entries; open novel; delete |
| `ReaderScreen` | Prefers `readChapterHtml` when file exists |

To change storage layout, edit `data/DownloadStore.kt` only.

---

## 6. Dependencies

`app/build.gradle.kts` (main ones):

- Jetpack Compose + Material3  
- Navigation Compose  
- OkHttp, Jsoup  
- Coil (`coil-compose`) for covers  

Add libraries there; keep `minSdk` / `compileSdk` in sync with your needs.

---

## 7. Build & release checklist

- [ ] `local.properties` not committed (`sdk.dir=…`)  
- [ ] Update `versionCode` / `versionName` in `app/build.gradle.kts`  
- [ ] Test: Dummy source + your source after CF  
- [ ] Test: download → airplane mode → open Offline → read chapter  
- [ ] Sign release: create keystore and `signingConfigs` (do not commit keystore passwords)

Debug APK:

```bash
./gradlew assembleDebug
```

Release (after signing config):

```bash
./gradlew assembleRelease
```

---

## 8. Common issues

| Symptom | What to try |
|---------|-------------|
| Empty novel list | CF not cleared; open CF → Done; check `adb logcat -s BLN` |
| Covers = book icon | Clear `cache/covers`; ensure CF Done so cookies exist |
| Search returns homepage list | Ensure search URL includes `?s=…` and WebView navigates (see `SessionWebView.sameUrl`) |
| Chapter list messy | Filter PDF/download links; sort by `Chapter.number` (see `BacaLightNovelSource`) |
| Build: SDK not found | Create `local.properties` with `sdk.dir=` |

---

## 9. Contributing / fork etiquette

- Document any new source (site name, selectors, known CF issues).  
- Do not hardcode secrets or automated anti-bot exploits.  
- Keep `DummySource` for offline UI testing without a network.
