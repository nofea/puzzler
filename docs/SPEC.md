# Puzzler — Specification

Puzzler is a Candy‑Crush‑style match‑3 game for Android. It has 10 levels, each harder than the last. A level needs 100 points to pass, and more complex patterns score more points.

## 1. Product decisions
| Topic | Decision |
|---|---|
| Name | **Puzzler** |
| Pieces | Gems with a distinct shape and color per type (colorblind‑friendly). The color bomb is a near‑black orb ringed by the six mini gem shapes, with a gold sparkle core (same design as the app icon) |
| App icon | The color bomb on a purple radial glow, with a monochrome version for themed icons. See §5 |
| Launch | Splash, then a ~2 s color‑bomb intro (assemble → detonate) on cold start; tap to skip. See §4 |
| Tutorials | A How to play page (from the map, Settings and in‑level "?") and guided in‑level tutorials in L1 and L6, shown once. See §6 |
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
- **Launch intro**: the app opens on a splash of the color bomb's orb and sparkle on the dark purple background. The intro then plays (about 2 s):
  1. **Assemble (0–0.8 s):** the six gems fly in from off‑screen and lock into their ring around the orb, one by one, with a tick as each lands.
  2. **Title (0.8 s):** the sparkle flashes, and "PUZZLER" (rounded display font, gold) fades in under the bomb, followed by the tagline "Match. Cascade. Detonate.".
  3. **Detonate (1.3 s):** the bomb detonates with a boom and a strong vibration, and the gems burst outward off‑screen.
  4. **Reveal (1.3–2.0 s):** a circle expanding from the bomb's center uncovers the level map.
  - Tapping during the intro jumps straight to the detonation.
  - The intro plays only when the app starts fresh, not when returning from the background.
  - It is skipped entirely when the phone's animations are turned off (accessibility).
  - Its sounds and vibration follow the Sound and Vibration settings.
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

## 6. Tutorials

### How to play page
Opened from "How to play" on the level map, the "How to play" row in Settings, or the "?" next to the level number in a level. While it's open from a level, that level's timer is paused.

The page is six swipeable cards, with page dots, Previous/Next, and Done on the last card. Each card has a looping mini‑demo on a 5×5 board played by the real game rules. A fingertip shows each swipe, and a small Moves/Score readout counts along.

| Card | Explains | Demo(s) |
|---|---|---|
| Basics | Swap neighbors; match 3+ across or down; a non‑matching swap bounces back for free; gems fall and refill; a dead board shuffles for free | A swap bouncing back; three in a row clearing |
| Scoring | The points table (line of 3/4/5, L/T, special clears, jelly, stone) and cascade multipliers ×1, ×1.5, ×2 … | A cascade whose second match scores ×1.5 |
| Specials | Line of 4 → striped, L/T → bomb, line of 5 → color bomb; specials fire when matched or hit, so they chain | Each special being made, and a striped gem clearing its row |
| Combos | Every special + special swap, and color bomb + gem | Striped + bomb; color bomb + gem |
| Obstacles | Jelly, stones (dark ones take two hits), chained gems | One match that clears jelly, breaks a stone and frees a chained gem |
| Limits & stars | Moves, the timer on L6–10, either running out ends the level, 100 to pass, ★★/★★★, Finish | Three in a row using a move |

### In‑level tutorials
Level 1 and level 6 have a tutorial the first time they're played. It's remembered on the device, and "Reset progress" brings it back. The screen is dimmed except for a pulsing gold spotlight on what the step is about. A card next to the spotlight shows the step's title and text, a step counter, **Skip tutorial**, and **Next** (or **Got it** on the last step). Skip ends the tutorial and counts as seen.

- **Guided swap:** the two gems to swap are spotlighted, and a fingertip animation swipes between them. Only that swap is accepted; every other touch is ignored and costs nothing. The step ends once the swap has played out.
- **Level 1:**
  1. *Make a match* (guided swap that lines up 3): "Swipe to swap these two gems and line up 3 of a kind."
  2. *Score* (spotlight on the score, bar and stars): "Matches score points. Reach 100 to pass the level; score more for ★★ and ★★★."
  3. *Moves* (spotlight on Moves): "Every swap uses a move. When moves run out, the level ends." Free play follows.
  4. *Level passed!* When the score first reaches 100, the Finish button is spotlighted: "You've passed! Tap Finish to bank your stars, or keep playing for a higher score." Tapping Finish there finishes the level.
- **Level 6** (the timer doesn't start until the tutorial ends):
  1. *Timer* (spotlight on the clock): "This level has a timer. It starts when this tutorial ends."
  2. *Two limits* (spotlight on Moves): "Run out of moves or time and the level ends. 100 points still passes."
  3. *Jelly* (spotlight on the jelly cells): "Pink cells are jelly. Clear the gem on top to clear the jelly for +2."
  4. *Special gems:* a guided swap that makes a special, if the starting board has one; its text names the special it makes. Otherwise it's a card explaining how to make specials, pointing to How to play → Specials. Level 6's board starts without one, so it shows the card.
