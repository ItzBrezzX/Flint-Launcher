# Flint Launcher: Stage 1 research and feasibility

Date of research: 12 Nov 2026. Each finding is tagged:

- **[searched]** found in sources this session. Most are secondary sources (wikis, issue trackers, third-party sites). Confirm against the primary source before relying on it.
- **[recalled]** written from prior knowledge and not re-checked this session. Treat as a hypothesis to verify in the stage that uses it.

Nothing here was tested on a device.

## 1. Android runtime landscape

- **PojavLauncher and Amethyst are active upstream references as checked on 14 Nov 2026.** The current repositories report recent commits and expose Android source. Both identify LGPL-3.0 at the launcher-project level; every bundled dependency still needs its own licence review.
  - https://github.com/TeamPojavLauncher/PojavLauncher
  - https://github.com/AngelAuraMC/Amethyst-Android
- **MojoLauncher is the preferred engine integration reference for Flint.** The inspected snapshot is `MojoLauncher/MojoLauncher@1b8d90424c1cb791321d45f60ac62c54f4b013fa`, licensed LGPL-3.0. Its README describes support from rd-132211 through 26.x snapshots and Forge/Fabric; recent release notes describe 26.2+ support and separate LWJGL2-on-GLFW and LWJGL3/GLFW paths. This is much closer to Flint's all-version target than implementing a single-version renderer path.
  - https://github.com/MojoLauncher/MojoLauncher
  - Native submodules pinned by that snapshot: GLFW `7a6c6ceb51f20cd0db84dec2914ac1a0237051f6`, mojoexec `9ec4b836e93900135bad823eaac16ebc81cf6b98`, SDL `c63845bd49c5a7667d1de0f6134f85ed1ccc8889`, NoBlocklist `f4209d4755d6af6a19fedbe578313e29ebfc45ff`.
  - It is not a drop-in dependency: `:app_mojo` is an Android application module with its own app identity, UI, Activities, resources, services and settings. Flint should extract the engine behind its own UI and interfaces. See [MOJO_BACKEND_INTEGRATION.md](MOJO_BACKEND_INTEGRATION.md).
- **Zalith Launcher 2** is an active Kotlin/Compose launcher built on a Pojav-derived engine; its repository identifies GPL-3.0. This is a useful architectural reference, not a drop-in runtime dependency.
  - https://github.com/ZalithLauncher/ZalithLauncher2
- **Legacy LWJGL2 path:** MojoLauncher maintains an LWJGL2-on-GLFW port whose README reports local tests with Minecraft 1.2.5, 1.12.2 and Forge 1.12.2. Its Android build requires a compatible Android GLFW library and matching native builds; the repository is a reference until the exact source and redistribution terms are audited.
  - https://github.com/MojoLauncher/lwjgl2-glfw
  - https://github.com/MojoLauncher/glfw
- **Modern LWJGL3 path:** Amethyst/Pojav use Android-specific GLFW/LWJGL integration and a game Surface/input bridge. The complete upstream app also depends on multiple native submodules and app-level classes; copying one Java or C file is not a complete backend.
- Renderers such as GL4ES, MobileGlues and LTW are separate compatibility choices. A renderer's presence in settings or a successful system GLES pbuffer test does not prove the selected Minecraft path can create a window or render frames.
- Java runtime selection must follow version metadata. Flint's catalogue currently includes Android mobile JRE artifacts for Java 8, 17, 21 and 25, but archive presence/layout is not proof that the VM executes on a particular Android device.

**Conclusion.** Running Minecraft on Android needs an ARM64 JRE built for Android, an LWJGL fork, a GL translation layer and input/windowing glue. Rebuilding this from scratch is a multi-person-year effort and is not realistic. Flint should reuse maintained, licence-compatible components and own the launcher shell around them (see ARCHITECTURE.md §3).

## 2. Minecraft versions and Java

- Mojang switched to year-based numbering after 1.21.11 (Dec 2025). Reported releases: 26.1 (24 Mar 2026), 26.1.2 (9 Apr 2026), 26.2 (16 Jun 2026). Snapshots for 26.3 were published by Aug 2026. [searched]
  - https://minecraft.wiki/w/Java_Edition_26.1/Development_versions
  - https://github.com/PrismarineJS/minecraft-data/issues/1174
- **26.1 and later require Java 25.** Versions 1.20.5 to 1.21.11 use Java 21; older versions need 17 or 8. [searched for 26.x, recalled for older]
- 26.1 is described as the first unobfuscated release, which changes how loaders and mods map names. [searched]
- The version manifest (`piston-meta.mojang.com/mc/game/version_manifest_v2.json`) is the only safe source of truth for what exists today. The launcher must never hardcode a version list. [recalled]
- The per-version JSON carries `javaVersion.majorVersion`, `mainClass`, `libraries[]` with `rules`, `downloads.artifact` and native classifiers, `assetIndex`, and arguments. Very old versions use `minecraftArguments` instead of `arguments`. [recalled]

**Consequence.** The example in the project brief ("1.21.11 / Fabric / Java 21") is the last Java 21 version. The runtime layer must ship or download at least JRE 8, 17, 21 and 25, chosen from `javaVersion.majorVersion` per version.

## 3. Authentication

- The chain is: Microsoft OAuth, then Xbox Live user token, then XSTS token, then `login_with_xbox` on Minecraft Services, then entitlement check, then profile fetch. [searched]
  - https://minecraft.wiki/w/Minecraft_Wiki:Projects/wiki.vg_merge/Microsoft_Authentication_Scheme
