# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Puzzler is a match-3 Android game (Kotlin, Jetpack Compose, single `app` module, package `com.sheen.puzzler`). `docs/SPEC.md` is the authoritative spec for the game and its features: rules, scoring, levels, screens and the app icon. Keep it, `README.md` and the code in sync when rules change. The spec is about the game only. Build, CI, signing, architecture and workflow details (including commit steps) belong in `README.md` or this file, never in the spec.

## Environment and commands

The toolchain comes from devenv (`devenv.nix`): JDK 21 (supplied by the devenv Android module), Android SDK platform 35, build-tools 35.0.0. The system JDK is too new for AGP, so run Gradle inside `devenv shell` (or via direnv's `.envrc`). The SDK is in the read-only Nix store, so AGP can't download other SDK components. `buildToolsVersion` is pinned to match.

| Task | devenv script | Gradle equivalent |
|---|---|---|
| Unit tests (engine + levels) | `unit-test` | `./gradlew :app:testDebugUnitTest` |
| Single test class / method | | `./gradlew :app:testDebugUnitTest --tests 'com.sheen.puzzler.engine.SpecialsTest'` (append `.methodName`, or use `*pattern*`) |
| Debug APK | `build-debug` | `./gradlew :app:assembleDebug` |
| Signed release APK (R8) | `build-release` | `./gradlew :app:assembleRelease` |
| UI tests (device/emulator) | `ui-test` | `./gradlew :app:connectedDebugAndroidTest` |
| Lint (ktlint, detekt, Android Lint) | `lint` | `./gradlew ktlintCheck detekt :app:lintDebug` |
| Auto-format (ktlint) | `format` | `./gradlew ktlintFormat` |
| Secrets and mobile security scan | `security-scan` | gitleaks + mobsfscan (see `devenv.nix`) |

- UI tests need a device in `adb devices`. For an emulator, use `devenv --profile emulator shell`, then `run-emulator` (headless API 35 AVD `puzzler-api35`; add `--window` to watch it).
- `assembleRelease` fails on purpose when no signing key is configured. The key comes from `keystore.properties` at the repo root (gitignored, written by `gen-keystore`) or from the `PUZZLER_KEYSTORE_FILE`, `PUZZLER_KEYSTORE_PASSWORD`, `PUZZLER_KEY_ALIAS` and `PUZZLER_KEY_PASSWORD` env vars. Never commit keystores or `keystore.properties`.
- `verify-apk` checks the release signature, and `install-apk` runs `adb install -r` with it.
- Linting is zero-tolerance in CI: new code must pass `lint` with no findings. Run `format` first for style. If a rule really doesn't fit, change it in `config/detekt.yml`, `app/lint.xml` or `.mobsf` with a comment giving the reason. Never use blanket `@Suppress` or a baseline file.
- CI (`.github/workflows/ci.yml`) runs on every push and on PRs to `main`. The `build` job runs the unit tests and the debug build. The `ui-test` job runs after it and executes `connectedDebugAndroidTest` on an API 35 emulator (`reactivecircus/android-emulator-runner`, AVD snapshot cached). Both use the runner's own Android SDK and Temurin 21, not devenv.
- Releases (`release.yml`) are tagged `vX.Y.Z` and run after CI passes on `main`. `feat:` commits bump MINOR and `fix:` commits bump PATCH. MAJOR is only bumped by running the workflow by hand. Commit messages and PR titles must therefore follow Conventional Commits (`pr-title.yml` checks titles). The app version comes from `-PpuzzlerVersion`, else the latest tag, else 0.0.0 (see `app/build.gradle.kts`). The bump logic lives in `scripts/next-version.sh`.

## Architecture

The core split: `engine/` is **pure Kotlin with no Android imports**, so all game rules are covered by fast JVM unit tests. Keep Android dependencies out of it.

- **`engine/`**: `GameEngine.swap(a, b)` runs a whole move synchronously and returns a `MoveResult` holding an ordered list of `Phase`s (SWAP, SWAP_BACK, CLEAR, FALL, SHUFFLE). Each phase carries a `BoardSnapshot` of the board after that step, plus the cascade index and score delta. The engine owns the score and move count but has **no clock**, so the caller enforces time limits.
  - A move works like this: swap → optional special+special or color-bomb combo (`comboBlast`) → `cascadeLoop` (`MatchFinder.findGroups` → create specials → `Resolver.clear` → `Gravity.settle`, repeated until stable, with a multiplier that rises each cascade) → `Shuffler` if no move is left (`MoveFinder.hasMove`).
  - `Resolver.clear` is the one place where clears happen. It chains special blasts through a queue (each piece id fires once), unlocks locked pieces instead of removing them, clears jelly, and damages stones next to matched cells or inside a blast.
  - `Piece.id` is unique per board, so the UI can match pieces across snapshots to animate them.
  - The engine's RNG comes from a seeded `Random` passed to `Board`, so tests and levels are deterministic.
- **`level/`**: `LevelDef` (kotlinx.serialization) is loaded from `app/src/main/assets/levels.json`. Layouts are ASCII grids: `.` cell, `#` hole, `J` jelly, `S`/`T` stone with 1/2 hp, `L` locked. `LevelDef.newEngine(seed)` builds the board and engine. The pass score is always 100 (`PASS_SCORE`). Levels can be tuned in the JSON without code changes.
- **`ui/`**: `GameViewModel` owns the engine and a timer coroutine. On a swipe it calls `engine.swap` and then plays the phases back one at a time, with a `delay(durationOf(kind))` between them, publishing each snapshot to `GameUiState`. `BoardCanvas` draws the board and items on a Compose Canvas and handles drag-to-swap. Navigation Compose connects `LevelMapScreen`, `GameScreen` and `SettingsScreen` in a single `MainActivity`.
  - Pieces are bitmap "items" (fries, burger, pizza, plant, soda can, chocolate; color indices 0–5). `ItemDrawing.kt` loads them and draws items and the color bomb, shared by `BoardCanvas` and the launch intro.
  - Item art comes from Noto Emoji SVGs in `art/items/`. Run `render-items` (`scripts/render-items.py`) to recolor them and regenerate `drawable-nodpi/item_*.webp` and the `mipmap-*dpi` icon layers; change the recolor tables in the script, never the generated files. The icon, `splash_orb.xml` and `ColorBomb` share proportions, so change them together.
  - Tutorials (`ui/tutorial/`): `Tutorials.forLevel` builds the L1/L6 step scripts and picks guided moves by simulating on a board copy. It's pure Kotlin, unit-tested in `TutorialsTest`. `GameViewModel` owns the step state, gates input to the guided swap, and holds the clock while a step shows. `TutorialOverlay` spotlights elements registered with `Modifier.tutorialTarget` and handles the guided swipe itself.
  - How to play (`ui/rules/`): every card's demo is a real `GameEngine` run on a 5×5 board from `Rules.cards`. `DemoBoardsTest` checks each board and that the demo shows what its caption says. When you change the rules, update the cards and that test.
  - UI tests mark the L1/L6 tutorials as seen in `@Before`, so they play the board directly. The tutorial tests reset that themselves.
  - Launch: `MainActivity` installs the androidx splash screen, then hands its orb position to `IntroOverlay`. The intro is drawn over the `NavHost` and plays once per process (`PuzzlerApplication.introShown`). It is skipped when the system's animator scale is 0, which is how the UI tests and CI run, so tests never see it.
- **`data/`**: `ProgressRepository` (Preferences DataStore) stores unlocked levels, the best score and stars per level, and the settings.
- **`fx/`**: `SoundPlayer` synthesizes PCM in code with `AudioTrack` (there are no audio asset files), and `Haptics`.

## Tests

- Engine tests build boards with the helpers in `app/src/test/.../engine/TestBoards.kt`. `testBoard(marks = ...)` gives a board with no runs, using background colors 0–3, and paints pattern cells with color `P` (5). `engineFor(board)` fails if the engine reshuffled the prepared board.
- `LevelsTest` checks that every level starts calm and playable, that the same seed gives the same board, and runs a **greedy-bot playability check** on each level. When you change scoring, specials or `levels.json`, expect this test to catch balance regressions. The star thresholds in SPEC.md §3 were tuned with these bots.
