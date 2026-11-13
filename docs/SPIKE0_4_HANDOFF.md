# Spike 0.4 — Cloud build + phone-only test handoff

## Status of this document’s environment

GitHub Actions was **authored** here. It has **not** been executed in this sandbox (no GitHub runner, no NDK). Treat build results as **NOT TESTED** until a green Actions run exists.

---

## 1. Get the debug APK (no PC required)

1. Open the repository on github.com in the phone browser.
2. Go to **Actions** → workflow **Android Build (arm64 native)**.
3. Open the latest successful run (green check).
4. Scroll to **Artifacts** → download **flint-launcher-debug-apk**.
5. The download is a zip; extract the `.apk` with the Files app or ZArchiver.

If the workflow is red, open **flint-build-logs** and read `native-build.log` / `app-build.log`. Do not install a failed build.

---

## 2. Install on itel A665L (Android 13)

1. Settings → Security → allow **Install unknown apps** for your browser/Files.
2. Open the APK → Install.
3. If Play Protect blocks it, choose Install anyway (debug builds are unsigned for Play).

---

## 3. Import a trusted ARM64 mobile JRE

Flint does **not** ship a JRE. You must supply a **mobile/Android aarch64** OpenJDK tarball (`.tar.gz`).

### Runtime acquisition (current reality)

| Candidate | Downloadable? | Notes |
|-----------|---------------|-------|
| `PojavLauncherTeam/android-openjdk-build-multiarch` releases | Partial / aged | Repo **archived May 2025**. Old release assets may still download; Actions artifacts expire. |
| GitHub Actions `jre17-pojav` / `jre21-pojav` | Login + short TTL | Not a stable public URL; SHA-256 must be measured after download. |
| Desktop Linux OpenJDK | **Do not use** | Wrong ABI / layout. |

**BLOCKED for automated fetch:** no stable public URL + published checksum is hardcoded in Flint.

**Manual route:**
1. On any machine (or Termux with care), obtain a known-good `jre17-*-aarch64*.tar.gz` (or successor).
2. `sha256sum file.tar.gz` → write the hash down.
3. Copy the file to the phone (`/sdcard/Download/`).
4. In Flint (debug diagnostics when available) or a future import UI, call `MobileJreManager.importLocalArchive(...)`.
5. Confirm `verified == true` (requires `libjli.so` + `libjvm.so`).

License: OpenJDK **GPLv2 + Classpath Exception**. Only use binaries you may redistribute/use.

---

## 4. Run MinimalJvmProbe

Until an in-app “Spike diagnostics” screen is wired, use logcat via a free app (e.g. **MatLog**) or a one-time Termux+shizuku workflow:

1. Ensure JRE is imported and verified.
2. Trigger the probe entry point (debug menu / instrumentation when present).
3. Look for log tag `FlintNative` and line `FLINT_JVM_OK`.
4. Confirm launch exit code **0**.

### Target evidence block (fill on device)

```
device: itel A665L
android: 13
apk_sha256: 
jre_origin: 
jre_sha256: 
nativeLoaded: 
jreLoadOk: 
launchExitCode: 
stdout_contains_FLINT_JVM_OK: yes/no
```

---

## 5. Labels

| Gate | Until proven |
|------|----------------|
| GitHub Actions build | NOT TESTED (workflow file present) |
| APK artifact | NOT TESTED |
| libflint_native.so in APK | NOT TESTED (workflow fails if missing once run) |
| Runtime archive | BLOCKED (no stable public artifact locked in tree) |
| Device import | NOT TESTED |
| JNI/JLI execution | NOT TESTED |
| FLINT_JVM_OK | NOT TESTED |
| READY_TO_PLAY | **Do not set** |
| Minecraft | **Not claimed** |
