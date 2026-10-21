package com.sheen.puzzler.data

import com.sheen.puzzler.engine.Board
import com.sheen.puzzler.engine.Cell
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.Piece
import com.sheen.puzzler.engine.Special
import com.sheen.puzzler.engine.UNLIMITED
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.random.Random

/** One board cell of a saved Endless run. A [pieceId] of 0 means no piece. */
@Serializable
data class SavedCell(
    val exists: Boolean = true,
    val pieceId: Long = 0,
    val color: Int = 0,
    val special: Special = Special.NONE,
    val jelly: Boolean = false,
    val stoneHp: Int = 0,
    val locked: Boolean = false,
)

/** An Endless run exactly as it was left: board, score and moves (docs/SPEC.md §4). The stage follows from the score. */
@Serializable
data class SavedRun(
    val rows: Int,
    val cols: Int,
    val colors: Int,
    val nextPieceId: Long,
    val score: Int,
    val movesMade: Int,
    val cells: List<SavedCell>,
) {
    /** An engine that carries on from here. The random stream restarts from [random]; refills needn't match. */
    fun toEngine(random: Random): GameEngine {
        val board =
            Board(
                rows,
                cols,
                colors,
                random,
                cells
                    .map { c ->
                        val piece = if (c.pieceId == 0L) null else Piece(c.pieceId, c.color, c.special)
                        Cell(c.exists, piece, c.jelly, c.stoneHp, c.locked)
                    }.toTypedArray(),
                nextPieceId,
            )
        return GameEngine(board, UNLIMITED, endless = true, score = score, movesMade = movesMade)
    }

    fun encode(): String = json.encodeToString(this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun of(engine: GameEngine): SavedRun {
            val board = engine.board
            return SavedRun(
                rows = board.rows,
                cols = board.cols,
                colors = board.colors,
                nextPieceId = board.nextPieceId,
                score = engine.score,
                movesMade = engine.movesMade,
                cells =
                    board
                        .positions()
                        .map { pos ->
                            val c = board[pos]
                            SavedCell(
                                c.exists,
                                c.piece?.id ?: 0,
                                c.piece?.color ?: 0,
                                c.piece?.special ?: Special.NONE,
                                c.jelly,
                                c.stoneHp,
                                c.locked,
                            )
                        }.toList(),
            )
        }

        /** Null if [text] isn't a run this version can read. */
        fun decode(text: String): SavedRun? = runCatching { json.decodeFromString<SavedRun>(text) }.getOrNull()
    }
}
