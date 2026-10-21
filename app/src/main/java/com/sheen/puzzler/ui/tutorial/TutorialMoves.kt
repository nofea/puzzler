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

/** What a move's first clear does, from a run on a copy of the board: [before] the move, [after] its first clear. */
internal class Simulated(
    val firstClearPatterns: List<Pattern>,
    val cascades: Int,
    val firstSpecial: Special?,
    val specialsFired: Int,
    private val before: BoardSnapshot,
    private val after: BoardSnapshot,
) {
    val jellyCleared get() = before.goals().jelly - after.goals().jelly
    val locksBroken get() = before.goals().locked - after.goals().locked
    val stoneHits get() = before.cells.sumOf { it.stoneHp } - after.cells.sumOf { it.stoneHp }
}

/** Picks the moves tutorials guide, by simulating them on a copy of the board so the real one is untouched. */
internal object TutorialMoves {
    fun simulate(
        engine: GameEngine,
        a: Pos,
        b: Pos,
    ): Simulated {
        val before = engine.board.snapshot()
        val result = GameEngine(engine.board.copy(Random(0)), 1).swap(a, b)
        val firstClear = result.phases.firstOrNull { it.kind == PhaseKind.CLEAR }
        return Simulated(
            firstClearPatterns = firstClear?.patterns.orEmpty(),
            cascades = result.maxCascade,
            firstSpecial = firstClear?.specialsCreated?.firstOrNull(),
            specialsFired = firstClear?.specialsFired ?: 0,
            before = before,
            after = firstClear?.snapshot ?: before,
        )
    }

    /** A move whose simulation passes [accept], preferring one without a cascade so the lesson stays clear. */
    fun pickMove(
        engine: GameEngine,
        accept: (Simulated) -> Boolean,
    ): Pair<Pos, Pos>? {
        val candidates =
            MoveFinder
                .allMoves(engine.board)
                .map { it to simulate(engine, it.first, it.second) }
                .filter { accept(it.second) }
        return (candidates.firstOrNull { it.second.cascades == 1 } ?: candidates.firstOrNull())?.first
    }

    /** A move that makes a special, and which special; striped first if [preferStriped]. */
    fun specialMove(
        engine: GameEngine,
        preferStriped: Boolean,
    ): Triple<Pos, Pos, Special>? {
        val moves =
            MoveFinder.allMoves(engine.board).mapNotNull { (a, b) ->
                simulate(engine, a, b).firstSpecial?.let { Triple(a, b, it) }
            }
        return (if (preferStriped) moves.firstOrNull { it.third.isStriped } else null) ?: moves.firstOrNull()
    }

    fun cellsWhere(
        engine: GameEngine,
        test: (Cell) -> Boolean,
    ) = engine.board
        .positions()
        .filter { test(engine.board[it]) }
        .toList()
}
