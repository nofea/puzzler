# Puzzler — Specification

## Context
Greenfield repo (`/home/sheen/Documents/projectenv/puzzler`, only LICENSE + README). Goal: a Candy‑Crush‑style match‑3 Android game in Kotlin, 10 progressively harder levels, 100 points to pass a level, points scaled by pattern complexity, sideloaded as a properly signed release APK. Build environment via **devenv** (Nix is installed; devenv is not; system JDK 27 is too new for AGP, devenv supplies JDK 17 + Android SDK).

First implementation step: commit this spec as `docs/SPEC.md` so it lives with the code.

## 1. Product decisions (from grilling)
| Topic | Decision |
|---|---|
| App | **Puzzler**, `com.sheen.puzzler`, minSdk 29, target/compileSdk 35 |
| UI | Jetpack Compose, board drawn on Canvas, single Activity |
| Pieces | Vector gems drawn in code (distinct shape + color per type, colorblind‑friendly) |
| Pass rule | ≥100 points before limits run out. Stars: 1★=100, 2★/3★ thresholds rise per level |
| Limits | L1–5: move limit. L6–10: move limit **and** timer; either running out = fail |
| Patterns | Line 3/4/5, L/T shapes, cascades, special pieces |
| Obstacles | Jelly, Stone (1–2 hits), Locked piece |
| Difficulty knobs | moves↓, time↓, colors 4→6, obstacles, irregular boards, higher star thresholds |
| Persistence | DataStore: unlocked levels, best score + stars per level, mute setting |
| Audio/FX | Synthesized short sounds (no asset files) + haptics, mute toggle in settings |
| Testing | JUnit engine tests + Compose UI tests on emulator |
| Signing | Generated release keystore outside git, Gradle signingConfig from `keystore.properties`/env, APK Signature Scheme v3 (AGP omits v2 for minSdk ≥ 28), verified with `apksigner` |

