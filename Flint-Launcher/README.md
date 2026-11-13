# Flint Launcher

An open-source Minecraft: Java Edition launcher for Android (ARM64 first).
Author: KingBrezz. Package: `com.flintlauncher.android`.

> **Status: early scaffold.** The project structure, build files, CI workflows and design documents exist. **The launcher does not run Minecraft yet.** The scaffold has not been compiled and the workflows have not been run. Nothing below the "Implemented" list should be assumed to work.

Flint is an unofficial project and is not affiliated with Mojang or Microsoft.

## Status

"Written" means source code and unit tests exist. **Nothing has been compiled, no test has been run, and nothing has run on a device.** See [docs/AUDIT.md](docs/AUDIT.md) for the full, honest picture.

| Area | State |
|---|---|
| Research and architecture | Done: [docs/RESEARCH.md](docs/RESEARCH.md), [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) |
| Gradle multi-module project (17 modules + app) and GitHub Actions (CI, APK, release) | Written. YAML syntax checked only, workflows never run |
| Home screen, navigation (PLAY / PROFILES / MODS / PARTNERS / SETTINGS, no Store), theme | Written. Real state only, empty states and disabled controls instead of fake data |
| Server list: 5 default public servers, live status via mcstatus.io, Copy IP, Visit | Written with tests |
| **Minecraft download and install**: version catalog (Release/Snapshot/Beta, search, sort), manifest and version-JSON handling with SHA-1 checks, client jar, libraries, assets, log4j config, parallel resumable verified downloads, pause/resume/cancel/retry, storage check, foreground service and notification | Written with tests, including an end-to-end test against a fake Mojang server. Lives under the PROFILES tab ("Installations") for now |
| Launch argument building (rules, placeholders, legacy and modern formats) | Written with tests, not connected to a runtime |
| Microsoft login chain (PKCE, Xbox, XSTS, Minecraft, entitlement, profile) | Client written with tests. No sign-in screen, no secure token storage, no Azure app yet |
| Profiles (validation, isolated folders, clone, import/export with unsafe-argument stripping) | Domain and storage written with tests. No profiles screen |
| Fabric/Quilt metadata and compatibility check | Written with tests. Not exposed in the UI |
| Forge, NeoForge, mods, modpacks, news, friends, skins, settings | **Not started** |
| Offline accounts (UUID convention, validation, persistence, manager) | **Implemented** with unit tests. UI screen not fully wired |
| Microsoft auth client (PKCE chain) | Written with tests. No Keystore token store, no sign-in UI, no live Azure app |
| Launch readiness resolver (explicit states, runtime/renderer/account gates) | **Implemented** (pure logic) |
| Real-time install UI + stages + READY TO PLAY gate | **Implemented** in Compose (driven by real DownloadManager) |
| Touch control models / editor / overlay | **Implemented** in Compose. Input bridge to game process **not implemented** |
| Java runtime and rendering on Android (so the game can actually start) | **Interfaces only** (`JavaRuntimeManager`, `GameLauncher`, `UnimplementedGameLauncher`). No ARM64 JRE, no JNI, no renderer libs. **BLOCKED** on Spike 0 |

## Build

Requirements: JDK 17, Android SDK (platform 35), Gradle 8.9.

```bash
gradle wrapper --gradle-version 8.9   # one time: creates gradlew and the wrapper jar, then commit them
./gradlew :app:assembleDebug
./gradlew test
```

The APK is written to `app/build/outputs/apk/debug/`.

## Build with GitHub Actions

1. Push this repository to GitHub (branch `main`).
2. Open **Actions → Build APK → Run workflow**.
3. When it finishes, download **flint-launcher-debug-apk** from the run's artifacts.

The workflows pin Gradle 8.9 themselves, so they do not need the wrapper jar. CI (`android-ci.yml`) compiles, runs unit tests and Android Lint on every push and pull request.

### Signed releases

Add these repository secrets: `FLINT_KEYSTORE_BASE64` (base64 of your `.jks`), `FLINT_KEYSTORE_PASSWORD`, `FLINT_KEY_ALIAS`, `FLINT_KEY_PASSWORD`. Then push a tag such as `v0.1.0`; the release workflow builds a signed APK, writes `SHA256SUMS.txt` and publishes a GitHub Release. Without the secrets, releases fail on purpose. Never commit keystores or passwords.

## Known limitations

- Minecraft can be downloaded and installed (once the build is verified) but **cannot be launched yet**: there is no Java runtime or renderer.
- The server list uses a third-party status API (mcstatus.io): it sees the device IP, and it can be rate-limited or down, in which case servers show "Status unknown".
- The home screen has no skin renderer, news images or real data sources yet; every action is disabled until its module exists.
- Toolchain versions are an older, conservative set (see architecture §9).
- Real Microsoft login needs an Azure app registration approved by Mojang; CurseForge needs an API key; friends needs a backend.
- No `LICENSE` file yet: the licence depends on which third-party runtime components are bundled (architecture §12).

## Contributing and security

See [CONTRIBUTING.md](CONTRIBUTING.md) and [SECURITY.md](SECURITY.md).
