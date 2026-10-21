# Puzzler — Specification

Puzzler is a Candy‑Crush‑style match‑3 game for Android with two modes. **Campaign** has 20 levels, each harder than the last; to pass a level you clear every obstacle on the board and score at least 100 points. **Endless** is one board that keeps going, for playing without interruptions. More complex patterns score more points.

## 1. Product decisions
| Topic | Decision |
|---|---|
| Name | **Puzzler** |
| Pieces | Six items in the six piece colors: fries (red), burger (orange), pizza (yellow), potted plant (green), soda can (blue), chocolate bar (purple). Shape and color both identify an item (colorblind‑friendly). The color bomb is a near‑black orb ringed by mini versions of the six items, with a gold sparkle core (same design as the app icon). See §8 |
| App icon | The color bomb on a purple radial glow, with a monochrome version for themed icons. See §6 |
| Launch | Splash, then a ~2 s color‑bomb intro (assemble → detonate) on cold start that reveals the Home screen; tap to skip. See §5 |
| Modes | **Campaign** (20 levels, §3) and **Endless** (§4), chosen on the Home screen. Endless unlocks after clearing campaign level 10 |
| Tutorials | A How to play page (from Home, the map, Settings and the in‑game "?") and an in‑level tutorial, shown once, on every campaign level that introduces a new mechanic: L1–L7, L11 and L16. See §7 |
| Pass rule (Campaign) | Clear every goal (jelly, locked pieces, stones) **and** score ≥100, before the limits run out (L11–20). Stars: 1★ = passed. On L1–10 the 2★/3★ thresholds are move pars (fewer moves = more stars), and reaching 3★ finishes the level automatically; on L11–20 they are scores |
| Limits (Campaign) | L1–10: none; these levels can't be lost. L11–15: move limit. L16–20: move limit **and** countdown timer; either running out ends the level |
| Endless | No limits, goals or dialogs; the run is resumed where it was left, and goes on until the player starts a new one or the board jams. Difficulty rises in stages as the score grows |
| Splashes | A small "Level cleared!" splash on every campaign win, a bigger "Game Over" splash when a level is lost, and the grandest, an "All levels complete" finale, the first time every level is cleared. See §5 |
| Patterns | Line 3/4/5, L/T shapes, cascades, special pieces |
| Obstacles | Jelly, Stone (1–2 hits), Locked piece |
| Difficulty knobs | moves↓, time↓, colors 4→6, obstacles, irregular boards, higher star thresholds |
| Records | Kept on the device, separately per mode. Campaign: unlocked levels, stars, best score and fewest moves per level, campaign points, recent wins. Endless: the current run, best run, best points per move, lifetime points, recent runs. See §5 |
| Progress | Also saved on the device: tutorials seen, finale seen, sound and vibration settings |
| Audio/FX | Short synthesized sounds and haptics; sound and vibration can each be turned off in Settings |

## 2. Game rules
These rules apply to both modes. Campaign adds goals, limits and stars (§3); Endless adds stages (§4).

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
- **Generation**: the starting board has no ready‑made matches. Each campaign level has a fixed seed, so it always starts with the same board.
- **Dead board**: if no valid move exists, the movable pieces are shuffled (obstacles stay put) until at least one move exists and there are no instant matches. This costs no move, and the game shows "Shuffling…".

## 3. Campaign
- **Goals**: every jelly, locked piece and stone on a level's starting board is a goal. A level is **passed** once all goals are cleared (jelly cleared, locks removed, stones destroyed) **and** the score is ≥100, in either order. A level with no obstacles only needs the 100 points.
- **End**:
  - *L1–10 (no limits):* the level can't be lost. Stars come from the number of moves used, so playing on can only lose stars. If the level is passed while still within its 3★ move par, it **finishes automatically** with 3★ and the win dialog pushes on to the next level. Otherwise, once passed, the player taps "Finish" to bank their stars. "Quit" leaves without a result.
  - *L11–20:* once passed, the level can still be played until moves or time run out (which then ends it as a win), or the player can tap "Finish" to bank their stars. Stars come from the score. Running out of moves or time before passing loses the level, even if the score is ≥100 (the goals are still missing).
  - A win plays the Level cleared splash and then the win dialog; a loss plays the Game Over splash and then the fail dialog, which offers a retry (§5).
