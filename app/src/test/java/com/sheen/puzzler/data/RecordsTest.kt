package com.sheen.puzzler.data

import com.sheen.puzzler.engine.Endless
import com.sheen.puzzler.engine.MoveFinder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class RecordsTest {
    @Test
    fun `campaign points are the sum of each level's best score`() {
        val progress =
            Progress(
                levels =
                    mapOf(
                        1 to LevelRecord(bestScore = 120, fewestMoves = 4, stars = 3),
                        2 to LevelRecord(bestScore = 300, stars = 1),
                    ),
            )
        assertEquals(420, progress.campaignPoints)
        assertEquals(4, progress.totalStars)
    }

    @Test
    fun `endless unlocks once level 10 is cleared`() {
        assertTrue(!Progress().endlessUnlocked)
        assertTrue(Progress(levels = mapOf(Records.ENDLESS_UNLOCK_LEVEL to LevelRecord(100, 9, 1))).endlessUnlocked)
    }

    @Test
    fun `points per move only count from 50 moves`() {
        assertNull(Records.pointsPerMove(5_000, Records.PPM_MIN_MOVES - 1))
        assertEquals(20.0, Records.pointsPerMove(1_000, Records.PPM_MIN_MOVES)!!, 0.0)
    }

    @Test
    fun `history keeps the last 20, newest last`() {
        val runs = (1..25).map { EndlessRunResult(score = it, moves = it, stage = 1, jammed = false, at = it.toLong()) }
        val kept = Records.capped(runs)
        assertEquals(Records.HISTORY_SIZE, kept.size)
        assertEquals(25, kept.last().score)
        assertEquals(runs, Records.decodeEndless(Records.encodeEndless(runs)))
        val wins = listOf(CampaignWin(level = 3, score = 140, moves = 6, stars = 2, at = 9))
        assertEquals(wins, Records.decodeCampaign(Records.encodeCampaign(wins)))
        assertEquals(emptyList<CampaignWin>(), Records.decodeCampaign("not json"))
        assertEquals(emptyList<EndlessRunResult>(), Records.decodeEndless(null))
    }

    @Test
    fun `a saved run comes back exactly as it was left`() {
        val engine = Endless.newEngine(Random(8))
        repeat(30) {
            val (a, b) = MoveFinder.findMove(engine.board)!!
            engine.swap(a, b)
        }
        val saved = SavedRun.of(engine)
        val restored = SavedRun.decode(saved.encode())!!.toEngine(Random(1))
        assertEquals(engine.snapshot(), restored.snapshot())
        assertEquals(engine.score, restored.score)
        assertEquals(engine.movesMade, restored.movesMade)
        assertEquals(engine.board.colors, restored.board.colors)
        assertEquals(engine.board.nextPieceId, restored.board.nextPieceId)
        assertTrue(restored.endless)
        assertNull(SavedRun.decode("{broken"))
    }
}
