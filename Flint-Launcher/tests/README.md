# Device and integration tests

Unit tests live next to their module (`<module>/src/test`). This folder holds the plan and results for tests that need a real device.

## Launch matrix (must be recorded, not assumed)

| Date | Device | Android | SoC/GPU | Minecraft | Loader | Java | Renderer | Result | Notes |
|---|---|---|---|---|---|---|---|---|---|

A row is added for every manual run. A Minecraft version or device is described as supported only if it has a passing row.

## Scenarios

1. Fresh install, vanilla release, reaches main menu and loads a world.
2. Kill the app during a download, reopen, confirm resume without re-downloading verified files.
3. Corrupt a library file, launch, confirm detection and repair.
4. Low-storage condition: confirm a clear error before downloading.
5. Fabric and Forge/NeoForge install and launch with one mod.
6. Memory and thermal behaviour over a 30-minute session.
7. Crash handling: force a game crash, confirm the log is captured and exportable.
