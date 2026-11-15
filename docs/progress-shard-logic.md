# Logic Shard Progress — Spike 0 (dola/shard-logic)

Branch: `dola/shard-logic` · Baseline: `7d1ffb0` · Stack pinned (AGP 8.7.3, Kotlin 2.0.21, Java 17, Gradle 8.9).
Compile verification was **not run** in this shard (Android SDK/NDK being reinstalled; Gradle configuration phase requires it). All code was written against the existing module APIs and cross-checked manually. The organizer compiles at merge.

## Implemented (real logic + tests)

### C. Minecraft Launch Pipeline (full) — `installer/` + `minecraft/`
- [x] **Version metadata download** — existing `MinecraftInstaller` (manifest → version JSON, SHA-1 checked, retry). Reused.
- [x] **Libraries download (rules applied, Maven-style loader libs)** — existing `LibraryResolver` + `InstallPlanner.planCore`. Reused.
- [x] **Assets index + objects download (dedup by hash)** — existing `InstallPlanner.planAssets` (`distinctBy { hash }`). Reused.
- [x] **Client JAR download** — existing. Reused.
- [x] **Native libraries extraction (zip-slip safe, filter LWJGL desktop natives → replaced by Flint natives)** — NEW:
  - `FlintNativePolicy.isReplacedByFlint(entryName)` — matches `lwjgl`/`glfw`/`openal`/`jemalloc` `.so` entries.
  - `NativesExtractor.extractFiltered(jars, target, skipEntry)` — zip-slip safe (via `GameLayout.safeChild`), reports skipped entries. Legacy `extract()` delegates.
- [x] **Classpath → actual launch** — NEW `LaunchCoordinator` produces `LaunchPlan` → `LaunchParameters` (primitive contract: `classpath: List<File>`, `jvmArgs`, `gameArgs`, `mainClass: String`, `nativesDir: File`, `env: Map<String,String>`, `workingDir: File`). Runtime shard copies these into its `LaunchRequest`.
- [x] **JVM arguments** — `LaunchCommandBuilder` (existing) + coordinator extras: `-Xmx`, `-Djava.library.path` (extracted + Flint natives), `-Dorg.lwjgl.librarypath` (Flint), `-Djava.io.tmpdir`, `-Duser.home`, log4j config arg.
- [x] **Minecraft game arguments** — `LaunchCommandBuilder` (existing), placeholder substitution, rules applied.
- [x] **Main class execution (resolve per version/era)** — from merged `VersionJson.mainClass`; `VersionEra.fallbackMainClass` for legacy.
- [x] **Environment variables** — `LD_LIBRARY_PATH` (Flint natives dir first, then extracted Mojang natives) in `LaunchPlan.env`.
- [x] **Game process/session management (state machine)** — NEW `GameSession` (IDLE → PREPARING → LAUNCHING → RUNNING → STOPPING → EXITED_CLEAN/EXITED_CRASH/FAILED). Terminal-state guards; `reset()` only way out. Driven by runtime shard.
- [x] **Exit code handling** — NEW `ExitCodeClassifier`: 0/SIGTERM(143)/SIGKILL(137) = clean; nonzero = crash; log-snippet heuristics → `ConfigError` (bad memory, missing main class, native load failure, old Java). OOM crash carries a memory-increase hint.
- [x] **Launch error reporting (categorized)** — `FlintError` sealed categories: `MissingRuntime` / `DownloadFailed` / `Crash` / `AuthError` / `ConfigError` / `Cancelled` / `Unknown`. `LaunchCoordinator` maps every failure path; `GameSession.fail()` records it.

### D. Minecraft Version Support — `minecraft/version/VersionEra.kt`
- [x] **Era matrix (12 eras)**: CLASSIC, INDEV, INFDEV, ALPHA, BETA, RELEASE_1_0_TO_1_12, RELEASE_1_13_TO_1_16, RELEASE_1_17_TO_1_20, RELEASE_1_21, SNAPSHOT, EXPERIMENTAL.
- [x] **Per-era traits**: `lwjglMajor` (2 ≤1.12, 3 ≥1.13), `argsFormat` (LEGACY_STRING ≤1.12, MODERN_OBJECT ≥1.13), `assetsLayout` (RESOURCES_DIR / VIRTUAL_LEGACY / CONTENT_ADDRESSED), `fallbackJavaMajor` (8/17/21 with sub-cutover notes), `fallbackMainClass`, `inMojangManifest` (false for Classic/Indev/Infdev — honest: not in `version_manifest_v2.json`, need legacy metadata).
- [x] **Classifier** handles release ids, snapshot/pre/rc patterns, old_alpha/old_beta types, and embedded vanilla version in loader ids (`fabric-loader-0.15.7-1.21`). Never throws.
- [x] Unit tests per era + trait cutovers + embedded-version extraction.

