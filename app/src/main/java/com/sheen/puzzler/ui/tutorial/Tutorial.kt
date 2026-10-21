package com.sheen.puzzler.ui.tutorial

import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pattern
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.Special
import kotlin.random.Random

/** HUD elements a tutorial step can point at. */
enum class HudTarget { SCORE, MOVES, TIME, FINISH }

/** What a step spotlights. */
sealed interface Target {
    data class Hud(val key: HudTarget) : Target
    data class Cells(val cells: List<Pos>) : Target
    data object None : Target
}

/** One step of an in-level tutorial (docs/SPEC.md §6). */
sealed interface TutorialStep {
    val title: String
    val text: String
    val target: Target

    /** A callout the player dismisses with Next. */
    data class Info(override val title: String, override val text: String, override val target: Target = Target.None) : TutorialStep

    /** Only the swap of [a] and [b] is allowed; the step ends when that move has played out. */
    data class GuidedSwap(override val title: String, override val text: String, val a: Pos, val b: Pos) : TutorialStep {
        override val target get() = Target.Cells(listOf(a, b))
    }

    /** Waits, hidden, until the score first reaches the pass mark, then shows like [Info]. */
    data class WhenPassed(override val title: String, override val text: String, override val target: Target) : TutorialStep
}

/** Progress through a tutorial. A [dormant] step is waiting (see [TutorialStep.WhenPassed]) and isn't shown. */
data class TutorialState(val steps: List<TutorialStep>, val index: Int = 0, val dormant: Boolean = false) {
    val step: TutorialStep? get() = steps.getOrNull(index)
    val visible: Boolean get() = step != null && !dormant

    /** Number of the current step and the total, for the "2 / 4" counter. */
    val counter: String get() = "${index + 1} / ${steps.size}"
}

object Tutorials {

    fun hasTutorial(levelId: Int) = levelId == 1 || levelId == 6

    /** The tutorial script for a level, with guided moves picked from [engine]'s starting board; null if none. */
    fun forLevel(levelId: Int, engine: GameEngine): List<TutorialStep>? = when (levelId) {
        1 -> level1(engine)
        6 -> level6(engine)
        else -> null
    }

    private fun level1(engine: GameEngine): List<TutorialStep> {
        val (a, b) = pickMove(engine) { r -> r.firstClearPatterns == listOf(Pattern.LINE3) && r.cascades == 1 }
            ?: pickMove(engine) { r -> r.firstClearPatterns == listOf(Pattern.LINE3) }
            ?: MoveFinder.findMove(engine.board)!!
        return listOf(
            TutorialStep.GuidedSwap("Make a match", "Swipe to swap these two items and line up 3 of a kind.", a, b),
            TutorialStep.Info(
                "Score",
                "Matches score points. Reach 100 to pass the level; score more for ★★ and ★★★.",
                Target.Hud(HudTarget.SCORE),
            ),
            TutorialStep.Info("Moves", "Every swap uses a move. When moves run out, the level ends.", Target.Hud(HudTarget.MOVES)),
            TutorialStep.WhenPassed(
                "Level passed!",
                "You've passed! Tap Finish to bank your stars, or keep playing for a higher score.",
                Target.Hud(HudTarget.FINISH),
            ),
        )
    }

    private fun level6(engine: GameEngine): List<TutorialStep> {
        val jelly = engine.board.positions().filter { engine.board[it].jelly }.toList()
        val special = MoveFinder.allMoves(engine.board).firstNotNullOfOrNull { (a, b) ->
            simulate(engine, a, b).firstSpecial?.let { Triple(a, b, it) }
        }
        val specialStep = if (special != null) {
            val (a, b, kind) = special
            val (title, text) = when (kind) {
                Special.STRIPED_H, Special.STRIPED_V ->
                    "Special items" to "Line up 4 to make a striped item. Matched later, it clears its whole row or column. Swipe here!"
                Special.BOMB -> "Special items" to "Make an L or T shape to create a bomb. Matched later, it blasts a 3×3 area. Swipe here!"
                else -> "Special items" to "Line up 5 to make a color bomb. Swap it with any item to clear that color. Swipe here!"
            }
            TutorialStep.GuidedSwap(title, text, a, b)
        } else {
            TutorialStep.Info(
                "Special items",
                "Line up 4, an L or T shape, or 5 in a row to make special items. See How to play → Specials for what each one does.",
            )
        }
        return listOf(
            TutorialStep.Info("Timer", "This level has a timer. It starts when this tutorial ends.", Target.Hud(HudTarget.TIME)),
            TutorialStep.Info("Two limits", "Run out of moves or time and the level ends. 100 points still passes.", Target.Hud(HudTarget.MOVES)),
            TutorialStep.Info("Jelly", "Pink cells are jelly. Clear the item on top to clear the jelly for +2.", Target.Cells(jelly)),
            specialStep,
        )
    }

    private class Simulated(val firstClearPatterns: List<Pattern>, val cascades: Int, val firstSpecial: Special?)

    /** Plays a move on a copy of the board, leaving [engine] untouched. */
    private fun simulate(engine: GameEngine, a: Pos, b: Pos): Simulated {
        val result = GameEngine(engine.board.copy(Random(0)), 1).swap(a, b)
        val firstClear = result.phases.firstOrNull { it.kind == PhaseKind.CLEAR }
        return Simulated(firstClear?.patterns.orEmpty(), result.maxCascade, firstClear?.specialsCreated?.firstOrNull())
    }

    private fun pickMove(engine: GameEngine, accept: (Simulated) -> Boolean): Pair<Pos, Pos>? =
        MoveFinder.allMoves(engine.board).firstOrNull { (a, b) -> accept(simulate(engine, a, b)) }
}