- **Campaign points**: the sum of every level's best score. A replay only adds to it by beating that level's best.

### Levels
Each level introduces at most one new mechanic (★ = it gets an in‑level tutorial, §7), then later levels mix them.

| L | Colors | Moves | Time | Board / obstacles | New | 2★ / 3★ |
|---|---|---|---|---|---|---|
| 1 | 4 | – | – | full 8×8 | ★ basics: match, score, goals, Finish | ≤ 5 / ≤ 3 moves |
| 2 | 5 | – | – | full | ★ specials | ≤ 5 / ≤ 3 moves |
| 3 | 5 | – | – | 8 jelly in the center | ★ jelly | ≤ 6 / ≤ 3 moves |
| 4 | 5 | – | – | 6 locked | ★ locked | ≤ 10 / ≤ 5 moves |
| 5 | 5 | – | – | 4 stones (1 hit) + 8 jelly | ★ stones | ≤ 7 / ≤ 4 moves |
| 6 | 5 | – | – | jelly + locked | ★ combos | ≤ 12 / ≤ 8 moves |
| 7 | 5 | – | – | corner holes, 4 stones (2 hits) | ★ holes, 2‑hit stones | ≤ 7 / ≤ 4 moves |
| 8 | 6 | – | – | jelly + locked, some locked on jelly | 6 colors | ≤ 28 / ≤ 12 moves |
| 9 | 6 | – | – | cross‑shaped board, jelly + stones | – | ≤ 9 / ≤ 6 moves |
| 10 | 6 | – | – | irregular board, all obstacles | – | ≤ 18 / ≤ 8 moves |
| 11 | 5 | 10 | – | jelly + 4 stones (1 hit) | ★ move limit | 430 / 646 |
| 12 | 6 | 18 | – | locked + jelly | – | 350 / 478 |
| 13 | 6 | 21 | – | cross‑shaped board, stones (2 hits) + jelly | – | 312 / 408 |
| 14 | 6 | 15 | – | irregular board, locked + stones | – | 234 / 343 |
| 15 | 6 | 24 | – | irregular board, all obstacles | – | 446 / 571 |
| 16 | 5 | 12 | 60s | 16 jelly | ★ countdown timer | 516 / 717 |
| 17 | 6 | 23 | 105s | locked + stones | – | 442 / 594 |
| 18 | 6 | 26 | 115s | cross‑shaped board, jelly + locked | – | 372 / 505 |
| 19 | 6 | 21 | 90s | irregular board, stones (2 hits) + jelly | – | 305 / 459 |
| 20 | 6 | 22 | 90s | irregular board, all obstacles | – | 450 / 604 |

Balance (tuned with a greedy player that values each cleared goal like 30 points):
- **L1–10** can't be lost, so a greedy player always passes eventually. The move pars are set from the greedy player's moves used to pass: 3★ near its 25th percentile and 2★ near its 70th, but never below 3 moves for 3★, with 2★ at least 2 moves more, so 3★ is never down to a lucky first move.
- **L11–20**: a greedy player passes about 90% of L11 attempts, falling steadily to about 50% on L20; each move limit is set at the greedy player's moves to pass at that rate. 2★ is set near the greedy player's 30th‑percentile score at the limit and 3★ near its 75th. Timers allow about 4–5 s per move, less on later levels.
- Every level's goals must be reachable: no locked piece, jelly or stone that no match or blast can ever touch.

## 4. Endless
One board that keeps going for as long as it can, for playing without interruptions. It unlocks once campaign level 10 is cleared.

- **No interruptions**: no move limit, no timer, no goals, no tutorials and no dialogs while playing. The game rules (§2) apply as usual, including the free shuffle on a dead board.
- **The run**: leaving (Quit, or closing the app) saves the run exactly as it is: board, score, moves and stage. Opening Endless resumes it. **New run** (with confirmation) ends the current run and starts a fresh one. Every run starts from a new random board.
- **Stages**: the score sets the stage, one stage per 500 points (stage 1 = 0–499, stage 2 = 500–999, …).

  | Stage | Colors | Obstacles |
  |---|---|---|
  | 1 | 4 | none |
  | 2 | 5 | none |
  | 3 | 6 | none |
  | 4+ | 6 | after a move settles, random normal items become locked or turn into 1‑hit stones: 0.6 per move on average at stage 4 and 0.5 more each stage after, so later stages often add several per move. There is no cap on how many can be on the board |

  New colors appear only on newly spawned items, and obstacles only after a move has settled, never in the middle of a cascade. Obstacles score as usual (+3 per stone destroyed) but are never goals.
