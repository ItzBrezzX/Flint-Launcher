# Progress — ANDROID/UI/AUTH/STORAGE shard (dola/shard-android)

Branch: `dola/shard-android` · Baseline: `7d1ffb0` · Stack pinned (AGP 8.7.3, Kotlin 2.0.21, compileSdk 35, minSdk 26, Java 17, Gradle 8.9, Compose BOM 2024.10.01).
Gradle was NOT run in this shard (Android SDK/NDK being reinstalled); code is written to match existing patterns and will be compiled by the organizer at merge.

## Legend
- ✅ Implemented (code written, unit-tested where pure-JVM)
- 🟡 Stub / skeleton (interface + honest placeholder, not functional end-to-end)
- 🔴 BLOCKED (requires real device / native hand-off / other shard — explicitly not faked)
- ➡️ Shared-dep / API request to another shard

---

## G. Account & Authentication

| Item | Status | Notes |
|---|---|---|
| Microsoft OAuth PKCE sign-in UI | ✅ | `ui/accounts/SignInScreen.kt` + `app/SignInViewModel.kt`. Browser PKCE via `MicrosoftAuthClient.authorizationUrl`; custom-scheme redirect `flintlauncher://oauth` captured by `SignInRedirectActivity` (manifest intent-filter) → `RedirectHolder` (process-wide StateFlow) → ViewModel exchanges code. No password handling. |
| Device-code flow option | ✅ | `auth/DeviceCodeAuthClient.kt` (RFC 8628): request code, show user code + verification URI, poll with `authorization_pending`/`slow_down`/`expired_token`/`access_denied` handling. UI shows code + copy + open-URL. |
| Xbox/XSTS/MC Services chain reuse | ✅ | Reuses existing `MicrosoftAuthClient.loginMinecraft` unchanged. |
| Access token refresh | ✅ | `app/MinecraftSessionManager.kt`: reads encrypted refresh token from vault, `authClient.refresh`, persists rotated refresh token, runs Xbox→XSTS→MC chain, caches access token in memory only with 5-min expiry headroom. `invalid_grant` → `SessionError.ReauthRequired`. |
| Secure token storage (Keystore AES-GCM) | ✅ | Pre-existing `account/TokenVault.kt` (AndroidKeystoreTokenVault, AES-256-GCM, AtomicFile, randomized IV, plaintext zeroed). Wired via `MicrosoftAccountStore`. Refresh token encrypted at rest; access token memory-only. |
| Session integration into launch (accessToken+UUID+username→game args) | ✅ | `PlayLaunchCoordinator` now resolves `ResolvedSession` (accessToken, userType `msa`/`legacy`, uuid) and passes into `LaunchContext` + `LaunchRequest`. Previously Microsoft accounts were hard-blocked; now they go through the real chain. |
| Offline accounts (name-based UUID, labelled Offline) | ✅ (verified/extended) | Pre-existing `OfflineUuid`, `UsernameRules`, `AccountManager.createOfflineAccount`, account chip labels OFFLINE in amber. Verified; extended via session resolver (`"0"` token, `legacy`). |
| Azure client id configuration | 🟡 | `app/AuthConfig.kt`: public-client only (no secret). `CLIENT_ID` defaults to blank → sign-in UI honestly shows "not configured" and disables flows. Set `FLINT_AZURE_CLIENT_ID` at build time (documented). ➡️ Request: a build-config mechanism to inject the client id without committing it. |

## I. Game Files & Storage

| Item | Status | Notes |
|---|---|---|
| In-app log viewer | ✅ | `ui/logs/LogViewerScreen.kt` + `app/GameLogReader.kt` + `ToolsViewModel`. Lists `logs/latest.log` + rotated logs. |
| GZIP log reader | ✅ | `GameLogReader` detects `.gz` (extension + magic bytes `0x1f8b`), decodes via `GZIPInputStream`, bounded read (512 KB) + tail lines. Unit-tested round-trip. |
| Storage migration | ✅ | `app/StorageMigration.kt`: copy + verify-by-size + delete old; partial-failure safe (failed files kept); refuses new-root-inside-old-root. Unit-tested. ➡️ Wire into Settings UI when a migration trigger is defined. |
| Per-version game directory | ✅ (verified) | Pre-existing `installer/GameLayout` (versions/libraries/assets under root) + `PlayLaunchCoordinator` per-account profile dir under `profiles/<hash>`. Verified; `ProfileDirs` in :profiles also per-profile. |
| World/save management (list/backup/restore via zip) | ✅ | `app/WorldManager.kt` + `ui/worlds/WorldManagerScreen.kt`. Backup zips saves dir with timestamp; restore unzips with path-traversal guard (entries must stay under saves/). Unit-tested round-trip + traversal rejection. |
| Profile import/export UI | ✅ | `ui/profiles/ProfileImportExportScreen.kt` + `ToolsViewModel`. JSON format + risky-argument filter owned by :profiles (`ProfileRepository.export/importProfile`); UI reports dropped risky args honestly. |

