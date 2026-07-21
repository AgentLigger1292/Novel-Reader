# Architecture

High-level design of Novel Reader for contributors and customizers.

---

## Layers

```
┌─────────────────────────────────────────┐
│  UI (Jetpack Compose)                   │
│  AppNav, Browse, Library, Offline,      │
│  History, NovelDetail, Reader, CF WebView│
└─────────────────┬───────────────────────┘
                  │
┌─────────────────▼───────────────────────┐
│  NovelApp                               │
│  sources map · store · downloads · http │
└─────────────────┬───────────────────────┘
                  │
     ┌────────────┼────────────┐
     ▼            ▼            ▼
 NovelSource   AppStore    DownloadStore
 (per site)    library/    offline HTML
               history
     │
     ▼
 HttpClient ──► SessionWebView (after CF)
            └──► OkHttp fallback
 CoverLoader ──► OkHttp + CookieManager
```

---

## Data flow

### Browse / Search

1. User selects source in `BrowseScreen`.
2. `source.getPopular(page)` or `source.search(query, page)`.
3. Source calls `HttpClient.getDocument(url)`:
   - Prefer **SessionWebView** HTML (same engine/cookies as CF screen).
   - On failure / no session → OkHttp (often 403 on CF sites).
4. Jsoup parses HTML → `List<Novel>` (title, path, coverUrl).
5. UI shows **grid** (`NovelGridCard`).
6. `CoverLoader.prefetch` downloads covers into `cache/covers` (with CF cookies).

### Novel detail

1. `source.getNovel(path)` → title, author, description, chapter list.
2. Optional: load from `DownloadStore.readNovelDetail` if offline preferred or online fails.
3. User can **Download** → `DownloadStore.downloadAll` (sequential `getChapterContent`).

### Reader

1. Prefer `DownloadStore.readChapterHtml` if present.
2. Else `source.getChapterContent(path)` → HTML fragment.
3. `htmlToParagraphs` → plain paragraphs.
4. Compose `LazyColumn` + `ReaderTheme` (not a browser for reading).

### Manual Cloudflare session

```
User taps CF
    → CfWebViewScreen loads siteUrl (JS enabled)
    → User completes challenge until real page
    → Done → CookieManager.flush()
           → SessionWebView.adopt(webView)
           → cookieGeneration++
    → Browse/search re-runs with shared WebView session
```

- **Not** an automatic CF/captcha solver.
- New headless WebViews often re-trigger “Tunggu sebentar…”; reuse **one** adopted WebView.

---

## Persistence

| Data | Location |
|------|----------|
| Library / history | `files/library.json`, `files/history.json` |
| Offline novels | `files/downloads/<sha1>/meta.json` + `chapters/*.html` |
| Cover disk cache | `cache/covers/<sha1>.(png\|jpg\|…)` |

No Room/KSP — JSON + files for simple CLI builds.

---

## Logging

Tag: **`BLN`**

```
adb logcat -s BLN
```

Typical events: popular/search tries, cover parse sample, CF Done, download progress, cover HTTP codes.

---

## Extending safely

1. New site → new `NovelSource` class + register in `NovelApp.sources`.
2. Avoid blocking main thread: use `Dispatchers.IO` inside sources.
3. Serialize heavy use of `SessionWebView` (shared lock) — don’t fire parallel `getHtml` without queue.
4. Cover downloads use OkHttp + cookies; validate image magic bytes so HTML error pages are not shown as covers.