- **Stage up**: a small "Stage N!" toast slides in over the top of the board for about 1 s, with a short rising chime and a light vibration tick, and the stage badge pulses. It never blocks input.
- **Jammed board ends the run**: if the board is dead and no shuffle of the movable items can make a move (too many locked items and stones), the run is over. The Game Over splash plays (§5) with the subtitle "Board jammed", then the **run summary** card: score, moves, stage reached and points per move, with a "New best!" badge on each record it beat. Buttons: **New run** and **Home**. The finished run is no longer saved.
- **HUD**: score, moves made, the stage badge, and points per move (score ÷ moves). Buttons: "New run" and "Quit".
- **Records** update live, as the run goes: best run score (and the moves it took) and best points per move change the moment the current run beats them, and lifetime Endless points grow with every move. A run counts towards best points per move only once it has lasted at least 50 moves. When a run ends (jammed, or New run), it goes into the recent runs list (§5).
- **Balance**: the obstacle rates are tuned so that a greedy player's median run jams around stage 8–10 (tuned: 10% of runs by stage 8, half by stage 9, 90% by stage 11), and jams before stage 6 are rare.

## 5. Screens and feedback
- **Launch intro**: the app opens on a splash of the color bomb's orb and sparkle on the dark purple background. The intro then plays (about 2 s):
  1. **Assemble (0–0.8 s):** the six items fly in from off‑screen and lock into their ring around the orb, one by one, with a tick as each lands.
  2. **Title (0.8 s):** the sparkle flashes, and "PUZZLER" (rounded display font, gold) fades in under the bomb, followed by the tagline "Match. Cascade. Detonate.".
  3. **Detonate (1.3 s):** the bomb detonates with a boom and a strong vibration, and the items burst outward off‑screen.
  4. **Reveal (1.3–2.0 s):** a circle expanding from the bomb's center uncovers the Home screen.
  - Tapping during the intro jumps straight to the detonation.
  - The intro plays only when the app starts fresh, not when returning from the background.
  - It is skipped entirely when the phone's animations are turned off (accessibility).
  - Its sounds and vibration follow the Sound and Vibration settings.
- **Home**: the screen the app opens on. "PUZZLER" (rounded display font, gold) at the top, then two big buttons:
  - **Campaign**, which opens the level map.
  - **Endless**, which resumes the current run, or starts one if there is none. Until campaign level 10 is cleared it shows a lock and "Clear level 10 to unlock", and can't be opened.
  - Below them: "How to play", "Settings", "About", and a **Records** panel showing campaign points and total stars, and the Endless best run score and best points per move (a dash until there is one).
  - Back from the level map or from Endless returns here.
- **About**: opened from Home. Shows the game's title and tagline, "A game by **Sheen Games**", and the installed app's version (e.g. "Version 1.4.0"), with a Back button.
- **Level map**: the 20 levels as a winding, scrolling path of nodes, opening scrolled to the furthest unlocked level. Locked levels show a lock, cleared levels show their stars (a gold ring at 3★), levels with a move limit show their moves, timed levels also show their time limit, and the total stars (out of 60) are shown at the top. Level 1 starts unlocked; clearing a level unlocks the next.
- **Game screen** (Campaign; Endless uses the same board and banner with its own HUD, §4):
  - **HUD**: level number and moves. On L1–10 moves count **up** ("Moves 7") and the star row shows the par for the next star the player would lose (e.g. "★★★ ≤ 15"). On L11–20 moves count down, with time left on timed levels (both turn red when running low). It also shows the score against the 100 target and the stars earned so far.
  - **Goals**: one counter per obstacle type on the level (jelly, lock, stone), each with its icon and the number left. It ticks down as goals are cleared, and shows a check mark at 0. Types the level doesn't have aren't shown.
  - **Progress bar**: on L1–10 it fills with progress towards passing (goals cleared and score up to 100); on L11–20 it runs to the 3★ score.
  - **Board**: swaps, clears, falls and refills are animated step by step. A banner calls out patterns and cascades (e.g. "Line of 5!", "Cascade x3!") with the points earned.
  - **Buttons**: "Finish" (enabled once the level is passed) and "Quit".
