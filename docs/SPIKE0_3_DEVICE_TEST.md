# Spike 0.3 — Device test procedure (itel A665L)

**Environment of this document author:** Linux sandbox without Android SDK/NDK and without the physical device.  
All device rows below are **NOT TESTED** here. Run these steps on a machine with Android SDK + the phone.

## Definition of done

A genuine ARM64 mobile OpenJDK executes `FlintJvmProbe` via Flint’s `nativeLoadJre` → `nativeLaunchJvm` and the process exit code is **0**. stdout contains `FLINT_JVM_OK`.

---

## Phase 1 — Build `:native` for arm64-v8a

### Prerequisites
- JDK 17+
- Android SDK platform 35
- NDK (CMake 3.22.1+, NDK 25+ recommended)
- Gradle wrapper generated: `gradle wrapper --gradle-version 8.9`

### Commands
```bash
cd Flint-Launcher
# one-time if gradlew missing
gradle wrapper --gradle-version 8.9

./gradlew :native:assembleDebug --info
# or full app
./gradlew :app:assembleDebug
```

### Verify .so is packaged
```bash
# after assemble
find . -name "libflint_native.so"
unzip -l app/build/outputs/apk/debug/*.apk | grep libflint_native
```

| Check | Expected | Record |
|-------|----------|--------|
| CMake configures | no error | |
| `libflint_native.so` exists under `arm64-v8a` | yes | |
| APK contains `lib/arm64-v8a/libflint_native.so` | yes | |
| `nm -D libflint_native.so \| grep nativeLoadJre` | symbol present | |

Label: **PASS / FAIL / BLOCKED**

---

## Phase 2 — Obtain a genuine mobile OpenJDK (ARM64)

Do **not** use a desktop Linux JRE.

### Provenance options
1. Build from source using a maintained fork of `android-openjdk-build-multiarch` (original repo archived May 2025).
2. Extract the JRE that ships inside a current PojavLauncher / Amethyst / successor APK (only if their license allows your use).
3. CI artifact from a fork you control — record the Actions run URL.

### Record
```
Origin URL or build commit: _______________
Java major: _______________
File name: _______________
SHA-256:   sha256sum the-file.tar.gz
License:   GPLv2 + Classpath Exception (OpenJDK) — confirm
```

### Import on device
```kotlin
val mgr = MobileJreManager(File(context.filesDir, "runtimes"))
val result = mgr.importLocalArchive(
    archive = File("/sdcard/Download/jre17-arm64.tar.gz"),
    id = "jre17-arm64",
    major = 17,
    expectedSha256 = "<paste real sha256 or null>",
)
// result.getOrThrow().verified must be true
```

### Extraction safety (host CI)
```bash
./gradlew :runtime:test --tests com.flintlauncher.android.runtime.SafeTarGzExtractorTest
```

| Check | Label |
|-------|-------|
| Traversal rejected | |
| Absolute path rejected | |
| Safe extract writes libjli path | |

---

## Phase 3 — Minimal Java program through JNI/JLI

### Source
`MinimalJvmProbe.writeProbeSource(dir)` writes `FlintJvmProbe.java` that prints:
```
FLINT_JVM_OK
java.version=...
os.arch=...
user.dir=...
```

### Compile on device (using the imported JRE’s javac if present) or host cross-tools
```bash
# Example if bin/javac exists inside imported JRE (often stripped in JRE-only packs)
$JRE_HOME/bin/javac -d /data/local/tmp/flint-probe FlintJvmProbe.java
```

### Execute through Flint bridge (instrumentation / debug button)
```kotlin
val report = MinimalJvmProbe.run(runtimeInfo, classpathDir = File("/data/local/tmp/flint-probe"))
// report.passed == true
// report.launchExitCode == 0
```

### Capture
```
nativeLoaded: 
jreLoadOk: 
launchExitCode: 
stdout: 
stderr: 
notes: 
device: itel A665L
android: 13
os.arch from probe: 
```

| Result | Label |
|--------|-------|
| exit code 0 + FLINT_JVM_OK | **PASS** only if seen on device |
| dlopen failure | FAIL — paste `logcat` |
| JLI_Launch non-zero | FAIL — paste code |
| Not run | **NOT TESTED** |

---

## Phase 4 — If JVM startup fails

Check in order:
1. `logcat | grep -E 'FlintNative|linker|dlopen'`
2. `readelf -d libjli.so | grep NEEDED` — missing `libc++_shared` / `liblog`?
3. File executable bits on extracted `.so` files
4. `LD_LIBRARY_PATH` / `java.library.path` pointing at `jre/lib` and `jre/lib/server`
5. Android 13 linker namespace — libraries under app `files/` may need `System.load` before `dlopen` of dependents
6. argv[0] and `-Duser.dir`

---

## Strict labels for the final report

| Item | Allowed labels |
|------|----------------|
| `:native` arm64 build | PASS / FAIL / BLOCKED / NOT TESTED |
| APK contains .so | PASS / FAIL / NOT TESTED |
| Runtime archive provenance | documented / BLOCKED |
| SHA-256 recorded | yes / no |
| importLocalArchive verified | PASS / FAIL / NOT TESTED |
| Extraction unit tests | PASS / FAIL / NOT TESTED |
| JVM exit 0 + FLINT_JVM_OK on itel A665L | PASS / FAIL / **NOT TESTED** |
| READY_TO_PLAY | **Do not set** |

Spike 0.3 is **done** only when the last JVM row is **PASS** on the target device.