## 2. Game rules
- **Board**: 8×8 grid of cells; a cell can be a hole (not part of board). Swap two orthogonally adjacent pieces; swap is reverted if it produces no match (no move consumed).
- **Match detection**: find all horizontal/vertical runs ≥3 of same color; runs sharing a cell merge into one group. Classify group: Line5 > L/T (group spans both axes, ≥5 cells) > Line4 > Line3.
- **Scoring** (base points per group, then cascade multiplier):

  | Pattern | Points | Creates |
  |---|---|---|
  | Line of 3 | 5 | – |
  | Line of 4 | 10 | Striped (row or column, per swap direction) |
  | L / T | 15 | Bomb (3×3 blast) |
  | Line of 5 | 25 | Color bomb |
  | Each piece cleared by a special | +1 | – |
  | Jelly cleared | +2 | – |
  | Stone destroyed | +3 | – |
  | Cascade step N (N=1 is the player's move) | ×(1 + 0.5·(N−1)) | – |

  Points are rounded down per step. A special is created at the swapped cell (or the group's center during cascades).
- **Specials**: Striped clears its row/column; Bomb clears 3×3; Color bomb swapped with a piece clears all of that color. Special+special swaps: striped+striped = row+column cross; striped+bomb = 3 rows + 3 cols; bomb+bomb = 5×5; color bomb+X = turn all of X's color into X's special type then fire; color bomb+color bomb = clear board. Specials caught in another clear fire too (chain).
- **Obstacles**:
  - *Jelly*: a layer under a piece; cleared when that cell's piece is cleared.
  - *Stone*: occupies a cell and never moves; loses 1 hit when an adjacent cell is matched or a special hits it. HP 1 or 2.
  - *Locked*: piece can't be swapped and doesn't fall; a match or special that includes it removes the lock (the piece stays put and becomes normal).
- **Gravity/refill**: pieces fall straight down; cells shadowed by stones or holes are filled by a diagonal slide from neighbor columns; new pieces spawn at the top of each column. Repeat match → clear → fall until stable (each loop = one cascade step).
- **Generation**: initial board and refills avoid pre‑made matches at start; RNG seeded per level (deterministic tests).
- **Dead board**: if no valid move exists, shuffle the movable pieces (keeping obstacles fixed) until at least one move exists and no instant matches (no move consumed, show "Shuffling…").
- **End**: when the score reaches 100, the level can still be played until moves/time run out, or tap "Finish" to bank stars. Win screen shows stars and best score; fail screen offers retry.

## 3. Levels (`app/src/main/assets/levels.json`, tunable without code changes)
| L | Colors | Moves | Time | Board / obstacles | 2★ / 3★ |
|---|---|---|---|---|---|
| 1 | 4 | 12 | – | full 8×8 | 1500 / 2250 |
| 2 | 5 | 12 | – | full | 400 / 650 |
| 3 | 5 | 10 | – | 8 jelly in center | 350 / 600 |
| 4 | 5 | 9 | – | jelly + 4 stones (1 hit) | 325 / 550 |
| 5 | 5 | 9 | – | corner holes, 6 locked | 275 / 525 |
| 6 | 5 | 9 | 60s | full, jelly | 300 / 550 |
| 7 | 6 | 9 | 50s | stones (2 hits) | 165 / 300 |
| 8 | 6 | 9 | 45s | cross‑shaped board, jelly | 140 / 225 |
| 9 | 6 | 8 | 40s | locks + stones + jelly | 155 / 285 |
| 10 | 6 | 7 | 35s | irregular board, all obstacles | 125 / 220 |

Move limits, timers and star thresholds were tuned with bots (2026‑10‑20). Cascades make 100 points come quickly, so a random player passes about 100% of L1 attempts and about 30% of L10 attempts, while a greedy player passes about 80–100% of attempts on every level. 2★ is set near the greedy player's 30th‑percentile score and 3★ near its 75th.

Layouts are ASCII grids in JSON (`.` cell, `#` hole, `J` jelly, `S`/`T` stone 1/2 hp, `L` locked). Includes a fixed seed. A playability test runs a greedy bot on each level to check it can reach 100 in most seeds.

## 4. Architecture
Single Gradle module `app`, packages under `com.sheen.puzzler`:
- `engine/` — **pure Kotlin, no Android imports**: `Board`, `Cell`, `Piece(color, special)`, `Obstacle`, `MatchFinder`, `PatternClassifier`, `Scorer`, `Gravity`, `SpecialResolver`, `MoveFinder`, `Shuffler`, `GameEngine.swap(a,b): MoveResult` returning an ordered list of `Step`s (Swap, Clear, Spawn special, Fall, Refill, Score) for animation.
- `level/` — `LevelDef` (kotlinx.serialization), `LevelRepository` (loads assets JSON).
- `data/` — `ProgressRepository` (Preferences DataStore).
- `ui/` — `MainActivity`, Navigation Compose: `LevelMapScreen` (10 nodes, locks, stars), `GameScreen` (HUD: score/target progress bar, moves, timer; `BoardCanvas` with drag‑to‑swap gestures; step animations via `Animatable`), `ResultDialog`, `SettingsScreen` (mute and haptics toggles).
- `ui/GameViewModel` — owns the engine, timer coroutine (pauses on lifecycle stop), consumes Steps sequentially for animations, then commits state.
- `fx/` — `SoundPlayer` (synthesized PCM via `AudioTrack` static buffers: match pitch rises with cascade step), `Haptics` (`VibratorManager`/`HapticFeedback`).

## 5. Build env (devenv)
- Install: `nix profile install nixpkgs#devenv`.
- `devenv.yaml`: nixpkgs input, `allowUnfree: true`, Android SDK license acceptance.
- `devenv.nix`: `android.enable = true` (platforms 35, build‑tools 35, emulator + system image for UI tests), `languages.java.jdk.package = pkgs.jdk17`, `languages.kotlin.enable`, scripts: `build-debug`, `build-release`, `gen-keystore`, `verify-apk`, `run-emulator`, `install-apk`.
- Gradle wrapper (Gradle 8.x) + version catalog `gradle/libs.versions.toml` (AGP 8.x, Kotlin 2.x + Compose compiler plugin, Compose BOM, DataStore, kotlinx‑serialization, navigation‑compose). Exact stable versions pinned at implementation time.
- `.gitignore`: `.devenv/`, `.direnv/`, `build/`, `local.properties`, `keystore.properties`, `*.jks`, `*.keystore`.

## 6. Signing
- `scripts/gen-keystore.sh`: `keytool -genkeypair` RSA 4096, validity 10000 days, writes `~/.android-keys/puzzler-release.jks` (outside the repo, chmod 600), prompts for passwords, and writes `keystore.properties` (gitignored) with `storeFile`, `storePassword`, `keyAlias`, `keyPassword`.
- `app/build.gradle.kts`: `signingConfigs.release` reads `keystore.properties`, falling back to env vars `PUZZLER_KEYSTORE_FILE/PASSWORD/KEY_ALIAS/KEY_PASSWORD`. The release build fails clearly if neither exists. v1 off, v2/v3 enabled (AGP emits only v3 at minSdk 29). Release build: R8 minify + resource shrinking.
- `scripts/verify-apk.sh`: `apksigner verify --verbose --print-certs` and prints the SHA‑256 cert fingerprint.
- README section: back up the keystore. Losing it means future updates can't install over the existing app.

## 7. Files to create (representative)
`devenv.nix`, `devenv.yaml`, `.envrc` (optional direnv), `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/proguard-rules.pro`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/com/sheen/puzzler/{engine,level,data,ui,fx}/…`, `app/src/main/assets/levels.json`, `app/src/test/…/engine/*Test.kt`, `app/src/androidTest/…/ui/*Test.kt`, `scripts/*.sh`, `docs/SPEC.md`, `README.md`.

## 8. Verification
1. `devenv shell` → `./gradlew :app:testDebugUnitTest`. Engine tests cover: line3/4/5, L, T, merged groups, cascade multiplier math, special creation and firing, special+special combos, jelly/stone/lock behavior, gravity with holes/stones (diagonal fill), no‑match swap revert, dead‑board shuffle, no initial matches, level JSON parsing, greedy‑bot playability per level.
2. `run-emulator` (headless API 35 AVD) → `./gradlew :app:connectedDebugAndroidTest`. Compose tests: level map shows L1 unlocked/L2 locked; a test level with a forced seed where a scripted swap scores and wins, then unlocks L2; move counter decrements; fail dialog appears when moves hit 0; progress survives activity recreation.
3. `gen-keystore` → `./gradlew :app:assembleRelease` → `verify-apk` shows v3 verified and the cert fingerprint.
4. `adb install -r app/build/outputs/apk/release/app-release.apk` on a phone (or the emulator) and play L1–L2 manually: animations, sounds, haptics, mute toggle, timer on L6.
5. Commit on branch `init_vib`.
