# Contributing

## Setup

See [README.md](README.md) for JDK/SDK and `./gradlew assembleDebug`.

## Before a PR

1. Run unit tests: `./gradlew test`
2. Smoke-test Dummy source (no network).
3. If you change a live source, note HTML selector changes in the PR description.
4. Do not add automated Cloudflare/captcha solvers.

## Code style

- Prefer small, focused changes (Kotlin + Compose).
- Match existing package layout under `com.novelreader`.
- New sources: one class file + registration in `NovelApp`.

## Docs

Update these when behavior changes:

- `README.md` — user-facing features / build
- `docs/CUSTOMIZING.md` — how to fork/add sources
- `docs/ARCHITECTURE.md` — data flow
