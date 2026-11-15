# Flint Launcher — Progress Tracking (Dola)

Branch: `dola/progress` · Date: 2026-11-15 · Strategy: Spike 0 end-to-end (launch → main menu → world) on arm64-v8a is priority #1. Code we write is clean-room; third-party runtime binaries (JRE/GL4ES/OpenAL) are separate attributed plugins (see `docs/RUNTIME_ARTIFACTS.md`).

Legend: `[x]` selesai & terverifikasi · `[~]` sebagian / fondasi ada, perlu verifikasi/extend · `[ ]` belum dikerjakan · `[!]` blocked (butuh perangkat fisik / artefak eksternal / keputusan owner) — alasan di baris yang sama.

## A. Game Window & Graphics
- [~] Android GLFW integration — fondasi Surface/EGL bridge ada (`egl_smoke.cpp`, `GL4ESSurfaceView.kt`); stub GLFW API clean-room sedang ditulis.
- [ ] GLFW window implementation
- [ ] GLFW event loop
- [ ] GLFW input callbacks
- [~] GL4ES terhubung ke game window — `nativeLoadGL4ES` (dlopen runtime) + render thread ada; GL4ES build-time dijadikan opsional (MIT, `FLINT_BUILD_GL4ES`).
- [~] GL4ES OpenGL symbol loading — runtime dlsym ada di `flint_jni.cpp`; loader generik sedang ditulis.
- [~] GL4ES context initialization — `initialize_gl4es` dipanggil setelah EGL current; perlu context-recovery.
- [ ] LWJGL 2 integration (compat layer memanggil GLFW stub kita)
- [ ] LWJGL 3 integration (compat layer memanggil GLFW stub kita)
- [ ] GPU renderer selection
- [ ] GPU fallback
- [~] OpenGL compatibility testing — `nativeEglCapabilities` probe ada; compat matrix belum.
- [ ] Renderer context recovery

## B. Java Runtime & JVM
- [!] ARM64 Java Runtime verification — butuh artefak JRE aarch64 (GPLv2+CE); mekanisme import+checksum+probe ada (`MobileJreManager`), URL+SHA-256 terverifikasi belum ada (repo Pojav archived).
- [~] Java 8 compatibility — pemilihan JRE by majorVersion ada; belum diverifikasi tanpa artefak.
- [~] Java 17 compatibility — sama.
- [~] Java 21 compatibility — sama.
- [~] Java 25+ compatibility — slot disediakan; belum ada artefak.
- [~] Runtime extraction validation — `SafeTarGzExtractor`/`SafeTarXzExtractor` + probe `libjli.so`/`libjvm.so` ada.
- [~] JNI lifecycle non-blocking — JNI smoke ada; lifecycle wrapper sedang ditulis.
- [ ] JVM thread management
- [ ] JVM shutdown/restart
- [ ] JVM crash recovery
- [~] Native library loading — `System.loadLibrary("flint_native")` + dlopen JLI/GL4ES ada; LD_LIBRARY_PATH plugin set sedang ditulis.

## C. Minecraft Launch Pipeline
- [~] Version metadata download — model+parser manifest ada (`minecraft`); wiring ke download coordinator sedang.
- [~] Libraries download — rules+library model ada; batch download via `download` sedang di-wire.
- [ ] Assets index download
- [ ] Assets objects download
- [ ] Client JAR download
- [ ] Native libraries extraction (Android: filter LWJGL natives, ganti dengan natives kita)
- [ ] Classpath connected to actual launch
- [~] JVM arguments connected — `LaunchArguments` builder ada; wire ke GameLauncher sedang.
- [~] Minecraft game arguments connected — sama.
- [ ] Main class execution (fork JVM via JLI_Launch)
- [ ] Environment variables (LD_LIBRARY_PATH, java.library.path)
- [ ] Game process/session management
- [ ] Exit code handling
- [ ] Launch error reporting

## D. Minecraft Version Support
- [~] Classic, Indev, Infdev, Alpha, Beta, Release 1.0–1.12, Release 1.13–1.16, Release 1.17–1.20, Release 1.21.x, Snapshots, Experimental versions — parser versi generik ada; matrix per-era + test sedang ditulis. (lacak per era di test)

