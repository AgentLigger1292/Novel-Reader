# AGENTS.md — working rules for AI agents (and humans) in this repo

Single-module Android app: **Novel Reader** (`:app`), Jetpack Compose UI, min SDK 26 / compile SDK 36 / target 34, Kotlin + JVM target 17.

> Deep docs: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) (layers & data flow), [docs/CUSTOMIZING.md](docs/CUSTOMIZING.md) (forking guide — note: it predates the `AppContainer` refactor, trust this file for registration details).

## Map (30 seconds)

| Area | Path | Notes |
|---|---|---|
| Sources (site parsers) | `app/src/main/java/com/novelreader/source/` | one class per site; base classes `PagedNovelParser`, `WordPressNovelParser`; shared WordPress rules in `source/wp/` |
| Source registration | `core/SourceRegistry.kt` → `defaultEntries` | declarative list — adding a site = one entry; **no plugin system** |
| Repositories / caches | `core/` | `SourcesRepository` (Room chapter cache), `Favourites`, `History`, `LocalEpub` |
| Database | `core/db/` | Room entities/DAOs; legacy JSON migration in `core/migration/` |
| Network | `network/` | `HttpClient` + `RateLimiter`; Cloudflare-protected sites go through `SessionWebView`/`WebViewFetcher` |
| AI translation | `translate/` | Gemini + any OpenAI-compatible endpoint, SSE streaming — don't reinvent |
| UI | `ui/` | package-by-feature screens (`explore/`, `details/`, `reader/`, `lists/`), navigation in `AppNav.kt` |
| Background work | `work/` | WorkManager workers (`DownloadWorker`) |
| Tests | `app/src/test/java/com/novelreader/` | JVM JUnit4 only, **no instrumented tests**; HTML/JSON fixtures in `app/src/test/resources/fixtures/` |

DI is manual: `AppContainer` + `AppVmFactory`. There is no Hilt/Dagger.

## Verify contract (how "done" is defined)

Before claiming any code change is done, run the verify script and report its final STATUS block verbatim:

```bash
bash scripts/verify.sh          # unit tests + debug APK
bash scripts/verify.sh --quick  # compile + unit tests only (fast inner loop)
```

Exit codes: `0` pass · `1` test failure · `2` compile failure. Non-zero means **not done** — read the `NEXT:` line it prints (points at the failing test class / report path), fix, rerun.

## Recipes

### Add or fix a novel source
1. Write/extend the parser class (extend `WordPressNovelParser` for WP sites, else `PagedNovelParser`); keep scraping logic in the class, use `source/wp/` rules for shared behavior.
2. Save a real page snapshot as a fixture: `app/src/test/resources/fixtures/<site>_<page>.html|json`.
3. Add a JUnit test beside the existing ones (pattern: `WpParseFixtureTest.kt`, `SakuraChapterNumberTest.kt`) — parse the fixture, assert titles/chapters/content.
4. Add the source to `core/SourceRegistry.kt` `defaultEntries` (one entry; set `catalog = false` only for offline/infrastructure sources).
5. `bash scripts/verify.sh` → green before done.
6. If the site is Cloudflare-protected, wire fetching through `network/SessionWebView` — plain OkHttp will get blocked.

### Change UI / core behavior
- Compose + MVVM: screen + ViewModel live in the same feature package under `ui/`.
- Persisted state changes (Room schema) need a migration in `core/db/` + bump the DB version; test with the `LegacyMigrationTest.kt` style.
- Never block the main thread: parsers and repos are `suspend`/Flow-based.

### Release
- Version lives in `app/build.gradle.kts` (`versionName`/`versionCode`).
- Release signing reads gitignored `keystore.properties` (never commit it; `*.keystore`, `*.jks`, `keystore.properties` are already in `.gitignore`).

## Conventions

- Commits: conventional style matching history — `feat:`, `fix(scope):`, `build:`, `chore:`.
- Code comments in English; keep them for constraints only.
- Don't commit build artifacts (`.gitignore` already covers `*.apk`, `/build`); don't "clean up" the committed APKs at repo root without asking.
- Prefer editing existing parsers/tests over new abstractions; this codebase is deliberately small.

## Known pitfalls

- `docs/CUSTOMIZING.md` §1.2 says sources register in `NovelApp` — outdated; it's `core/AppContainer.kt`.
- CI (`.github/workflows/android.yml`) runs `testDebugUnitTest` + `assembleDebug`; it is the same gate as `scripts/verify.sh`.
- Parser tests must not hit the network — fixtures only. A test that flakes on network is a bug.
- Windows dev boxes: use `bash scripts/verify.sh` from Git Bash, or `powershell -File scripts/verify.ps1`.
