# Puzzler — Specification

Puzzler is a Candy‑Crush‑style match‑3 game for Android. It has 10 levels, each harder than the last. A level needs 100 points to pass, and more complex patterns score more points.

## 1. Product decisions
| Topic | Decision |
|---|---|
| Name | **Puzzler** |
| Pieces | Gems with a distinct shape and color per type (colorblind‑friendly). The color bomb is a near‑black orb ringed by the six mini gem shapes, with a gold sparkle core (same design as the app icon) |
| App icon | The color bomb on a purple radial glow, with a monochrome version for themed icons. See §5 |
| Pass rule | ≥100 points before the limits run out. Stars: 1★ = 100; the 2★/3★ thresholds rise per level |
| Limits | L1–5: move limit. L6–10: move limit **and** timer; either running out ends the level |
| Patterns | Line 3/4/5, L/T shapes, cascades, special pieces |
| Obstacles | Jelly, Stone (1–2 hits), Locked piece |
| Difficulty knobs | moves↓, time↓, colors 4→6, obstacles, irregular boards, higher star thresholds |
| Progress | Saved on the device: unlocked levels, best score and stars per level, sound and vibration settings |
| Audio/FX | Short synthesized sounds and haptics; sound and vibration can each be turned off in Settings |

## 2. Game rules
- **Board**: 8×8 grid of cells; a cell can be a hole (not part of the board). Swap two orthogonally adjacent pieces by dragging, or by tapping one and then a neighbor. A swap that produces no match is reverted and costs no move.
- **Match detection**: find all horizontal/vertical runs ≥3 of the same color; runs sharing a cell merge into one group. Classify each group: Line5 > L/T (group spans both axes, ≥5 cells) > Line4 > Line3.
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

  Points are rounded down per step. A special is created at the swapped cell (or at the group's center during cascades).
- **Specials**: Striped clears its row/column; Bomb clears 3×3; a Color bomb swapped with a piece clears every piece of that color. Special+special swaps: striped+striped = row+column cross; striped+bomb = 3 rows + 3 cols; bomb+bomb = 5×5; color bomb+X = turn every piece of X's color into X's special type, then fire them all; color bomb+color bomb = clear the board. Specials caught in another clear fire too (chain).
- **Obstacles**:
  - *Jelly*: a layer under a piece; cleared when that cell's piece is cleared.
  - *Stone*: occupies a cell and never moves; loses 1 hit when an adjacent cell is matched or a special hits it. HP 1 or 2.
  - *Locked*: the piece can't be swapped and doesn't fall; a match or special that includes it removes the lock (the piece stays put and becomes normal).
- **Gravity/refill**: pieces fall straight down; cells shadowed by stones or holes are filled by a diagonal slide from neighboring columns; new pieces spawn at the top of each column. Match → clear → fall repeats until the board is stable (each loop is one cascade step).
- **Generation**: the starting board has no ready‑made matches. Each level has a fixed seed, so it always starts with the same board.
- **Dead board**: if no valid move exists, the movable pieces are shuffled (obstacles stay put) until at least one move exists and there are no instant matches. This costs no move, and the game shows "Shuffling…".
- **End**: once the score reaches 100, the level can still be played until moves or time run out, or the player can tap "Finish" to bank their stars. The win screen shows stars and best score; the fail screen offers a retry.

## 3. Levels
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

Balance: cascades make 100 points come quickly. A random player passes about 100% of L1 attempts and about 30% of L10 attempts, while a greedy player passes about 80–100% of attempts on every level. 2★ is set near the greedy player's 30th‑percentile score and 3★ near its 75th.

## 4. Screens and feedback
- **Level map**: the 10 levels as a winding path of nodes. Locked levels show a lock, cleared levels show their stars (a gold ring at 3★), timed levels show their time limit, and the total stars are shown at the top. Level 1 starts unlocked; clearing a level unlocks the next.
- **Game screen**:
  - **HUD**: level number, moves left, and time left on timed levels (both turn red when running low). It also shows the score against the 100 target, the stars earned so far, and a progress bar that runs to the 3★ score.
  - **Board**: swaps, clears, falls and refills are animated step by step. A banner calls out patterns and cascades (e.g. "Line of 5!", "Cascade x3!") with the points earned.
  - **Buttons**: "Finish" (enabled once the level is passed) and "Quit".
- **Timer**: pauses while the app is in the background.
- **Result dialog**: shows the title ("Level N cleared!", "Out of moves" or "Out of time"), stars, score and best score. A win offers Next level / Replay / Map; a loss offers Retry / Map.
- **Settings**: sound and vibration toggles, and "Reset progress" (with confirmation), which locks everything except level 1 and clears stars and best scores.
- **Sound and haptics**: distinct sounds for swap, invalid swap, special, shuffle, win and fail. The match chime rises in pitch with each cascade step.

## 5. App icon
The launcher icon is the game's **color bomb**, the one piece that is unique to Puzzler.

| Topic | Decision |
|---|---|
| Subject | Color bomb: one orb ringed by the six gems |
| Style | Flat and minimal: solid fills, no gradients on the subject |
| Orb | Near‑black `#140A26` |
| Ring | The six gems as mini versions of their in‑game shape and color (red circle, orange rounded square, yellow diamond, green triangle, blue hexagon, purple star), evenly spaced, starting at the top and going clockwise in that order |
| Center | Gold four‑point sparkle `#FFD34D` |
| Background | Purple radial glow: `#3A2470` at the center fading to `#1B1035` at the edges |
| Themed icon | Monochrome version: the orb silhouette with the gems and sparkle cut out |
| In‑game | The color bomb on the board uses the same design and proportions |

Proportions (on the 108‑unit adaptive icon canvas, everything inside the central 66‑unit safe zone):
- Orb radius 26.
- Gem centers on a ring of radius 0.6 × orb; each gem radius 0.19 × orb, with each shape's proportions matching the in‑game gems.
- Sparkle outer radius 0.23 × orb; inner radius 0.3 × outer.
