# Stage 15: independent audit (honest status)

Date: 12 Nov 2026. Scope: everything in this repository. Method: reading and static consistency checks only. **There was no Kotlin compiler, no Gradle, no network and no device in the environment that wrote this code.**

## What was checked

- Bracket balance of all Kotlin sources, that every internal import resolves to a declared symbol, that every version-catalog alias exists, that every `project(":x")` is declared, that the module graph has no cycles, that `R.string` references exist, that XML and workflow YAML parse, and that no secrets are committed. All passed.
- Test logic was traced by hand against the code (expected values, ordering, fixtures).

## What has NOT been verified

| Claim someone might make | Reality |
|---|---|
| "It compiles" | Unknown. ~45 Kotlin files were never compiled. Expect some compile errors on the first build; a single one fails the whole APK build |
| "Tests pass" | Unknown. 149 unit tests exist, none has been run |
| "Download works" | Designed and tested against a *fake* Mojang server in code that has not run. Never exercised against Mojang's real servers or on a phone |
| "Minecraft runs" | No. There is no Java runtime or renderer in the project |
| "Microsoft login works" | The chain client is written. It has never talked to Microsoft, and it needs an Azure app registration approved by Mojang |
| "CI works" | Workflows were written and syntax-checked, never executed |

## Requirement coverage

| Brief item | State |
|---|---|
| Version manager (release/snapshot/beta, search, sort, installed status) | Written + tests |
| Installation manager: vanilla | Written + tests (end-to-end with fake server) |
| Installation manager: Fabric/Quilt | Metadata + compatibility only; no install flow in the UI |
| Installation manager: Forge/NeoForge | Reported as unsupported (needs the Java runtime) |
| Download manager (all listed stats, pause/resume/cancel/retry, ranges, backoff, SHA-1, corruption, low storage, persistence) | Written + tests. Pause and cancel are tested only through the coordinator with fakes, not inside `DownloadManager` itself |
| Background execution | Foreground service (`dataSync`) written, untested. Android 15 limits `dataSync` services to about 6 hours per day |
| Accounts: offline UUID, Microsoft chain | Written + tests. No account UI, no secure token storage |
| Skins | Not started |
| Mods, modpacks | Not started |
| Profiles | Domain + storage + tests. No UI, not wired to Home or launch |
| Partners/servers | Written + tests; default list is public servers, not partners |
| News, friends, settings | Not started |
| Auto-update of launcher, metadata refresh intervals | Not started (version catalog is fetched on demand only) |
| Launch pipeline step 7-8 (renderer, start game) | Not started |

## Known risks and bugs to look for first

1. **First compile.** Likeliest trouble spots: Compose API signatures, `fun interface` with suspend methods, MockWebServer test helpers, and kotlinx.serialization on private classes.
2. **Runtime feasibility.** Android 10+ restrictions on executing downloaded code, JRE 25 for Minecraft 26.x, renderer support per device. Spike 0 decides whether the project is viable as designed.
3. **LWJGL libraries.** The planner downloads Mojang's LWJGL jars; the Android runtime needs its own. They are not filtered yet, so the install is larger than needed and the classpath would be wrong at launch.
4. **Legacy assets.** Versions with virtual asset indexes (about 1.7 and older) fail with a clear message instead of installing.
5. **Storage location.** Game files go to app-specific external storage when available. On devices where that is small or slow this needs a user-visible setting.
6. **Status API.** The server list depends on mcstatus.io, whose terms were not read.
7. **XSTS error table, auth endpoint URLs and Fabric/Quilt endpoints** are from memory or community docs and must be confirmed against primary sources.
8. **Unverified default servers.** Hoplite's address and all website URLs are unconfirmed.
9. **Licence.** No `LICENSE` file; the choice depends on which Pojav-derived components are bundled.

## Recommended order from here

1. Run **Actions: Android CI** and fix compile errors until it is green. Then read the test report and fix failures.
2. Install the debug APK on an ARM64 phone: open PROFILES, install a small old release, watch the Download Manager, kill the app mid-download and confirm Retry resumes.
3. Run Spike 0 (architecture §3): start vanilla to the main menu with an existing Android JRE and renderer.
4. Only then continue with accounts UI, profiles UI, mods and settings.

A feature should be called supported only when the launch matrix in `tests/README.md` has a passing row for it.
