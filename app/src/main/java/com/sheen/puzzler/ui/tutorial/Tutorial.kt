package com.sheen.puzzler.ui.tutorial

import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.Cell
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pattern
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.Special
import kotlin.random.Random

/** HUD elements a tutorial step can point at. */
enum class HudTarget { SCORE, MOVES, TIME, FINISH, GOALS }

/** What a step spotlights. */
sealed interface Target {
    data class Hud(
        val key: HudTarget,
    ) : Target

    /** Board [cells], and optionally a HUD element ([hud]) along with them. */
    data class Cells(
        val cells: List<Pos>,
        val hud: HudTarget? = null,
    ) : Target

    object None : Target
}

/** One step of an in-level tutorial (docs/SPEC.md §7). */
sealed interface TutorialStep {
    val title: String
    val text: String
    val target: Target

    /** A callout the player dismisses with Next. */
    data class Info(
        override val title: String,
        override val text: String,
        override val target: Target = Target.None,
    ) : TutorialStep

    /** Only the swap of [a] and [b] is allowed; the step ends when that move has played out. */
    data class GuidedSwap(
        override val title: String,
        override val text: String,
        val a: Pos,
        val b: Pos,
    ) : TutorialStep {
        override val target get() = Target.Cells(listOf(a, b))
    }

    /** Waits, hidden, until the level is first passed, then shows like [Info]. */
    data class WhenPassed(
        override val title: String,
        override val text: String,
        override val target: Target,
    ) : TutorialStep

    /**
     * Fires the special the previous step made. It depends on the board as it is once that move has played out, so
     * the game turns it into a [GuidedSwap] (or, with no swap that fires it, a spotlight) when it's reached; see
     * [Tutorials.resolveFireSpecial].
     */
    data class FireSpecial(
        override val title: String,
        override val text: String,
    ) : TutorialStep {
        override val target get() = Target.None
    }
}

/** Progress through a tutorial. A [dormant] step is waiting (see [TutorialStep.WhenPassed]) and isn't shown. */
data class TutorialState(
    val steps: List<TutorialStep>,
    val index: Int = 0,
    val dormant: Boolean = false,
) {
    val step: TutorialStep? get() = steps.getOrNull(index)
    val visible: Boolean get() = step != null && !dormant

    /** Number of the current step and the total, for the "2 / 4" counter. */
    val counter: String get() = "${index + 1} / ${steps.size}"
}

/** In-level tutorials: one on every campaign level that introduces a new mechanic (docs/SPEC.md §7). */
object Tutorials {
    private val levels = setOf(1, 2, 3, 4, 5, 6, 7, 11, 16)

    private val level11 =
        listOf(
            TutorialStep.Info(
                "Move limit",
                "From now on moves are limited. Every swap uses one. Run out before you pass, and it's game over.",
                Target.Hud(HudTarget.MOVES),
            ),
            TutorialStep.Info(
                "Stars",
                "Stars now come from points: once you've passed, keep playing for ★★ and ★★★, or tap Finish.",
                Target.Hud(HudTarget.SCORE),
            ),
        )

    private val level16 =
        listOf(
            TutorialStep.Info("Timer", "This level has a timer. It starts when this tutorial ends.", Target.Hud(HudTarget.TIME)),
            TutorialStep.Info("Two limits", "Run out of moves or time before you pass, and it's game over.", Target.Hud(HudTarget.MOVES)),
        )

    fun hasTutorial(levelId: Int) = levelId in levels

    /** The tutorial script for a level, with guided moves picked from [engine]'s starting board; null if none. */
    fun forLevel(
        levelId: Int,
        engine: GameEngine,
    ): List<TutorialStep>? =
        when (levelId) {
            1 -> level1(engine)
            2 -> level2(engine)
            3 -> level3(engine)
            4 -> level4(engine)
            5 -> level5(engine)
            6 -> level6(engine)
            7 -> level7(engine)
            11 -> level11
            16 -> level16
            else -> null
        }

    private fun level1(engine: GameEngine): List<TutorialStep> {
        val (a, b) =
            TutorialMoves.pickMove(engine) { r -> r.firstClearPatterns == listOf(Pattern.LINE3) && r.cascades == 1 }
                ?: TutorialMoves.pickMove(engine) { r -> r.firstClearPatterns == listOf(Pattern.LINE3) }
                ?: MoveFinder.findMove(engine.board)!!
        return listOf(
            TutorialStep.GuidedSwap("Make a match", "Swipe to swap these two items and line up 3 of a kind.", a, b),
            TutorialStep.Info("Score", "Matches score points. Reach 100 and clear every goal to pass.", Target.Hud(HudTarget.SCORE)),
            TutorialStep.Info(
                "Moves",
                "There's no move limit here, but the fewer moves you use, the more stars you earn.",
                Target.Hud(HudTarget.MOVES),
            ),
            TutorialStep.WhenPassed(
                "Level passed!",
                "You've passed! Tap Finish now to bank your stars. Every extra move can cost a star.",
                Target.Hud(HudTarget.FINISH),
            ),
        )
    }