## E. Input Controls
- [~] Touch input bridge ke Minecraft — Touch Controls Editor fondasi ada; bridge ke GLFW event sedang.
- [~] Virtual joystick — fondasi editor ada; implementasi runtime bridge sedang.
- [ ] Camera movement (touch → mouse motion event)
- [ ] Jump/sneak/sprint (key mapping)
- [ ] Keyboard input bridge
- [ ] Mouse movement bridge
- [ ] Mouse click bridge
- [ ] Mouse capture/release
- [ ] Gamepad/controller
- [~] Custom key mapping integration — editor ada; integrasi ke event stream sedang.

## F. Audio
- [ ] Android audio backend (OpenSL/AAudio stub)
- [!] OpenAL Soft — binary LGPL, dynamic-link only; packaging contract + download-on-demand, belum dibundel.
- [ ] LWJGL OpenAL compatibility
- [ ] Sound effects
- [ ] Music playback
- [ ] Audio device management
- [ ] Volume control integration

## G. Account & Authentication
- [~] Microsoft OAuth — client PKCE ada di `auth`; UI sign-in belum.
- [ ] Microsoft device-code flow
- [~] Xbox Live authentication — chain client ada; belum end-to-end tanpa Azure app approval.
- [~] XSTS authentication — sama.
- [~] Minecraft Services authentication — sama.
- [ ] Access token refresh
- [ ] Secure token storage (Android Keystore AES-GCM) — di `account`, sedang dilengkapi.
- [ ] Session integration ke Minecraft (access token → game args)

## H. Mod, Shader & Loader
- [ ] Shader installation
- [ ] Resource pack installation
- [ ] Modpack installation (.mrpack)
- [~] Fabric installer — metadata Fabric/Quilt ada; installer via proses sedang.
- [ ] Forge installer (butuh runtime proses)
- [ ] NeoForge installer (butuh runtime proses)
- [~] Quilt installer — metadata ada.
- [ ] Mod dependency resolution
- [ ] Mod conflict detection
- [~] Version compatibility checking — dasar ada; diperluas.
- [~] Modrinth browser — fondasi ada (mods/modpacks).

## I. Game Files & Storage
- [ ] In-app log viewer
- [ ] GZIP log reader
- [ ] Storage migration
- [~] Per-version game directory — profil terisolasi ada.
- [ ] World/save management
- [ ] World backup/restore
- [ ] Profile import/export
- [~] Game Files Manager — fondasi ada; extend.

## J. UI & Gameplay
- [ ] PLAY terhubung ke sesi Minecraft sungguhan
- [ ] Game session screen (SurfaceView + loading)
- [ ] Loading/progress UI
- [ ] Landscape game mode
- [ ] Immersive fullscreen
- [ ] Crash screen dan recovery
- [ ] Main menu final verification
- [!] Masuk Minecraft main menu — butuh perangkat fisik + JRE + MC terinstall; tidak bisa diverifikasi di VM.
- [!] Masuk world — sama.
- [!] Gameplay benar-benar berjalan — sama.

## K. Build, CI & Testing
- [~] Build APK multi-ABI — abiFilters 4 ABI ada; NDK sedang dipasang.
- [!] ARM64/ARMv7/x86/x86_64 build verification — butuh NDK terpasang + build.
- [~] GL4ES packaging verification — opsional backend; kontrak plugin ada.
- [~] Native library loading test — smoke JNI ada; instrumentation belum.
- [~] Unit tests lengkap — 149 test tertulis, belum pernah dijalankan; sedang dieksekusi+diperbaiki.
- [ ] Instrumentation tests
- [!] Instalasi APK di perangkat — butuh perangkat.
- [!] Launch Minecraft di perangkat — butuh perangkat.
- [!] Gameplay test — butuh perangkat.
- [!] Long-session stability test — butuh perangkat.
- [!] Memory/performance profiling — butuh perangkat.
- [ ] Release APK/AAB (butuh signing secrets owner)

## Blocker yang butuh tindakan user/owner
1. Artefak JRE aarch64 (Java 8/17/21) dengan URL stabil + SHA-256 terverifikasi, atau sediakan file untuk `importLocalArchive`.
2. Persetujuan Azure app registration + Mojang app-ID untuk login Microsoft sungguhan.
3. Perangkat Android arm64 fisik (minSdk 26) untuk verifikasi launch → main menu → world.
4. Release keystore + GitHub Secrets (`FLINT_*`) untuk release APK/AAB.
5. (Opsional) CurseForge API key, friends backend.
