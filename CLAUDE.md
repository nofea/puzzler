# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Puzzler is a match-3 Android game (Kotlin, Jetpack Compose, single `app` module, package `com.sheen.puzzler`). `docs/SPEC.md` is the authoritative spec for game rules, scoring, level design and signing. Keep it, `README.md` and the code in sync when rules change.

## Environment and commands

The toolchain comes from devenv (`devenv.nix`): JDK 17, Android SDK platform 35, build-tools 35.0.0. The system JDK is too new for AGP, so run Gradle inside `devenv shell` (or via direnv's `.envrc`). The SDK is in the read-only Nix store, so AGP can't download other SDK components. `buildToolsVersion` is pinned to match.

| Task | devenv script | Gradle equivalent |
|---|---|---|
| Unit tests (engine + levels) | `unit-test` | `./gradlew :app:testDebugUnitTest` |
| Single test class / method | | `./gradlew :app:testDebugUnitTest --tests 'com.sheen.puzzler.engine.SpecialsTest'` (append `.methodName`, or use `*pattern*`) |
| Debug APK | `build-debug` | `./gradlew :app:assembleDebug` |
| Signed release APK (R8) | `build-release` | `./gradlew :app:assembleRelease` |
| UI tests (device/emulator) | `ui-test` | `./gradlew :app:connectedDebugAndroidTest` |

- UI tests need a device in `adb devices`. For an emulator, use `devenv --profile emulator shell`, then `run-emulator` (headless API 35 AVD `puzzler-api35`; add `--window` to watch it).
- `assembleRelease` fails on purpose when no signing key is configured. The key comes from `keystore.properties` at the repo root (gitignored, written by `gen-keystore`) or from the `PUZZLER_KEYSTORE_FILE`, `PUZZLER_KEYSTORE_PASSWORD`, `PUZZLER_KEY_ALIAS` and `PUZZLER_KEY_PASSWORD` env vars. Never commit keystores or `keystore.properties`.
- `verify-apk` checks the release signature, and `install-apk` runs `adb install -r` with it.

## Architecture

The core split: `engine/` is **pure Kotlin with no Android imports**, so all game rules are covered by fast JVM unit tests. Keep Android dependencies out of it.

- **`engine/`**: `GameEngine.swap(a, b)` runs a whole move synchronously and returns a `MoveResult` holding an ordered list of `Phase`s (SWAP, SWAP_BACK, CLEAR, FALL, SHUFFLE). Each phase carries a `BoardSnapshot` of the board after that step, plus the cascade index and score delta. The engine owns the score and move count but has **no clock**, so the caller enforces time limits.
  - A move works like this: swap → optional special+special or color-bomb combo (`comboBlast`) → `cascadeLoop` (`MatchFinder.findGroups` → create specials → `Resolver.clear` → `Gravity.settle`, repeated until stable, with a multiplier that rises each cascade) → `Shuffler` if no move is left (`MoveFinder.hasMove`).
  - `Resolver.clear` is the one place where clears happen. It chains special blasts through a queue (each piece id fires once), unlocks locked pieces instead of removing them, clears jelly, and damages stones next to matched cells or inside a blast.
  - `Piece.id` is unique per board, so the UI can match pieces across snapshots to animate them.
  - The engine's RNG comes from a seeded `Random` passed to `Board`, so tests and levels are deterministic.
- **`level/`**: `LevelDef` (kotlinx.serialization) is loaded from `app/src/main/assets/levels.json`. Layouts are ASCII grids: `.` cell, `#` hole, `J` jelly, `S`/`T` stone with 1/2 hp, `L` locked. `LevelDef.newEngine(seed)` builds the board and engine. The pass score is always 100 (`PASS_SCORE`). Levels can be tuned in the JSON without code changes.
- **`ui/`**: `GameViewModel` owns the engine and a timer coroutine. On a swipe it calls `engine.swap` and then plays the phases back one at a time, with a `delay(durationOf(kind))` between them, publishing each snapshot to `GameUiState`. `BoardCanvas` draws the board and gems on a Compose Canvas and handles drag-to-swap. Navigation Compose connects `LevelMapScreen`, `GameScreen` and `SettingsScreen` in a single `MainActivity`.
- **`data/`**: `ProgressRepository` (Preferences DataStore) stores unlocked levels, the best score and stars per level, and the settings.
- **`fx/`**: `SoundPlayer` synthesizes PCM in code with `AudioTrack` (there are no audio asset files), and `Haptics`.

## Tests

- Engine tests build boards with the helpers in `app/src/test/.../engine/TestBoards.kt`. `testBoard(marks = ...)` gives a board with no runs, using background colors 0–3, and paints pattern cells with color `P` (5). `engineFor(board)` fails if the engine reshuffled the prepared board.
- `LevelsTest` checks that every level starts calm and playable, that the same seed gives the same board, and runs a **greedy-bot playability check** on each level. When you change scoring, specials or `levels.json`, expect this test to catch balance regressions. The star thresholds in SPEC.md §3 were tuned with these bots.