    private fun level2(engine: GameEngine): List<TutorialStep> {
        val make = TutorialMoves.specialMove(engine, preferStriped = true)
        val makeStep =
            if (make != null) {
                val (a, b, kind) = make
                TutorialStep.GuidedSwap("Make a special", kind.makeText, a, b)
            } else {
                TutorialStep.Info("Make a special", "Line up 4 to make a striped item.")
            }
        return listOf(
            makeStep,
            TutorialStep.FireSpecial("Fire it", "Match a special to fire it. Striped items clear their whole row or column."),
            TutorialStep.Info(
                "More specials",
                "An L or T shape makes a bomb (3×3 blast). A line of 5 makes a color bomb. See How to play → Specials.",
            ),
        )
    }

    private fun level3(engine: GameEngine): List<TutorialStep> =
        listOfNotNull(
            TutorialStep.Info(
                "Jelly",
                "Pink cells are jelly. Every jelly is a goal: clear them all to pass.",
                Target.Cells(TutorialMoves.cellsWhere(engine) { it.jelly }, HudTarget.GOALS),
            ),
            TutorialMoves.pickMove(engine) { it.jellyCleared > 0 }?.let { (a, b) ->
                TutorialStep.GuidedSwap("Clear jelly", "Match the item on top to clear the jelly underneath, for +2.", a, b)
            },
        )

    private fun level4(engine: GameEngine): List<TutorialStep> =
        listOfNotNull(
            TutorialStep.Info(
                "Locked items",
                "Chained items can't be swapped or fall. Every chain is a goal.",
                Target.Cells(TutorialMoves.cellsWhere(engine) { it.locked }, HudTarget.GOALS),
            ),
            TutorialMoves.pickMove(engine) { it.locksBroken > 0 }?.let { (a, b) ->
                TutorialStep.GuidedSwap(
                    "Break a chain",
                    "Include a chained item in a match, or hit it with a special, to break the chain. The item stays and becomes normal.",
                    a,
                    b,
                )
            },
        )

    private fun level5(engine: GameEngine): List<TutorialStep> =
        listOfNotNull(
            TutorialStep.Info(
                "Stones",
                "Stones block cells and never move. Every stone is a goal.",
                Target.Cells(TutorialMoves.cellsWhere(engine) { it.isStone }, HudTarget.GOALS),
            ),
            TutorialMoves.pickMove(engine) { it.stoneHits > 0 }?.let { (a, b) ->
                TutorialStep.GuidedSwap("Break a stone", "Match next to a stone, or hit it with a special, to break it, for +3.", a, b)
            },
        )

    private fun level6(engine: GameEngine): List<TutorialStep> =
        listOfNotNull(
            TutorialStep.Info(
                "Combos",
                "Swap two specials together for a bigger blast: striped + striped makes a cross, a bomb with anything " +
                    "makes it bigger, and a color bomb turns a whole color into specials. See How to play → Combos.",
            ),
            TutorialMoves.specialMove(engine, preferStriped = false)?.let { (a, b, kind) ->
                TutorialStep.GuidedSwap("Make a special", "Start here: this swap makes ${kind.displayName}.", a, b)
            },
        )

    private fun level7(engine: GameEngine): List<TutorialStep> {
        val hard = TutorialMoves.cellsWhere(engine) { it.stoneHp >= 2 }
        return listOf(
            TutorialStep.Info(
                "Gaps",
                "Gaps aren't part of the board. Items slide around them.",
                Target.Cells(TutorialMoves.cellsWhere(engine) { !it.exists }),
            ),
            TutorialStep.Info(
                "Dark stones",
                "Dark stones take two hits. The first hit cracks them into a normal stone.",
                Target.Cells(hard.take(1)),
            ),
        )
    }

    /**
     * The [TutorialStep.FireSpecial] step for the board as it is now: a guided swap that fires a special, or if no swap
     * does, a spotlight on the special (or a plain card if there's none).
     */
    fun resolveFireSpecial(
        step: TutorialStep.FireSpecial,
        engine: GameEngine,
    ): TutorialStep {
        TutorialMoves.pickMove(engine) { it.specialsFired > 0 }?.let { (a, b) ->
            return TutorialStep.GuidedSwap(step.title, step.text, a, b)
        }
        val specials = TutorialMoves.cellsWhere(engine) { it.piece != null && it.piece.special != Special.NONE }
        return TutorialStep.Info(step.title, step.text, if (specials.isEmpty()) Target.None else Target.Cells(specials.take(1)))
    }
}

/** What the guided swap that makes this special says. */
internal val Special.makeText
    get() =
        when (this) {
            Special.STRIPED_H, Special.STRIPED_V -> "Line up 4 to make a striped item."
            Special.BOMB -> "Make an L or T shape to make a bomb."
            else -> "Line up 5 to make a color bomb."
        }

internal val Special.displayName
    get() =
        when (this) {
            Special.STRIPED_H, Special.STRIPED_V -> "a striped item"
            Special.BOMB -> "a bomb"
            else -> "a color bomb"
        }
