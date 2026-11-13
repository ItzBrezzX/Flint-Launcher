# Flint Launcher: architecture (Stage 2)

Status: design document. Statements about what works refer only to what is listed in the README status table. Research and sources: [RESEARCH.md](RESEARCH.md).

## 1. Principles

1. Honesty over polish. No fake progress, fake presence, fake server stats or fake login success. Anything not built shows an explicit "unavailable" state.
2. The runtime is the riskiest part. It is de-risked first (see §3, Spike 0) before more UI is built on top of it.
3. Dependencies point inward. Pure Kotlin/JVM modules hold the logic and run in fast unit tests; Android modules are thin.
4. Never bypass Microsoft authentication, Minecraft ownership checks, or provider download restrictions.
5. Secrets never enter the repo or the APK.

## 2. Module map

```
app ──► ui, account, profiles, installer, runtime, mods, modpacks, news, friends, ...
account ─► auth, database          runtime ─► native, minecraft
installer ─► minecraft, download   modpacks ─► mods, installer, download
mods ─► network, minecraft, profiles, download
download, auth, news, friends ─► network ─► core
minecraft, profiles ─► core        database ─► core
```

| Module | Kind | Responsibility |
|---|---|---|
| `core` | JVM | Result/error types, shared models, dispatchers |
| `network` | JVM | OkHttp, User-Agent, cache, conditional requests |
| `minecraft` | JVM | Manifest and version JSON models, library rules, Java requirement |
| `download` | JVM | Persistent resumable verified download engine |
| `installer` | JVM | Install planning and execution, loader compatibility validation |
| `auth` | JVM | OAuth (PKCE), Xbox, XSTS, Minecraft Services |
| `profiles` | JVM | Isolated profiles, import/export |
| `mods`, `modpacks` | JVM | Modrinth/CurseForge, dependency resolution, `.mrpack` |
| `news`, `friends` | JVM | Feed client; friends client (backend optional) |
| `partners` | JVM | Server list (bundled 5 + optional remote feed) and live status lookups |
| `database` | Android | Room: profiles, accounts metadata, download queue, cache |
| `account` | Android | Keystore-backed token storage, account switching, skins |
| `runtime` | Android | JRE management, renderer selection, game process |
| `native` | Android | JNI/C++ bridge (CMake added in Spike 0) |
| `ui` | Android | Design system (theme, components) |
| `app` | Android | Activities, navigation, dependency wiring, foreground services |

The package for `:native` is `com.flintlauncher.android.nativebridge` because `native` is a reserved Java keyword.

## 3. Runtime strategy (decision needed)

Options:

- **A. Fork Amethyst-Android** and rebuild the UI on top. Fastest to a running game, but inherits its architecture and its licence (LGPL-3.0).
- **B. Own launcher shell, reused engine components** (recommended). Flint implements accounts, installs, downloads, profiles and UI itself. It consumes Pojav-lineage pieces (Android JRE builds, LWJGL fork, renderers, native glue) as separable, attributed components. This is how Zalith Launcher 2 is reported to be structured.
- **C. From scratch.** Rejected: unrealistic scope and no benefit.

Design under B:

- The game runs in a dedicated Android process (`:game`). The JVM is loaded in-process via `dlopen(libjvm.so)` and `JNI_CreateJavaVM`, never via `exec`. When the game exits, the process exits.
- Installers (Forge/NeoForge) run the same way in a short-lived runtime process.
- JREs for Java 8, 17, 21 and 25 are downloaded on demand with hash checks, to keep the APK small. The launcher selects the JRE from `javaVersion.majorVersion`.
- Renderers sit behind one interface (`Renderer`), selectable per profile (for example GL4ES, MobileGlues, LTW, Zink). Device capability checks decide what is offered.

**Spike 0 (blocks everything else on the runtime):** on a physical ARM64 device, launch vanilla 1.21.11 and the latest 26.x to the main menu from a minimal harness, with exact device model, Android version and renderer recorded. No compatibility claim is made until this passes.

## 4. Launch pipeline

1. Validate profile (`profiles`).
2. Resolve version JSON and required Java (`minecraft`).
3. Verify game files, libraries and assets by hash; queue anything missing (`download`).
4. Ensure the JRE is present and verified (`runtime`).
5. Refresh and validate the account token (`account`, `auth`).
6. Build JVM and game arguments from version JSON, profile and settings (`minecraft`, `profiles`).
7. Start the `:game` process with the chosen renderer (`runtime`, `native`).
8. Capture logs and exit codes; show a meaningful error on failure (`runtime`, `app`).

## 5. Download manager

