package com.sheen.puzzler.engine

/** Shortest run that matches. */
const val MIN_RUN = 3

/** Run length that makes a striped piece. */
const val STRIPED_RUN = 4

/** Run length that makes a color bomb. */
const val COLOR_BOMB_RUN = 5

/** A straight run of 3+ same-colored pieces. */
data class Run(
    val cells: List<Pos>,
    val horizontal: Boolean,
) {
    val length get() = cells.size
}

/** Runs that share at least one cell, merged into one scoring group. */
data class MatchGroup(
    val color: Int,
    val runs: List<Run>,
) {
    val cells: Set<Pos> = runs.flatMapTo(LinkedHashSet()) { it.cells }

    val pattern: Pattern =
        when {
            runs.any { it.length >= COLOR_BOMB_RUN } -> Pattern.LINE5
            runs.any { it.horizontal } && runs.any { !it.horizontal } -> Pattern.LT
            runs.any { it.length == STRIPED_RUN } -> Pattern.LINE4
            else -> Pattern.LINE3
        }

    /** Special piece this group creates, if any. [swapHorizontal] orients striped pieces made by a player swap. */
    fun specialToCreate(swapHorizontal: Boolean?): Special =
        when (pattern) {
            Pattern.LINE5 -> {
                Special.COLOR_BOMB
            }

            Pattern.LT -> {
                Special.BOMB
            }

            Pattern.LINE4 -> {
                val horizontal = swapHorizontal ?: runs.first { it.length == STRIPED_RUN }.horizontal
                if (horizontal) Special.STRIPED_H else Special.STRIPED_V
            }

            Pattern.LINE3 -> {
                Special.NONE
            }
        }

    /** Where a created special goes: a swapped cell inside the group, else the run crossing or the run's middle. */
    fun specialPosition(swapped: Collection<Pos>): Pos =
        swapped.firstOrNull { it in cells }
            ?: crossing()
            ?: runs.maxBy { it.length }.let { it.cells[it.length / 2] }

    /** The cell where an L/T group's horizontal and vertical runs meet; null for straight lines. */
    private fun crossing(): Pos? {
        if (pattern != Pattern.LT) return null
        val h = runs.filter { it.horizontal }.flatMap { it.cells }.toSet()
        return runs.filter { !it.horizontal }.flatMap { it.cells }.firstOrNull { it in h }
    }
}

object MatchFinder {
    fun findRuns(board: Board): List<Run> {
        val runs = mutableListOf<Run>()
        for (r in 0 until board.rows) scanLine(board, (0 until board.cols).map { Pos(r, it) }, true, runs)
        for (c in 0 until board.cols) scanLine(board, (0 until board.rows).map { Pos(it, c) }, false, runs)
        return runs
    }

    private fun scanLine(
        board: Board,
        line: List<Pos>,
        horizontal: Boolean,
        out: MutableList<Run>,
    ) {
        var start = 0
        while (start < line.size) {
            val color = board.colorAt(line[start])
            var end = start + 1
            if (color != null) {
                while (end < line.size && board.colorAt(line[end]) == color) end++
                if (end - start >= MIN_RUN) out += Run(line.subList(start, end).toList(), horizontal)
            }
            start = end
        }
    }

    fun findGroups(board: Board): List<MatchGroup> {
        val runs = findRuns(board)
        if (runs.isEmpty()) return emptyList()
        // Union-find over runs sharing a cell.
        val parent = IntArray(runs.size) { it }

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) x = parent[x]
            return x
        }
        val owner = HashMap<Pos, Int>()
        runs.forEachIndexed { i, run ->
            for (p in run.cells) {
                val other = owner.putIfAbsent(p, i)
                if (other != null) parent[find(i)] = find(other)
            }
        }
        return runs.indices.groupBy(::find).values.map { idx ->
            val members = idx.map { runs[it] }
            MatchGroup(board.colorAt(members.first().cells.first())!!, members)
        }
    }

    fun hasMatch(board: Board): Boolean = findRuns(board).isNotEmpty()
}
