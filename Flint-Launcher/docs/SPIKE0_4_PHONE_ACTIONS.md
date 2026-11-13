# Run Android Build from an Android phone (GitHub website)

Prerequisite: the Flint repo is on GitHub and you can open it while logged in.

1. Open **github.com** → your Flint repository.
2. Tap **Actions**.
3. In the left workflow list, tap **Android Build (arm64 native)**.
4. Tap **Run workflow** (uses `workflow_dispatch`).
5. Choose branch **main** (or the branch that contains this workflow) → **Run workflow**.
6. Wait until the run finishes.
   - Green = success.
   - Red = open the run → failed step → logs (also download **flint-build-logs**).
7. On a green run, scroll to **Artifacts**.
8. Download **flint-launcher-debug-apk**.
9. Unzip the artifact → install the `.apk` (allow unknown apps for Files/browser).

Do not treat a green cloud build as proof that the JVM probe passed on the phone.