## J. UI & Gameplay (non-gameplay parts)

| Item | Status | Notes |
|---|---|---|
| PLAY button connected to real launch flow | ✅ | `HomeViewModel.launchSelected` emits `HomeEvent.StartGameSession(versionId)`; `MainActivity` starts `GameSessionActivity`. Activity runs full flow: JRE check → install check → arg build → `GameLauncher.launch` → session screen. (Previously launch happened inline and only showed a toast message.) |
| Game session screen (SurfaceView + loading/progress) | ✅ | `ui/session/GameSessionScreen.kt`: `AndroidView` binds `GL4ESSurfaceView` (owned by :native, used not edited); loading overlay with spinner + status; running overlay with renderer status + controls. `app/GameSessionController` monitors JVM alive + tails stdout. |
| Landscape game mode | ✅ | `GameSessionActivity` manifest `sensorLandscape` + `configChanges`. |
| Immersive fullscreen | ✅ | `WindowInsetsControllerCompat` hide system bars + `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, `FLAG_KEEP_SCREEN_ON`, `LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS`, `decorFitsSystemWindows=false`. Toggle button in overlay. |
| Crash screen + recovery | ✅ | `ui/session/GameSessionScreen.kt` `CrashScreen`: reason + exit code + expandable log + copy log + retry + back-to-launcher. Controller detects JVM exit and transitions to Crashed. |
| Main menu final verification screen | ✅ (honest) | `ui/session/MainMenuVerificationScreen.kt`: checklist where every device-dependent item (title screen render, touch/keyboard input, audio, world entry, multiplayer) is marked 🔴 BLOCKED; JVM launch marked ✅. Nothing faked. |
| Enter main menu / enter world / actual gameplay | 🔴 BLOCKED | Requires LWJGL/GLFW Android hand-off + real device GPU/input. Explicitly marked in running overlay banner + verification screen. Not claimed. |

## F. Audio

| Item | Status | Notes |
|---|---|---|
| Android audio backend interface | ✅ | `app/audio/AudioBackend.kt`: `init/write/setVolume/pause/resume/release` + `Volume` clamp + `VolumeController`. |
| AudioTrack impl skeleton | ✅ | `app/audio/AudioTrackBackend.kt`: MODE_STREAM PCM16, post-M `AudioTrack.Builder` (USAGE_GAME) + legacy path, volume via `setVolume`/`setStereoVolume`, lifecycle wired. 🟡 No real PCM data yet — Minecraft sound engine must route samples here (native shard). |
| AAudio/OpenSL ES | 🟡 | Skeleton contract only; AudioTrack is the universal fallback. ➡️ Request: native shard owns AAudio/OpenSL; this shard provides the Java-side interface to bind to. |
| Volume control | ✅ | `VolumeController` (0..1 clamp, applies to active backend). ➡️ Wire into Settings UI slider. |
| OpenAL Soft = dynamic plugin (LGPL, dlopen) | ✅ (contract documented) | `app/audio/OpenAlDynamicPlugin.kt`: user-replaceable `filesDir/audio-plugins/libopenal.so`, lazy load, AudioTrack fallback, never statically linked, never downloaded by Flint. 🔴 Actual dlopen/JNI bridge owned by native shard — not implemented here. Contract documented in file header. |

## database / news / friends / partners

| Item | Status | Notes |
|---|---|---|
| Room scaffold (accounts, profiles, download queue) | ✅ | `database/`: 3 entities + 3 DAOs + `FlintDatabase` v1 (schema exported via ksp arg). JSON-file repos remain source of truth; Room is adoptable incrementally. |
| news explicit unavailable stub | ✅ | `news/NewsRepository.kt`: `NewsAvailability.Unavailable` — never fabricates articles. |
| friends explicit unavailable stub | ✅ | `friends/FriendsRepository.kt`: `FriendsAvailability.Unavailable` — never fakes presence. |
| partners verify/extend | ✅ (verified) | Pre-existing real impl (bundled server feed + mcstatus.io status lookups + tests). Verified wired in `AppContainer`. No change needed. |

## K (partial). Unit tests

| Item | Status |
|---|---|
| `MinecraftSessionManager` (offline/refresh/cache/invalid_grant/not-configured) | ✅ `app/src/test/.../MinecraftSessionManagerTest.kt` |
| `GameLogReader` (list/plain tail/gzip decode) | ✅ `GameLogReaderTest.kt` |
| `WorldManager` (backup/restore round-trip, path-traversal, ordering) | ✅ `WorldManagerTest.kt` |
| `StorageMigration` (migrate/skip/refuse nested) | ✅ `StorageMigrationTest.kt` |
| `DeviceCodeAuthClient` (parse + error) | ✅ `auth/src/test/.../DeviceCodeAuthClientTest.kt` (MockWebServer) |
| Compose UI tests / instrumentation tests | 🔴 Requires emulator/device; not run in this shard. |

---

## Shared-dep / API requests (➡️)

1. **Build-config injection of Azure client id** (`AuthConfig`): need a non-secret way to bake `FLINT_AZURE_CLIENT_ID` (public client, no secret) into the APK. Currently blank default → sign-in disabled honestly.
2. **Audio native bridge**: `AudioBackend` Java interface ready; native shard to provide AAudio/OpenSL ES + OpenAL Soft dlopen JNI binding. `OpenAlDynamicPlugin.load()` is the intended call site.
3. **Game exit code**: `PojavStyleGameLauncher` captures `JLI_Launch` return code in logs but does not expose it via `GameLauncher` interface. `GameSessionController` treats any JVM exit as crash. ➡️ Add `exitCode: Int?` to a `GameProcess`/status API.
4. **Volume UI**: `VolumeController` ready; Settings screen (runtime shard?) to add a slider.
5. **Storage migration trigger**: `StorageMigration.migrate()` ready; needs a Settings entry + old-root detection.

## Device-blocked items (🔴 — explicitly not faked)

- Minecraft main menu rendering, world entry, actual gameplay (LWJGL/GLFW Android hand-off + GPU).
- Touch/keyboard/mouse input routing into Minecraft.
- Audio playback (backend skeleton + interface only; no PCM data path).
- OpenAL Soft dlopen actually loading and playing.
- Compose instrumentation tests, full end-to-end launch on device.
- Real Microsoft sign-in end-to-end (requires configured Azure client id + device browser).

## Files added/modified (this shard)

- `database/`: `FlintDatabase.kt`, `entity/*` (3), `dao/*` (3)
- `auth/`: `DeviceCodeAuthClient.kt`, `MicrosoftAuthOperations.kt`; `MicrosoftAuthClient.kt` (+deviceCode endpoint, implements interface)
- `account/`: unchanged (TokenVault/AccountRepository already existed; verified)
- `app/`: `AuthConfig.kt`, `MinecraftSessionManager.kt`, `RedirectHolder.kt`, `SignInRedirectActivity.kt`, `SignInViewModel.kt`, `GameSessionController.kt`, `GameSessionActivity.kt`, `GameLogReader.kt`, `WorldManager.kt`, `StorageMigration.kt`, `ToolsViewModel.kt`, `audio/*` (3); modified `PlayLaunchCoordinator.kt`, `FlintApplication.kt`, `HomeViewModel.kt`, `MainActivity.kt`, `AccountsViewModel.kt` (unchanged), `AndroidManifest.xml`
- `ui/`: `accounts/SignInScreen.kt`, `session/GameSessionScreen.kt`, `session/MainMenuVerificationScreen.kt`, `logs/LogViewerScreen.kt`, `worlds/WorldManagerScreen.kt`, `profiles/ProfileImportExportScreen.kt`, `state/SignInUiState.kt`, `state/GameSessionUiState.kt`, `state/ToolsUiState.kt`; modified `state/AccountsUiState.kt`, `state/HomeUiState.kt`, `accounts/AccountsScreen.kt`, `home/HomeScreen.kt`, `home/HomeSections.kt`
- `news/`, `friends/`: explicit unavailable repositories
- Tests: `app/src/test/...` (4), `auth/src/test/...` (1)
- `docs/progress-shard-android.md` (this file)