- **Timer**: pauses while the app is in the background.
- **Result dialog**: shows the title ("Level N cleared!", "Out of moves" or "Out of time"), stars, and the result against the best: moves used and fewest moves on L1–10, score and best score on L11–20. A loss also lists the goals still left (e.g. "2 jelly, 1 lock left") and the score if it was under 100. A win offers Next level / Replay / Map; a loss offers Retry / Map. After a 3★ auto‑finish on L1–10, **Next level** is the big primary button and pulses gently, with Replay and Map smaller beside it.
- **Level cleared splash** (every campaign win; about 1 s, then the win dialog). Deliberately smaller than the Game Over splash and the finale: no dimming and no shake, and the board stays visible.
  1. **Pop (0–0.3 s):** "Level cleared!" (rounded display font, gold) pops in over the board with a small gold ring bursting out from behind it, and the items left on the board do a short hop.
  2. **Stars (0.3–1.0 s):** the earned stars stamp in one by one under the title, each with a rising chime and a light vibration tick.
  - Tapping skips straight to the win dialog.
- **Game Over splash** (campaign L11–20 when moves or time run out before passing, and an Endless run that jams; about 1.5 s, then the fail dialog or the run summary):
  1. **Collapse (0–0.6 s):** the items drop off the bottom of the board under gravity, each spinning a little, column by column, while the screen dims to a dark red.
  2. **Slam (0.6 s):** "GAME OVER" (rounded display font, red with a dark outline) slams in from oversized to full size with a short screen shake, with "Out of moves", "Out of time" or "Board jammed" fading in under it.
  3. **Hold (0.6–1.5 s):** the title holds, then the fail dialog (or Endless run summary) opens over it.
  - Sound: a descending two‑note synth "wah‑wah", then a low thud on the slam. Haptics: a heavy vibration on the slam.
  - Tapping skips straight to the fail dialog (or run summary).
- **All levels complete finale**: plays after the result dialog the first time level 20 is won (every level is cleared by then), when the player leaves the dialog. About 4 s, then it waits for a tap:
  1. **Assemble (0–0.8 s):** as in the launch intro, the six items fly in and lock into their ring around the color bomb, with a tick each.
  2. **Detonate (0.8 s):** the bomb detonates with a boom and a strong vibration; a gold flash fills the screen.
  3. **Celebrate (0.8–4 s):** fireworks burst in the six piece colors, and confetti made of the six items rains down and keeps falling. "ALL LEVELS COMPLETE!" (rounded display font, gold) scales in, and the total stars count up under it ("★ 47 / 60").
  4. **Continue:** "Tap to continue" fades in; a tap goes to the level map.
  - Sound: a rising fanfare arpeggio into the boom, then sparkle tails with each firework. Haptics: a celebratory pattern of short pulses with the fireworks.
  - It's remembered on the device, and "Reset campaign" brings it back. "Replay finale" in Settings plays it again once it has been seen.
