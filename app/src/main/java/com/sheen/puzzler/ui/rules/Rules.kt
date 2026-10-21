package com.sheen.puzzler.ui.rules

import com.sheen.puzzler.engine.Board
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.Special
import kotlin.random.Random

/** One scripted move in a demo; [valid] is false for a swap meant to bounce back. */
data class DemoMove(
    val a: Pos,
    val b: Pos,
    val valid: Boolean = true,
)

/**
 * A looping mini-demo on a small board, played by the real engine so it always shows the real rules.
 * The board must have no ready-made match and at least one move, or the engine would reshuffle it (see DemoBoardsTest).
 */
class RuleDemo(
    val caption: String,
    private val grid: List<String>,
    val moves: List<DemoMove>,
    private val setup: Board.() -> Unit = {},
) {
    fun newEngine(): GameEngine = GameEngine(Board.fromPieces(grid, colors = 6, random = Random(7)).apply(setup), DEMO_MOVES)

    companion object {
        const val DEMO_MOVES = 5
    }
}

/** One card of the How to play pages. [table] rows are label to value. */
class RulesCard(
    val title: String,
    val lines: List<String>,
    val demos: List<RuleDemo>,
    val table: List<Pair<String, String>> = emptyList(),
)

/** Content of the How to play pages (docs/SPEC.md §6). */
object Rules {
    // Demo boards are 5×5. Background items use colors 0-3 in a pattern with no runs; P and Q mark the pieces that
    // matter, using colors 5 (purple star) and 4 (blue hexagon) so they stand out.
    private const val P = '5'
    private const val Q = '4'

    private fun board(vararg marks: Pair<Pos, Char>): List<String> {
        val at = marks.toMap()
        return (0 until 5).map { r -> (0 until 5).map { c -> at[Pos(r, c)] ?: ('0' + (r + 2 * c) % 4) }.joinToString("") }
    }

    private fun Board.special(
        pos: Pos,
        special: Special,
    ) {
        this[pos] = this[pos].copy(piece = this[pos].piece!!.copy(special = special))
    }

    private val dropIn = DemoMove(Pos(1, 2), Pos(2, 2))

    private val lineOf3 =
        RuleDemo(
            "Three in a row clears",
            board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(1, 2) to P),
            listOf(dropIn),
        )

    val cards =
        listOf(
            RulesCard(
                "Basics",
                listOf(
                    "Swipe an item onto a neighbor to swap them.",
                    "Line up 3 or more of the same item, across or down, to clear them.",
                    "A swap that doesn't make a match bounces back and costs nothing.",
                    "Cleared items fall, and new ones drop in from the top.",
                    "If no move is possible, the board shuffles for free.",
                ),
                listOf(
                    RuleDemo(
                        "No match: the swap bounces back",
                        board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(1, 2) to P),
                        listOf(DemoMove(Pos(0, 0), Pos(0, 1), valid = false)),
                    ),
                    lineOf3,
                ),
            ),
            RulesCard(
                "Scoring",
                listOf("Falling items can make new matches: a cascade. Each cascade step multiplies its points: ×1, ×1.5, ×2, ×2.5 …"),
                listOf(
                    RuleDemo(
                        "Cascade: the second match scores ×1.5",
                        board(
                            Pos(0, 2) to Q,
                            Pos(1, 2) to Q,
                            Pos(2, 2) to P,
                            Pos(3, 2) to P,
                            Pos(4, 1) to P,
                            Pos(4, 3) to Q,
                            Pos(4, 4) to Q,
                        ),
                        listOf(DemoMove(Pos(4, 1), Pos(4, 2))),
                    ),
                ),
                table =
                    listOf(
                        "Line of 3" to "5",
                        "Line of 4" to "10",
                        "L or T shape" to "15",
                        "Line of 5" to "25",
                        "Item cleared by a special" to "+1",
                        "Jelly cleared" to "+2",
                        "Stone broken" to "+3",
                    ),
            ),
            RulesCard(
                "Specials",
                listOf(
                    "Line of 4 → striped item: clears its whole row or column (the stripes show which).",
                    "L or T shape → bomb: clears the 3×3 area around it.",
                    "Line of 5 → color bomb: swap it with any item to clear every item of that color.",
                    "Specials fire when they're matched or hit by another special, so chains are possible.",
                ),
                listOf(
                    RuleDemo(
                        "Line of 4 makes a striped item",
                        board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(2, 3) to P, Pos(1, 2) to P),
                        listOf(dropIn),
                    ),
                    RuleDemo(
                        "L or T shape makes a bomb",
                        board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(1, 2) to P, Pos(3, 2) to P, Pos(4, 2) to P),
                        listOf(dropIn),
                    ),
                    RuleDemo(
                        "Line of 5 makes a color bomb",
                        board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(2, 3) to P, Pos(2, 4) to P, Pos(1, 2) to P),
                        listOf(dropIn),
                    ),
                    RuleDemo(
                        "A matched striped item clears its row",
                        board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(1, 2) to P),
                        listOf(dropIn),
                    ) { special(Pos(2, 1), Special.STRIPED_H) },
                ),
            ),
            RulesCard(
                "Combos",
                listOf(
                    "Swap two specials together for a bigger blast:",
                    "Striped + striped: a row and a column.",
                    "Striped + bomb: three rows and three columns.",
                    "Bomb + bomb: a 5×5 area.",
                    "Color bomb + item: every item of that color.",
                    "Color bomb + special: every item of that color becomes that special, and they all fire.",
                    "Color bomb + color bomb: the whole board.",
                ),
                listOf(
                    RuleDemo("Striped + bomb", board(), listOf(DemoMove(Pos(2, 2), Pos(2, 3)))) {
                        special(Pos(2, 2), Special.STRIPED_H)
                        special(Pos(2, 3), Special.BOMB)
                    },
                    RuleDemo("Color bomb + item", board(Pos(2, 2) to '*'), listOf(DemoMove(Pos(2, 2), Pos(2, 3)))),
                ),
            ),
            RulesCard(
                "Obstacles",
                listOf(
                    "Jelly (pink) sits under an item. Clear that item to clear the jelly: +2.",
                    "Stones don't move. Match next to one, or hit it with a special, to crack it. " +
                        "Dark stones take two hits; +3 when broken.",
                    "Chained items can't be swapped and don't fall. Include one in a match to free it.",
                ),
                listOf(
                    RuleDemo(
                        "Jelly clears, the stone breaks, the chain comes off",
                        board(Pos(2, 0) to P, Pos(2, 1) to P, Pos(1, 2) to P, Pos(1, 0) to 'S'),
                        listOf(dropIn),
                    ) {
                        this[Pos(2, 0)] = this[Pos(2, 0)].copy(jelly = true)
                        this[Pos(2, 2)] = this[Pos(2, 2)].copy(jelly = true)
                        this[Pos(2, 1)] = this[Pos(2, 1)].copy(locked = true)
                    },
                ),
            ),
            RulesCard(
                "Limits & stars",
                listOf(
                    "Each swap uses a move. Levels 6–10 also have a timer.",
                    "Run out of moves or time and the level ends.",
                    "Score 100 to pass. Higher scores earn ★★ and ★★★; each level shows its targets.",
                    "Once you've passed, tap Finish to bank your stars, or keep playing for more.",
                ),
                listOf(lineOf3),
            ),
        )
}
