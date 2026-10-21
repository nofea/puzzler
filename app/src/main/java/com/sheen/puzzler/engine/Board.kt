package com.sheen.puzzler.engine

import kotlin.random.Random

/** Mutable board state. Cells are immutable values replaced on change. */
class Board(
    val rows: Int,
    val cols: Int,
    colors: Int,
    val random: Random,
    private val cells: Array<Cell>,
    private var nextId: Long = 1,
) {
    /** Colors new pieces are drawn from. Endless raises it mid-run; pieces already on the board keep theirs. */
    var colors: Int = colors
        set(value) {
            require(value in MIN_COLORS..MAX_COLORS)
            field = value
        }

    init {
        require(cells.size == rows * cols)
        require(colors in MIN_COLORS..MAX_COLORS)
    }

    /** Id the next new piece gets; saved with an Endless run so ids stay unique after a restore. */
    val nextPieceId get() = nextId

    operator fun get(pos: Pos): Cell = cells[pos.row * cols + pos.col]

    operator fun get(
        row: Int,
        col: Int,
    ): Cell = cells[row * cols + col]

    operator fun set(
        pos: Pos,
        cell: Cell,
    ) {
        cells[pos.row * cols + pos.col] = cell
    }

    fun inBounds(pos: Pos) = pos.row in 0 until rows && pos.col in 0 until cols

    fun positions(): Sequence<Pos> =
        sequence {
            for (r in 0 until rows) for (c in 0 until cols) yield(Pos(r, c))
        }

    fun newPiece(
        color: Int,
        special: Special = Special.NONE,
    ) = Piece(nextId++, color, special)

    fun randomPiece() = newPiece(random.nextInt(colors))

    fun colorAt(pos: Pos): Int? = if (inBounds(pos)) this[pos].piece?.color?.takeIf { it >= 0 } else null

    fun snapshot() = BoardSnapshot(rows, cols, cells.toList())

    fun copy(random: Random = this.random) = Board(rows, cols, colors, random, cells.copyOf(), nextId)

    fun jellyCount() = cells.count { it.exists && it.jelly }

    fun stoneCount() = cells.count { it.isStone }

    fun lockedCount() = cells.count { it.locked }

    fun goals() = Goals(jellyCount(), lockedCount(), stoneCount())

    /** Fills every empty playable cell without creating a ready-made match. */
    fun fillWithoutMatches() {
        for (pos in positions()) {
            val cell = this[pos]
            if (!cell.isPlayable || cell.piece != null) continue
            val banned = mutableSetOf<Int>()
            val l1 = colorAt(Pos(pos.row, pos.col - 1))
            if (l1 != null && l1 == colorAt(Pos(pos.row, pos.col - 2))) banned += l1
            val u1 = colorAt(Pos(pos.row - 1, pos.col))
            if (u1 != null && u1 == colorAt(Pos(pos.row - 2, pos.col))) banned += u1
            val choices = (0 until colors).filter { it !in banned }
            this[pos] = cell.copy(piece = newPiece(choices[random.nextInt(choices.size)]))
        }
    }

    override fun toString(): String =
        buildString {
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    val cell = this@Board[r, c]
                    append(
                        when {
                            !cell.exists -> '#'
                            cell.isStone -> if (cell.stoneHp > 1) 'T' else 'S'
                            cell.piece == null -> '_'
                            cell.piece.special == Special.COLOR_BOMB -> '*'
                            else -> '0' + cell.piece.color
                        },
                    )
                }
                appendLine()
            }
        }

    companion object {
        const val MIN_COLORS = 3
        const val MAX_COLORS = 6

        /**
         * Builds an empty board from a layout. Characters:
         * `.` cell, `#` hole, `J` jelly, `S` stone (1 hit), `T` stone (2 hits), `L` locked piece, `K` locked on jelly.
         */
        fun fromLayout(
            layout: List<String>,
            colors: Int,
            random: Random,
        ): Board {
            val rows = layout.size
            val cols = layout.first().length
            require(layout.all { it.length == cols }) { "Layout rows must have equal length" }
            val cells =
                Array(rows * cols) { i ->
                    when (val ch = layout[i / cols][i % cols]) {
                        '.' -> Cell()
                        '#' -> Cell(exists = false)
                        'J' -> Cell(jelly = true)
                        'S' -> Cell(stoneHp = 1)
                        'T' -> Cell(stoneHp = 2)
                        'L' -> Cell(locked = true)
                        'K' -> Cell(locked = true, jelly = true)
                        else -> throw IllegalArgumentException("Unknown layout char '$ch'")
                    }
                }
            return Board(rows, cols, colors, random, cells)
        }

        /**
         * Builds a board with explicit pieces, for tests. Characters: `0`-`5` colors, `#` hole, `S`/`T` stones,
         * `*` color bomb, `_` empty. Set other specials on the returned board directly.
         */
        fun fromPieces(
            grid: List<String>,
            colors: Int,
            random: Random = Random(0),
        ): Board {
            val rows = grid.size
            val cols = grid.first().length
            val board = Board(rows, cols, colors, random, Array(rows * cols) { Cell() })
            for (pos in board.positions()) {
                board[pos] =
                    when (val ch = grid[pos.row][pos.col]) {
                        '#' -> Cell(exists = false)
                        'S' -> Cell(stoneHp = 1)
                        'T' -> Cell(stoneHp = 2)
                        '_' -> Cell()
                        '*' -> Cell(piece = board.newPiece(Piece.NO_COLOR, Special.COLOR_BOMB))
                        in '0'..'5' -> Cell(piece = board.newPiece(ch - '0'))
                        else -> throw IllegalArgumentException("Unknown piece char '$ch'")
                    }
            }
            return board
        }
    }
}
