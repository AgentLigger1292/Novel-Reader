# Architecture

High-level design of Novel Reader — restructured to follow the Kotatsu-Redo app flow, adapted for text novels (v0.2.0).

---

## Layers

```
┌────────────────────────────────────────────────────┐
│  UI (Jetpack Compose, package-by-feature)          │
│  ui/  AppNav (bottom nav: Feed·History·Favourite·  │
│       Explore)                                     │
│  ├─ explore/    ExploreScreen + ExploreViewModel   │
│  ├─ details/    DetailsScreen + DetailsViewModel   │
│  ├─ reader/     ReaderScreen + ReaderViewModel     │
│  └─ lists/      History / Favourites / Feed /      │
│                 Downloads screens + VMs            │
└─────────────────┬──────────────────────────────────┘
                  │ (manual DI — AppContainer)
┌─────────────────▼──────────────────────────────────┐
│  core/                                             │
│  ├─ parser/SourcesRepository  source facade +      │
│  │                             Room chapter cache  │
│  ├─ HistoryRepository    addOrUpdate(chapter,      │
│  │                       scroll, percent)          │
│  ├─ FavouritesRepository categories + join queries │
│  ├─ TrackerRepository    feed state (TrackEntity)  │
│  ├─ migration/           legacy JSON → Room        │
│  └─ prefs/AppSettings    typed SharedPreferences   │
└─────────────────┬──────────────────────────────────┘
                  │
┌─────────────────▼──────────────────────────────────┐
│  core/db (Room)                                    │
│  novels · chapters · history · favourite_categories│
│  · favourites · sources · tracks                   │
└────────────────────────────────────────────────────┘
                  │
┌─────────────────▼──────────────────────────────────┐
│  source/ (parsers — UNCHANGED from v0.1.x)         │
│  WordPressSource base + BacaLightNovel /           │
│  SakuraNovel / MistmintHaven                       │
└────────────────────────────────────────────────────┘
                  │
┌─────────────────▼──────────────────────────────────┐
│  work/ (WorkManager)                               │
│  DownloadWorker  per-novel unique work,            │
│                  notification + progress           │
│  TrackWorker     periodic new-chapter check for    │
│                  favourites → Feed                 │
└────────────────────────────────────────────────────┘
```

## Data flow (Kotatsu equivalents)

- **Explore** → `SourcesRepository` → `NovelSource.getPopular/search(page)` with load-more pagination (page state lives in `ExploreViewModel`, no longer lost on rotation).
- **Open novel** → `getNovelWithCache()` fetches details, upserts `NovelEntity` + replaces `ChapterEntity` cache — chapter lists stay available offline, exactly like Kotatsu's `MangaDataRepository`.
- **Read** → `HistoryRepository.addOrUpdate(novelId, chapterId, scroll, percent)` (Kotatsu's exact column set); the reader debounces saves while scrolling and restores position on reopen.
- **Favourite** → `FavouritesRepository` with a default "Umum" category.
- **Download** → `DownloadWorker` (unique per novel) — runs even if the user leaves the screen; progress surfaces via `DownloadStore.liveProgress` Flow.
- **Feed** → `TrackWorker` (periodic, network-constrained) diffs fresh chapter counts against the Room cache and updates `TrackEntity`; opening a novel clears its badge.

## Migration

On first launch of v0.2.0, `library.json` and `history.json` are imported into Room (default category "Umum"), then renamed to `*.migrated`. The offline download folder format (`downloads/<sha1>/…`) is unchanged, so existing downloads keep working.
