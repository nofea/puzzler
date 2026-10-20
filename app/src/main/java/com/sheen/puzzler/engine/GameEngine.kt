package com.sheen.puzzler.engine

enum class PhaseKind { SWAP, SWAP_BACK, CLEAR, FALL, SHUFFLE }

/** One animatable step of a move. [snapshot] is the board after the step. */
data class Phase(
    val kind: PhaseKind,
    val snapshot: BoardSnapshot,
    val cascade: Int = 0,
    val scoreDelta: Int = 0,
    val patterns: List<Pattern> = emptyList(),
    val specialsCreated: List<Special> = emptyList(),
    val specialsFired: Int = 0,
)

data class MoveResult(val valid: Boolean, val phases: List<Phase>) {
    val scoreDelta get() = phases.sumOf { it.scoreDelta }
    val maxCascade get() = phases.maxOfOrNull { it.cascade } ?: 0
}

/**
 * Rules for one level: owns the board, the score and the move count. Time limits are enforced by the caller,
 * since the engine has no clock.
 */
class GameEngine(val board: Board, moves: Int) {

    var score = 0
        private set
    var movesLeft = moves
        private set
    var movesMade = 0
        private set

    init {
        // Initial board from a layout: fill, and make sure the first position is playable and calm.
        board.fillWithoutMatches()
        if (MatchFinder.hasMatch(board) || !MoveFinder.hasMove(board)) Shuffler.shuffle(board)
    }

    val canMove get() = movesLeft > 0

    fun snapshot() = board.snapshot()

    fun swap(a: Pos, b: Pos): MoveResult {
        if (!canMove || !MoveFinder.isValidSwap(board, a, b)) {
            if (canMove && board.inBounds(a) && board.inBounds(b) && a.isAdjacentTo(b) &&
                board[a].isMovable && board[b].isMovable
            ) {
                // Show the attempt, then swap back. Costs no move.
                swapPieces(a, b)
                val there = Phase(PhaseKind.SWAP, board.snapshot())
                swapPieces(a, b)
                return MoveResult(false, listOf(there, Phase(PhaseKind.SWAP_BACK, board.snapshot())))
            }
            return MoveResult(false, emptyList())
        }

        val pa = board[a].piece!!
        val pb = board[b].piece!!
        swapPieces(a, b)
        movesLeft--
        movesMade++
        val phases = mutableListOf(Phase(PhaseKind.SWAP, board.snapshot()))

        val combo = comboBlast(a, b, pa, pb)
        var cascade = 1
        if (combo != null) {
            val outcome = Resolver.clear(board, emptySet(), combo.cells, combo.fired)
            val delta = Scoring.applyMultiplier(outcome.bonusPoints, cascade)
            score += delta
            phases += Phase(PhaseKind.CLEAR, board.snapshot(), cascade, delta, specialsFired = outcome.specialsFired + 2)
            Gravity.settle(board)
            phases += Phase(PhaseKind.FALL, board.snapshot(), cascade)
            cascade++
        }
        phases += cascadeLoop(cascade, swapped = if (combo == null) listOf(b, a) else emptyList(), swapHorizontal = a.row == b.row)

        if (!MoveFinder.hasMove(board)) {
            Shuffler.shuffle(board)
            phases += Phase(PhaseKind.SHUFFLE, board.snapshot())
        }
        return MoveResult(true, phases)
    }

    private fun cascadeLoop(startCascade: Int, swapped: List<Pos>, swapHorizontal: Boolean): List<Phase> {
        val phases = mutableListOf<Phase>()
        var cascade = startCascade
        var playerSwap = swapped
        while (true) {
            val groups = MatchFinder.findGroups(board)
            if (groups.isEmpty()) break

            val matched = groups.flatMapTo(HashSet()) { it.cells }
            val created = groups.mapNotNull { g ->
                val special = g.specialToCreate(if (playerSwap.isNotEmpty()) swapHorizontal else null)
                if (special == Special.NONE) null else Triple(g.specialPosition(playerSwap), special, g.color)
            }
            val outcome = Resolver.clear(board, matched)
            for ((pos, special, color) in created) {
                val pieceColor = if (special == Special.COLOR_BOMB) Piece.NO_COLOR else color
                board[pos] = board[pos].copy(piece = board.newPiece(pieceColor, special), locked = false)
            }

            val base = groups.sumOf { it.pattern.basePoints }
            val delta = Scoring.applyMultiplier(base + outcome.bonusPoints, cascade)
            score += delta
            phases += Phase(
                PhaseKind.CLEAR, board.snapshot(), cascade, delta,
                patterns = groups.map { it.pattern },
                specialsCreated = created.map { it.second },
                specialsFired = outcome.specialsFired,
            )
            Gravity.settle(board)
            phases += Phase(PhaseKind.FALL, board.snapshot(), cascade)
            cascade++
            playerSwap = emptyList()
        }
        return phases
    }

    private class Combo(val cells: List<Pos>, val fired: Set<Long>)

    /** Effect of swapping a color bomb or two specials. Pieces have already been swapped: [pa] is now at [b]. */
    private fun comboBlast(a: Pos, b: Pos, pa: Piece, pb: Piece): Combo? {
        val sa = pa.special
        val sb = pb.special
        val fired = setOf(pa.id, pb.id)
        if (sa == Special.COLOR_BOMB || sb == Special.COLOR_BOMB) {
            if (sa == Special.COLOR_BOMB && sb == Special.COLOR_BOMB) {
                return Combo(board.positions().toList(), fired)
            }
            val other = if (sa == Special.COLOR_BOMB) pb else pa
            val targets = Resolver.cellsOfColor(board, other.color)
            if (other.special == Special.NONE) return Combo(targets + a + b, fired)
            // Turn every piece of that color into the other special; they all fire as they clear.
            for (p in targets) {
                val cell = board[p]
                val special = if (other.special.isStriped) {
                    if (board.random.nextBoolean()) Special.STRIPED_H else Special.STRIPED_V
                } else other.special
                board[p] = cell.copy(piece = cell.piece!!.copy(special = special))
            }
            val bomb = if (sa == Special.COLOR_BOMB) b else a
            return Combo(targets + bomb, setOf(if (sa == Special.COLOR_BOMB) pa.id else pb.id))
        }
        if (sa == Special.NONE || sb == Special.NONE) return null
        val cells = when {
            sa.isStriped && sb.isStriped -> Resolver.row(board, b.row) + Resolver.column(board, b.col)
            sa == Special.BOMB && sb == Special.BOMB -> Resolver.square(board, b, 2)
            else -> (b.row - 1..b.row + 1).filter { it in 0 until board.rows }.flatMap { Resolver.row(board, it) } +
                (b.col - 1..b.col + 1).filter { it in 0 until board.cols }.flatMap { Resolver.column(board, it) }
        }
        return Combo(cells + a + b, fired)
    }

    private fun swapPieces(a: Pos, b: Pos) {
        val pa = board[a].piece
        board[a] = board[a].copy(piece = board[b].piece)
        board[b] = board[b].copy(piece = pa)
    }
}
