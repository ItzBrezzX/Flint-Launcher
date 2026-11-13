# Contributing

1. Read `docs/ARCHITECTURE.md`. Keep logic in the JVM modules; keep Android modules thin.
2. One focused change per pull request, with tests for new logic.
3. Run `gradle test lintDebug` before opening a PR.
4. Do not claim a feature or device is supported without a recorded passing run in `tests/README.md`.
5. No secrets, keystores, API keys or copied proprietary Minecraft assets in commits.
6. New third-party code needs its licence checked and listed before it is merged.