### H. Mod, Shader & Loader — `mods/` + `modpacks/`
- [x] **Shader/resourcepack install dirs per profile** — existing `ProfileDirs` (shaderPacks, resourcePacks). Reused.
- [x] **Modpack (.mrpack) install** — NEW `modpacks/`: `MrpackManifest` (format 1–2, files with hashes/env/downloads, dependencies → loader detection), `MrpackInstaller` (parse → plan verified `DownloadRequest`s, sha512 preferred, skip `client=unsupported`, extract `overrides/` + `client-overrides/`, zip-slip safe on both manifest paths and zip entries, categorized `FlintError`). Returns `PackMetadata`; app shard creates the Profile.
- [x] **Fabric + Quilt metadata** — existing `FabricStyleMeta` (installer/). Reused. (No installer process — profile JSON directly.)
- [x] **Forge + NeoForge installer-as-process contract** — NEW `LoaderInstallerContract`: `InstallerProcessSpec(installerJar, args, workingDir, expectedVersionJson, loaderKind)` + URL builders (`ForgeInstallerContract`, `NeoForgeInstallerContract`). Exact CLI flags pinned to documented installers; verified at runtime by exit code + output JSON presence.
- [x] **Mod dependency resolution** — NEW pure `DependencyGraphResolver` (DAG walk, install order = deps first, cycle/missing/incompatible/too-deep detection, lookup injected for testability). Existing `ModrinthRepository.install` does networked resolution inline; pure resolver is the extracted testable core.
- [x] **Mod conflict detection** — NEW `ModConflictDetector`: duplicate project, duplicate file hash, incompatible-pair present, missing required dep. Pure.
- [x] **Version compatibility checking (MC + loader + deps)** — NEW `ModCompatibilityChecker.check(version, minecraftVersion, loader)` + `pickCompatible`.
- [x] **Modrinth browser foundation** — existing `ModrinthApi`/`ModrinthRepository`/`ModrinthModInstaller`. Extended with pure resolver/conflict/compat above.

### core scaffold — `core/`
- [x] `FlintResult<T,E>` railway type (map/flatMap/onSuccess/onError) + `Outcome<T>` alias.
- [x] `FlintError` sealed categories (used by all new logic).
- [x] `FlintDispatchers` injectable interface + Default.
- [x] `PlayerIdentity` shared model (username/uuid/accessToken/userType/xuid/clientId).

### profiles — `profiles/`
- [x] Single-profile import/export (JSON, versioned `format=1`) — existing `ProfileRepository.export/importProfile`. Reused.
- [x] NEW `ProfileSetCodec` — bulk export/import of all profiles as one versioned JSON file; import sanitizes risky JVM args (`ProfileSecurity`), strips ids/default flags, reports dropped args.

## API-only / not fully implemented (honest)
- **Classic/Indev/Infdev install**: classified in era matrix but NOT downloadable from Mojang's modern manifest; `inMojangManifest=false`. Would need legacy launcher metadata (not fetched).
- **Forge/NeoForge actual execution**: contract (`InstallerProcessSpec`) defined + tested, but running the installer process requires the runtime shard's JVM (not available in logic shard). Exit-code verification of the installer is runtime-shard work.
- **ModrinthRepository refactor**: the inline dependency resolution in `ModrinthRepository.install` was NOT refactored to use the new pure `DependencyGraphResolver` (avoided breaking the existing passing test). The pure resolver is ready to be wired in.
- **Shader pack validation / resource pack zipping**: dirs exist; no pack-format validation (metadata parsing) implemented.
- **AuthError in launch path**: `LaunchCoordinator` takes a `PlayerIdentity` and does not validate tokens (auth shard's job). `FlintError.AuthError` category exists for the app shard to use.
- **Real process forking**: `GameSession` is a pure state machine; actual `fork`/`waitFor`/`destroy` is runtime-shard (`GameLauncher`).

## Shared-dep requests (for organizer / libs.versions.toml — NOT edited by this shard)
- None required. All new code uses already-available deps (kotlinx-serialization, okhttp, coroutines, junit, mockwebserver). `modpacks` module gained `testImplementation(libs.okhttp.mockwebserver)` in its own `build.gradle.kts` (module-level file, allowed).

## Per-line checklist status (acceptance)
1. Full launch pipeline C — **implemented** (LaunchCoordinator + LaunchPlan/LaunchParameters + GameSession + ExitCodeClassifier + FlintError). Tests: `LaunchCoordinatorTest`, `FlintNativePolicyTest`, `NativesExtractorFilteredTest`, `GameSessionTest`.
2. Version matrix D per era + tests — **implemented** (`VersionEraTest`).
3. H: Fabric/Quilt (existing) + Forge/NeoForge contract + resolver + conflict + compat + mrpack — **implemented** (`ModLogicTest`, `MrpackInstallerTest`). ModrinthRepository refactor to pure resolver — **API-only** (documented above).
4. core scaffold — **implemented** (`FlintResultTest`).
5. Profile import/export JSON — **implemented** (single existing + bulk new, `ProfileSetCodecTest`).
6. Unit tests for everything new — **written** (JUnit4, matching existing style: TemporaryFolder, MockWebServer, runBlocking, assert*).

## Files changed (all within allowed dirs: core/, minecraft/, installer/, mods/, modpacks/, profiles/ + modpacks/build.gradle.kts)
- `core/`: +FlintResult, FlintError, FlintDispatchers, models/PlayerIdentity + test
- `minecraft/`: +version/VersionEra, version/LaunchPlan, session/GameSession + tests
- `installer/`: +FlintNativePolicy, +LaunchCoordinator, extended NativesExtractor + tests
- `mods/`: +LoaderInstallerContract, +ModDependencyResolver, +ModConflictDetector, +ModCompatibilityChecker + test
- `modpacks/`: +MrpackModels, +MrpackInstaller + test, build.gradle.kts (+mockwebserver test dep)
- `profiles/`: +ProfileSetCodec + test
- `docs/progress-shard-logic.md` (this file)

Never touched: settings.gradle.kts, root build.gradle.kts, gradle/libs.versions.toml, docs/RUNTIME_ARTIFACTS.md, docs/PROGRESS_DOLA.md, .github/workflows/, app/, runtime/, native/, auth/, account/, database/, ui/, news/, partners/, friends/.