- **A third-party launcher needs its own Azure app registration, and Mojang requires that app ID to be reviewed and approved** before Minecraft Services accepts it. Unapproved IDs are rejected. [searched, from third-party launcher docs; confirm the current form and process]
- XSTS error codes (no Xbox account, child account, region blocked) are documented in several places, and the sources I found disagreed on which code means what. The mapping must be taken from a primary source in Stage 5 and covered by tests. [searched, conflicting]
- PKCE authorization-code flow with a custom-scheme redirect is the spec'd approach. Device-code flow is a fallback if redirect handling proves fragile. [recalled]

## 4. Mod loaders

- **Fabric** metadata: `meta.fabricmc.net/v2/versions/...` (game versions, loader versions, per-pair launcher profile JSON). **Quilt** metadata: `meta.quiltmc.org/v3/versions/...`. [recalled]
- **NeoForge**: versions come from the maven metadata on `maven.neoforged.net`. Its version scheme changed with the new Minecraft numbering, so never parse it by assuming `1.x`. [searched in part]
  - https://neoforged.net/news/2025-retrospection
- **Forge**: `files.minecraftforge.net/net/minecraftforge/forge/promotions_slim.json` and `maven.minecraftforge.net`. [recalled]
- Forge and NeoForge ship an **installer JAR** that runs processors (patching, remapping). On Android this installer must run on the embedded JRE, so the runtime must exist before loaders can be installed. Fabric and Quilt only need a profile JSON plus libraries. [recalled]

## 5. Mods and modpacks

- **Modrinth** API v2 at `api.modrinth.com/v2`: requires a unique, descriptive `User-Agent`; documented rate limit about 300 requests per minute per IP, with `X-Ratelimit-*` headers; version files carry `sha1` and `sha512` hashes. [searched, recalled]
  - https://docs.modrinth.com/api/
- **Modrinth modpacks** are `.mrpack` ZIPs: `modrinth.index.json`, plus `overrides/` and optional `client-overrides/`. [recalled]
- **CurseForge** API at `api.curseforge.com/v1` needs an API key (`x-api-key`) issued to a developer. Keys must not be shipped in a public APK. Some files return no `downloadUrl` because the author disabled third-party distribution. **Flint will not work around that**: those files are shown as "download from CurseForge", never fetched. [searched, recalled]
- CurseForge modpacks use a `manifest.json` with project and file IDs that are resolved through the API. [recalled]

## 6. Platform constraints to design around

- Android 10+ blocks executing files from app-writable storage for apps with a high `targetSdk`. The JVM must be loaded in-process (`dlopen` + `JNI_CreateJavaVM`), not started with `exec`. Only one JVM can exist per process, so the game runs in its own Android process. [recalled, must be proven in the runtime spike]
- Android 14+ requires a declared foreground-service type for long downloads (`dataSync`) and the `POST_NOTIFICATIONS` runtime permission on 13+. [recalled]
- Android Keystore can wrap secrets in hardware-backed keys; refresh tokens should never be stored in plain text. [recalled]
- Self-updating from GitHub Releases needs `REQUEST_INSTALL_PACKAGES`; Android enforces same-signer on update, which doubles as an integrity check. [recalled]

## 7. Toolchain

- Current Compose BOM is 2026.09.00 with Compose compiler plugin 2.4.10; Compose 1.12 requires compileSdk 37 and AGP 9. [searched]
  - https://developer.android.com/develop/ui/compose/compiler
- The scaffold is intentionally pinned to AGP 8.7.3 / Gradle 8.9 / Kotlin 2.0.21 because it was written without any ability to compile. See ARCHITECTURE.md §9.

## 8. Server list and status

- **Status API.** mcstatus.io v2: `GET https://api.mcstatus.io/v2/status/java/<address>` (a separate `/bedrock/` route exists; using the Java route for a Bedrock host reports offline). The response has `online` and, when online, `players.online` / `players.max`. The documented limit is about 5 requests per second per client IP. [searched]
  - https://mcstatus.io/docs
- I did **not** read mcstatus.io's terms of service. Check them before shipping, and keep the lookup switchable in Settings. [open]
- **Addresses** (secondary sources: server guides and lists, 2026): Hypixel `mc.hypixel.net`; MCC Island `play.mccisland.net`; DonutSMP `donutsmp.net`; Mineplex `mineplex.com` (sources disagree: `us.mineplex.com`, `play.mineplex.com` also appear); Hoplite `mc.hoplite.gg` (taken from the reference screenshot, not confirmed by a source). Website URLs are inferred. [searched in part]
- The default list is a public list, not a partner list. Showing it as "partners" would imply an endorsement that does not exist.

## 10. How other launchers install a version

- A third-party launcher's documented pipeline: version JSON, client jar with SHA-1, libraries with platform rules and SHA-1, native extraction, asset index and objects (skipping files that already verify), then the log4j configuration from the version JSON's `logging` section. [searched]
  - https://github.com/ClassicLauncher/classiclauncher-minecraft-java
- `client.json` documents `logging.client` (`argument`, `file{id,sha1,size,url}`, `type`). The official launcher stores the file under `assets/log_configs/` and passes `-Dlog4j.configurationFile=<path>`. [searched]
  - https://wiki.ronlab.site/content/minecraftwiki_en_all_maxi_2025-11/Client.json
- Mojang's Log4Shell notice says modified clients and third-party launchers may not be patched automatically, which is why Flint downloads and applies this file. [searched]
  - https://minecraft.net/article/important-message--security-vulnerability-java-edition
- I could not read PojavLauncher's own downloader source (only repository READMEs came up), so Pojav-specific handling, such as replacing LWJGL libraries, is design knowledge and not verified here. [open]

## 9. Not yet researched

Mojang asset download details and library rule edge cases, exact Android foreground-service behaviour on current Android versions, legal review of third-party licences and Mojang's branding rules, and candidate news and server-status sources. Each is scheduled in the stage that needs it.
