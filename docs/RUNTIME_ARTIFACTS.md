# Mobile OpenJDK runtime artifacts — research log (Spike 0.4)

## Upstream

- Project: https://github.com/PojavLauncherTeam/android-openjdk-build-multiarch  
- Status: **Archived 19 May 2025** (read-only).  
- Purpose: Build scripts for Android/iOS mobile OpenJDK used by PojavLauncher.  
- License of OpenJDK builds: **GPLv2 with Classpath Exception**.

## Artifact form

Historically packaged as architecture-split tarballs / CI zips named like:

- `jre8-pojav`
- `jre17-pojav`
- `jre21-pojav`

Consumed by Pojav’s own CI via `dawidd6/action-download-artifact` from branches `buildjre8` and `buildjre17-21`.

## SHA-256

**Not recorded in this repository.** Checksums must be computed from a file you actually downloaded:

```bash
sha256sum jre17-arm64.tar.gz
```

Do not invent or copy unverified hashes from chat logs.

## Downloadability (as of research in this spike)

| Source | Still downloadable? | ARM64 Android? | Use in Flint |
|--------|---------------------|----------------|--------------|
| GitHub release `jre17-ec28559` assets | May still be present; verify in browser | Intended Android multi-arch | Only after you verify file + hash |
| Actions artifacts on archived repo | Often expired / login required | Yes when named aarch64 | Unreliable for CI URL |
| Desktop Adoptium/Temurin Linux aarch64 | Yes | **No** (not mobile layout / not Android-linked) | **Forbidden** |

## Conclusion for Flint

**Runtime archive: BLOCKED** for automatic download.  
Supported path: `MobileJreManager.importLocalArchive(file, id, major, expectedSha256?)`.