- Queue and per-file state live in Room, so a restart resumes where it stopped.
- Per file: HTTP `Range` resume when the server supports it, SHA-1 (Mojang) or SHA-512/SHA-1 (Modrinth) verification, skip files that already verify, exponential backoff with jitter.
- Configurable concurrency; speed uses a sliding window (current) and total-bytes/elapsed (average). ETA comes from those, never from a timer.
- Total size comes from manifest data. If a size is unknown it is shown as unknown, not guessed.
- Storage check (`StatFs`) before a plan starts.
- Runs in a foreground service with type `dataSync` and a notification. Android 13+ needs the notification permission.

## 6. Accounts and authentication

- Microsoft: authorization-code flow with PKCE, custom-scheme redirect, then the chain in RESEARCH.md §3. Requires an Azure app registration approved by Mojang (§12).
- Tokens: refresh token encrypted with an Android Keystore AES-GCM key and stored in app-private storage. Access tokens are kept in memory only. Passwords are never seen.
- Offline accounts are labelled **Offline** everywhere. UUID follows the standard offline convention (name-based UUID from `"OfflinePlayer:" + name`). They are never presented as authenticated.
- Skins: official services for Microsoft accounts. Offline skins are local previews only and are labelled as such.

## 7. Mods and modpacks

- Modrinth is the default source. CurseForge is opt-in and needs the user's own API key (or an operator-hosted proxy). No shared key ships in the APK.
- Compatibility = Minecraft version AND loader AND declared dependencies; incompatible files are not installable. Conflicts are flagged when declared incompatibilities are known.
- Each profile has its own `mods/`, `config/`, `resourcepacks/`, `shaderpacks/`. Updates replace the JAR only and leave `config/` untouched.
- Files with no permitted download URL are shown as "get from provider", never fetched by other means.

## 8. Friends, partners, news, updates

- **Friends** need a server. Until one is configured the screen says so. A documented optional backend (account-linked, rate-limited, TLS only) is a later design doc, not a promise.
- **Servers (`:partners`):** the default list is five public servers taken from another launcher's list (Hypixel, Mineplex, MCC Island, DonutSMP, Hoplite). They are **not** Flint partners and the UI says so. An optional remote JSON feed (https only, validated, deduplicated, capped at 5) can replace the list; any failure falls back to the bundled list. Status comes from the mcstatus.io v2 API (`online`, `players.online/max`); lookups run two at a time (the API allows about 5 requests/s per IP) and are cached for 60 s. A failed or rate-limited lookup shows "Status unknown", never Online or Offline, and player counts are shown only when the API returned them. A direct Server List Ping client (no third party) is a later option.
- **News:** configurable JSON/RSS source; candidates (Mojang launcher content, Minecraft feed, project GitHub releases) are verified in Stage 12.
- **Updates:** conditional requests (ETag / Last-Modified), per-source refresh intervals, manual refresh. Launcher updates come from GitHub Releases; the APK's SHA-256 is checked against the release `SHA256SUMS.txt`, and Android enforces the same signing key on install.

## 9. Toolchain and the upgrade plan

The scaffold pins AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21, compileSdk 35 and Compose BOM 2024.10.01 so that it resolves together without being compiled. The current stack (AGP 9, Kotlin 2.4, compileSdk 37, Compose BOM 2026.09) is a Stage 3 task for an environment that can run Gradle. AGP 9 changes Kotlin plugin setup, so it is done as one reviewed change, not piecemeal. `gradle-wrapper.jar` is not committed (see README); CI uses a pinned Gradle instead.

## 10. Security and privacy

- Tokens encrypted at rest; no secrets in logs; log export redacts tokens and UUID-adjacent headers.
- TLS only (`usesCleartextTraffic=false`); no custom trust managers.
- Release APKs are signed in CI from GitHub Secrets; releases without signing secrets fail.
- Backups disabled (`allowBackup=false`) so tokens are not copied off-device.
- Dependabot for Gradle and Actions. Dependency verification metadata is added in Stage 3.

## 11. Testing

- JVM unit tests per module (JUnit; MockWebServer for network, interruption and resume tests).
- Android instrumented tests for Room and Keystore.
- Device tests (`tests/README.md`): real launches on physical ARM64 devices, recorded per device and renderer. A feature is "supported" only with a recorded passing run.

## 12. Licensing and external prerequisites

Decisions and inputs only the project owner can provide:

1. **Flint's licence.** If Pojav-derived LGPL code is bundled, LGPL-3.0 for Flint is the simplest compatible choice (GPL-3.0 if Zalith 2 code is used). A legal read of the exact combination is advised; nothing here is legal advice. No `LICENSE` file is committed yet.
2. **Azure app registration** plus Mojang's app-ID approval, before real Microsoft login can work.
3. **CurseForge API key** (optional feature).
4. **Release keystore** and the four `FLINT_*` GitHub Secrets.
5. **A friends backend**, if friends is to be real.
6. **Branding:** Flint is unofficial and not affiliated with Mojang or Microsoft. Follow Mojang's brand and EULA guidelines; Flint uses its own name, icon and assets.

## 13. Stage status

| Stage | State |
|---|---|
| 1 Research | Done (secondary sources, see tags) |
| 2 Architecture | Done (this document) |
| 3 Foundation | Scaffold written, **never compiled**, CI never run |
| 4 UI | Home, navigation and Installations screens written (never compiled); other screens are explicit "not implemented" screens |
| 5 Accounts | Auth chain client + tests written; no UI, token storage or Azure app |
| 6 Version manager | Written with tests |
| 7 Runtime and install engine | Install engine written with tests; **runtime not started** |
| 8 Download manager | Written with tests (pause/cancel only covered at coordinator level) |
| 9 Loaders | Fabric/Quilt metadata only; Forge/NeoForge need the runtime |
| 10 to 12 | Mods, modpacks, news, friends, settings not started; profiles domain written |
| 13 Tests | 149 unit tests written, none run |
| 14 CI/APK | Workflows written, never run |
| 15 Audit | [AUDIT.md](AUDIT.md) |

## 14. Home screen: desktop reference to Android layout

| Desktop reference element | Flint on Android |
|---|---|
| Left sidebar (Play, Profiles, Partners, Store, Settings) | Bottom bar on phones, rail on tablets and landscape. Items: PLAY, PROFILES, MODS, PARTNERS, SETTINGS. **No Store**; MODS replaces it |
| Centre player showcase | Showcase card: username tag, player area, version / loader / profile chips. Skin render comes from the skin manager (not built) |
| LAUNCH GAME button with installation line and dropdown | Same, orange, dark text for contrast. Disabled with "No installation selected" when there is none |
| Three "Add a Profile" slots | Three slots (select / edit / delete with confirmation), "+N more in Profiles" beyond three |
| News cards | Horizontal card row with loading, error, retry and empty states. No images until an image loader is added |
| Friends panel | States "unavailable" until a backend exists. No fake presence |
| Partnered servers with player counts | "Servers" section: five cards with Copy IP (clipboard), Visit (https only) and a refresh button. Status and player counts come only from a real lookup; otherwise "Status unknown". No logos are bundled |
| Third-party promo cards (chat, webstore) | Not included |
| Window controls, account dropdown | Account chip showing OFFLINE or MICROSOFT; no window controls on Android |

Layout breakpoints: under 600dp bottom bar; 600dp and up rail; 840dp and up two columns (main + partners). Android Studio previews for phone, empty and tablet states are in `HomePreview.kt` (sample data lives only there).

## 15. Minecraft install pipeline

Modelled on how the official launcher and other launchers install a version (see RESEARCH.md §10):

1. Fetch `version_manifest_v2.json`, find the version, fetch its JSON and verify the SHA-1 from the manifest. Store it as `versions/<id>/<id>.json`. A loader profile with `inheritsFrom` is merged onto its vanilla parent.
2. Download the asset index first (it decides the asset list), then everything else in one verified batch: client jar, libraries (rules applied; Maven-style loader libraries supported), the log4j configuration file, and every asset object (deduplicated by hash).
3. All files go through `DownloadManager`: `.part` files with Range resume, size and hash verification before the final move, retries with backoff for network/408/429/5xx only, valid files skipped, free-space check before starting.
4. Only after every file is present is `versions/<id>/.flint-installed` written. "Installed" in the UI means that marker exists. Running install again repairs corrupt or missing files and downloads nothing else.
5. The log4j configuration (`-Dlog4j.configurationFile=...`) is returned as a JVM argument and must be passed at launch. For 1.7 to 1.18 it mitigates the Log4Shell vulnerability.
6. Paths from remote metadata are confined to the game directory; asset hashes must be 40 hex characters; native jars are extracted with zip-slip protection.

Install runs in `InstallCoordinator` (app scope), observed by a `dataSync` foreground service for the notification. After a process kill the user is offered "Retry", which resumes from the files already on disk.

Not handled yet: legacy virtual asset indexes (about 1.7 and older, reported as an error), mirrors, filtering out LWJGL libraries that the Android runtime replaces, Forge/NeoForge installers, JRE download.
