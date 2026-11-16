# Snapshot status: INCOMPLETE

Microsoft authentication remains paused.

## Verified in previous local session

- Gradle Wrapper 8.9 boots (`bash gradlew --version`).
- Wrapper JAR SHA-256: `498495120a03b9a6ab5d155f5de3c8f0d986a449153702fb80fc80e134484f17`.
- Local SDK/NDK install reached `:native` CMake configure + GL4ES git checkout for `arm64-v8a`.
- Local NDK build was killed by ~1.2 GiB RAM limit (environment), not classified as a source defect.

## Done in this snapshot

- `.github/workflows/android-build.yml` optimized for GitHub-hosted runners:
  - JDK 17, Gradle 8.9 via `./gradlew`
  - SDK platform 35, build-tools 35.0.0, NDK 27.0.12077973, CMake 3.22.1
  - Sequential stages: native → SO check → unit tests → app APK → APK SO check
  - `GRADLE_OPTS` caps workers/heap; `--no-daemon`
  - Fails if `libflint_native.so`, `libGL.so`, or `libglfw.so` missing for any ABI
  - Uploads APK + logs as artifacts (`actions/upload-artifact@v4`)

## Not done

- GitHub CLI is not authenticated here → nothing was committed, pushed, or run on Actions.
- No successful CI log, APK, or native ELF verification from CI.
- No Minecraft menu / world / input verification.

READY TO PLAY: no.