- **Animations off** (the phone's animations are turned off): the Level cleared and Game Over splashes are skipped and the dialog (or Endless run summary) opens straight away; the finale is replaced by a still card with the title, the total stars and a Continue button. Their sounds and vibration still play.
- **Records** (kept on the device; Campaign and Endless are stored separately):
  - *Campaign:* unlocked levels; per level the stars, best score and fewest moves; campaign points (§3) and total stars; and the last 20 wins (level, score, moves, stars, date and time).
  - *Endless:* the current run (§4); best run score and the moves it took; best points per move (runs of ≥50 moves); lifetime Endless points (everything ever scored in Endless); and the last 20 ended runs (score, moves, stage, how it ended: jammed or New run, date and time). Best run and best points per move update live during a run (§4).
- **Settings**: sound and vibration toggles, "Replay finale" (shown once the finale has been seen), and two resets, each with a confirmation:
  - **Reset campaign** locks everything except level 1 and clears the campaign records, and brings back the tutorials and the finale. Endless locks again until level 10 is cleared, but its records and current run are kept.
  - **Reset endless** clears the current run and the Endless records.
- **Sound and haptics**: distinct sounds for swap, invalid swap, special, shuffle, level cleared (a chime per star), game over, the finale fanfare and the Endless stage‑up. The match chime rises in pitch with each cascade step. All sounds and vibration follow the Sound and Vibration settings.
- **Update from an older version**: the levels and rules changed, so the first launch after updating resets all progress (both resets, plus the tutorials and the finale).

## 6. App icon
The launcher icon is the game's **color bomb**, the one piece that is unique to Puzzler.

| Topic | Decision |
|---|---|
| Subject | Color bomb: one orb ringed by the six items |
| Style | Flat orb and sparkle; the mini items use the item artwork (§8) |
| Orb | Near‑black `#140A26` |
| Ring | Mini versions of the six items (fries, burger, pizza, potted plant, soda can, chocolate bar), evenly spaced, starting at the top and going clockwise in that order |
| Center | Gold four‑point sparkle `#FFD34D` |
| Background | Purple radial glow: `#3A2470` at the center fading to `#1B1035` at the edges |
| Themed icon | Monochrome version: the orb silhouette with the items and sparkle cut out |
| In‑game | The color bomb on the board uses the same design and proportions |

Proportions (on the 108‑unit adaptive icon canvas, everything inside the central 66‑unit safe zone):
- Orb radius 26.
- Item centers on a ring of radius 0.6 × orb; each mini item fills a square of side 0.42 × orb.
- Sparkle outer radius 0.23 × orb; inner radius 0.3 × outer.

## 7. Tutorials

### How to play page
Opened from "How to play" on Home or the level map, the "How to play" row in Settings, or the "?" in the game HUD (Campaign and Endless). While it's open from a level, that level's timer is paused.

The page is six swipeable cards, with page dots, Previous/Next, and Done on the last card. Each card has a looping mini‑demo on a 5×5 board played by the real game rules. A fingertip shows each swipe, and a small Moves/Score readout counts along.

| Card | Explains | Demo(s) |
|---|---|---|
| Basics | Swap neighbors; match 3+ across or down; a non‑matching swap bounces back for free; items fall and refill; a dead board shuffles for free | A swap bouncing back; three in a row clearing |
| Scoring | The points table (line of 3/4/5, L/T, special clears, jelly, stone) and cascade multipliers ×1, ×1.5, ×2 … | A cascade whose second match scores ×1.5 |
| Specials | Line of 4 → striped, L/T → bomb, line of 5 → color bomb; specials fire when matched or hit, so they chain | Each special being made, and a striped item clearing its row |
| Combos | Every special + special swap, and color bomb + item | Striped + bomb; color bomb + item |
| Obstacles | Jelly, stones (dark ones take two hits), chained items; every obstacle is a goal that must be cleared to pass | One match that clears jelly, breaks a stone and frees a chained item |
| Goals, limits & stars | Clear every goal and score 100 to pass. L1–10 have no limits and stars for using few moves; L11–15 add a move limit, L16–20 a timer too, and either running out ends the level; there, more points earn ★★/★★★. Finish. Endless: no limits or goals, stages every 500 points, the run ends only if the board jams | Three in a row using a move |

### In‑level tutorials
Every campaign level that introduces a new mechanic (L1–L7, L11, L16) has a tutorial the first time it's played. Each is remembered on the device, and "Reset campaign" brings them back. Endless has no in‑level tutorials. The screen is dimmed except for a pulsing gold spotlight on what the step is about. A card next to the spotlight shows the step's title and text, a step counter, **Skip tutorial**, and **Next** (or **Got it** on the last step). Skip ends the tutorial and counts as seen.

- **Guided swap:** the two items to swap are spotlighted, and a fingertip animation swipes between them. Only that swap is accepted; every other touch is ignored and costs nothing (not even a move). The step ends once the swap has played out. Each level with a guided swap is built so that its starting board has that swap.
- **Level 1 — basics:**
  1. *Make a match* (guided swap that lines up 3): "Swipe to swap these two items and line up 3 of a kind."
  2. *Score* (spotlight on the score, bar and stars): "Matches score points. Reach 100 and clear every goal to pass."
  3. *Moves* (spotlight on Moves): "There's no move limit here, but the fewer moves you use, the more stars you earn." Free play follows.
  4. *Level passed!* When the level is first passed, the Finish button is spotlighted: "You've passed! Tap Finish now to bank your stars. Every extra move can cost a star." Tapping Finish there finishes the level. This step is left out when the pass is a 3★ auto‑finish (§3).
- **Level 2 — specials:**
  1. *Make a special* (guided swap that makes a line of 4; its text names the special it makes): "Line up 4 to make a striped item."
  2. *Fire it* (guided swap that matches the new special, if there is one; otherwise a spotlight on it): "Match a special to fire it. Striped items clear their whole row or column."
  3. *More specials* (card): "An L or T shape makes a bomb (3×3 blast). A line of 5 makes a color bomb. See How to play → Specials."
- **Level 3 — jelly:**
  1. *Jelly* (spotlight on the jelly cells, and the jelly goal counter): "Pink cells are jelly. Every jelly is a goal: clear them all to pass."
  2. *Clear jelly* (guided swap that clears an item on jelly): "Match the item on top to clear the jelly underneath, for +2."
- **Level 4 — locked:**
  1. *Locked items* (spotlight on the locked items, and the lock goal counter): "Chained items can't be swapped or fall. Every chain is a goal."
  2. *Break a chain* (guided swap that matches a locked item): "Include a chained item in a match, or hit it with a special, to break the chain. The item stays and becomes normal."
- **Level 5 — stones:**
  1. *Stones* (spotlight on the stones, and the stone goal counter): "Stones block cells and never move. Every stone is a goal."
  2. *Break a stone* (guided swap that makes a match next to a stone): "Match next to a stone, or hit it with a special, to break it, for +3."
- **Level 6 — combos:**
  1. *Combos* (card): "Swap two specials together for a bigger blast: striped + striped makes a cross, a bomb with anything makes it bigger, and a color bomb turns a whole color into specials. See How to play → Combos."
  2. *Make a special* (guided swap that makes a special, if the starting board has one; otherwise this step is left out): "Start here: this swap makes a <special>."
- **Level 7 — board shapes and strong stones:**
  1. *Gaps* (spotlight on the holes): "Gaps aren't part of the board. Items slide around them."
  2. *Dark stones* (spotlight on a 2‑hit stone): "Dark stones take two hits. The first hit cracks them into a normal stone."
- **Level 11 — move limit:**
  1. *Move limit* (spotlight on Moves): "From now on moves are limited. Every swap uses one. Run out before you pass, and it's game over."
  2. *Stars* (spotlight on the score, bar and stars): "Stars now come from points: once you've passed, keep playing for ★★ and ★★★, or tap Finish."
- **Level 16 — countdown timer** (the timer doesn't start until the tutorial ends):
  1. *Timer* (spotlight on the clock): "This level has a timer. It starts when this tutorial ends."
  2. *Two limits* (spotlight on Moves): "Run out of moves or time before you pass, and it's game over."

## 8. Items
The six pieces are everyday items: snacks and a houseplant. Each is recolored so its dominant color is its piece color, so color and shape both tell items apart at a glance.

| Color | Item | Recolor |
|---|---|---|
| Red | Fries | none: red carton, golden fries |
| Orange | Burger | none: orange bun |
| Yellow | Pizza slice | cheese made yellow; crust and pepperoni unchanged |
| Green | Potted plant | the orange pot becomes green, a step lighter and darker than the leaves |
| Blue | Soda can | adapted from a canned‑food tin: blue body, the food label replaced by a light‑blue panel with a white rim, stripe and logo dot, and a pull tab on the lid |
| Purple | Chocolate bar | wrapper made purple; the chocolate stays brown |

- **Look:** items sit directly on the board tiles with a soft drop shadow. There's no glossy gem shading.
- **Specials:** a striped item shows three white stripes across it (horizontal or vertical, matching the direction it clears). A bomb item has a white ring around it and a white dot in its center. A color bomb is the near‑black orb ringed by the six mini items with a gold sparkle core.
- **Everywhere:** the same items appear on the board, in How to play, in the launch intro (they fly into the ring) and on the app icon.
- **Art credit:** based on Google's Noto Emoji artwork (🍟 🍔 🍕 🪴 🥫 🍫), recolored and adapted.
