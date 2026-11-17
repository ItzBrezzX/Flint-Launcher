# Snapshot status: INCOMPLETE — touch HUD + native packaging update

Base: uploaded `Flint-Launcher-main (5).zip`, merged with `Flint-Launcher-latest-host-patch.zip`.

## Included in this source update

- Reference-style in-game touch HUD: top toolbar (`DEBUG`, `CHAT`, `KEYBOARD`, `TAB`, `MOUSE`, `3RD`) and the lower-left 3×3 action pad.
- Touch HUD settings gear with move mode, add custom button, select/rebind key or mouse action, remove custom button, hide/show controls, and reset layout.
- Dragging preserves the point where the user grabbed a button; button centers are clamped so controls remain inside the visible screen.
- HUD positions and custom button bindings are saved in app-private SharedPreferences using normalized screen coordinates.
- Invisible Android keyboard input view forwards committed text and key presses to the native GLFW input queue.
- Android APK native packaging uses legacy extraction so runtime libraries can be resolved from `ApplicationInfo.nativeLibraryDir`, as expected by the launcher code.
- Host native bridge remains merged: native library directory, display metrics and performance-core preference.

## Renderer investigation

The inspected `app-debug(1).apk` contains `lib/arm64-v8a/libGL.so` and the matching GLFW/LWJGL libraries. Its `libGL.so` is a valid AArch64 ELF with GL4ES exports. The screenshot's missing-renderer message is therefore consistent with a library lookup/extraction-path problem, not proof that the APK archive has no GL4ES entry. The packaging change above addresses the `nativeLibraryDir` extraction assumption, but it still needs a newly built APK and on-device validation.

## Verification status

- Source and APK archive inspection: completed.
- Build: not completed. `./gradlew :app:assembleDebug --offline` could not start the build because Gradle 8.9 was not cached and the environment could not resolve `services.gradle.org`; no Android SDK is installed in this environment.
- Device verification: not performed. Minecraft title screen, world rendering, keyboard, touch bindings and layout persistence remain unverified on the user's phone.

READY TO PLAY: no — do not treat this source update as a verified APK.
