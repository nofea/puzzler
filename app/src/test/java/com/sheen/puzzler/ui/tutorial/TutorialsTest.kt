package com.sheen.puzzler.ui.tutorial

import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Special
import com.sheen.puzzler.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TutorialsTest {
    private val levels = LevelParser.parse(File("src/main/assets/levels.json").readText())

    private fun engine(id: Int) = levels[id - 1].newEngine()

    private fun script(id: Int) = Tutorials.forLevel(id, engine(id))!!

    /** The level's guided swap with [title], checked to be a real move on the starting board. */
    private fun guided(
        id: Int,
        title: String,
    ): TutorialStep.GuidedSwap {
        val engine = engine(id)
        val step = Tutorials.forLevel(id, engine)!!.single { it.title == title }
        assertTrue("level $id: '$title' should be a guided swap", step is TutorialStep.GuidedSwap)
        step as TutorialStep.GuidedSwap
        assertTrue(MoveFinder.isValidSwap(engine.board, step.a, step.b))
        return step
    }

    /** Plays [step] on [id]'s starting board and returns what its first clear did. */
    private fun play(
        id: Int,
        step: TutorialStep.GuidedSwap,
    ) = engine(id).let { e ->
        val before = e.snapshot()
        val clear = e.swap(step.a, step.b).phases.first { it.kind == PhaseKind.CLEAR }
        before to clear
    }

    @Test
    fun `every level that introduces a mechanic has a tutorial, and only those`() {
        val expected = setOf(1, 2, 3, 4, 5, 6, 7, 11, 16)
        for (level in levels) {
            val script = Tutorials.forLevel(level.id, level.newEngine())
            assertEquals("level ${level.id}", level.id in expected, script != null)
            assertEquals(level.id in expected, Tutorials.hasTutorial(level.id))
        }
    }

    @Test
    fun `level 1 starts with a guided swap and ends at Finish`() {
        guided(1, "Make a match")
        val steps = script(1)
        assertEquals(listOf("Make a match", "Score", "Moves", "Level passed!"), steps.map { it.title })
        assertTrue(steps.last() is TutorialStep.WhenPassed)
    }

    @Test
    fun `level 2 makes a striped item, then fires it`() {
        val make = guided(2, "Make a special")
        val (_, clear) = play(2, make)
        assertTrue(clear.specialsCreated.first().isStriped)
        assertEquals(listOf("Make a special", "Fire it", "More specials"), script(2).map { it.title })

        // Once the special exists, "Fire it" becomes a guided swap that fires it.
        val e = engine(2)
        e.swap(make.a, make.b)
        val fire = Tutorials.resolveFireSpecial(script(2)[1] as TutorialStep.FireSpecial, e)
        if (fire is TutorialStep.GuidedSwap) {
            val first = e.swap(fire.a, fire.b).phases.first { it.kind == PhaseKind.CLEAR }
            assertTrue(first.specialsFired > 0)
        } else {
            assertTrue((fire.target as Target.Cells).cells.isNotEmpty())
        }
    }

    @Test
    fun `levels 3 to 5 each guide a swap that clears their new obstacle`() {
        val (jellyBefore, jellyClear) = play(3, guided(3, "Clear jelly"))
        assertTrue(jellyClear.snapshot.goals().jelly < jellyBefore.goals().jelly)

        val (lockBefore, lockClear) = play(4, guided(4, "Break a chain"))
        assertTrue(lockClear.snapshot.goals().locked < lockBefore.goals().locked)

        val (stoneBefore, stoneClear) = play(5, guided(5, "Break a stone"))
        assertTrue(stoneClear.snapshot.cells.sumOf { it.stoneHp } < stoneBefore.cells.sumOf { it.stoneHp })

        // Each starts by spotlighting the obstacles and the goal counter.
        for ((id, title) in listOf(3 to "Jelly", 4 to "Locked items", 5 to "Stones")) {
            val target = script(id).first { it.title == title }.target as Target.Cells
            assertTrue(target.cells.isNotEmpty())
            assertEquals(HudTarget.GOALS, target.hud)
        }
    }

    @Test
    fun `level 6 explains combos, then guides a special if it can`() {
        val steps = script(6)
        assertEquals("Combos", steps.first().title)
        assertTrue("How to play" in steps.first().text)
        steps.getOrNull(1)?.let { step ->
            val (_, clear) = play(6, step as TutorialStep.GuidedSwap)
            assertTrue(clear.specialsCreated.isNotEmpty())
            assertTrue(clear.specialsCreated.first() != Special.NONE)
        }
    }

    @Test
    fun `levels 7, 11 and 16 are spotlight cards`() {
        val l7 = script(7)
        assertEquals(listOf("Gaps", "Dark stones"), l7.map { it.title })
        val stone = (l7[1].target as Target.Cells).cells.single()
        assertEquals(2, engine(7).board[stone].stoneHp)
        assertTrue((l7[0].target as Target.Cells).cells.all { !engine(7).board[it].exists })

        assertEquals(listOf(HudTarget.MOVES, HudTarget.SCORE), script(11).map { (it.target as Target.Hud).key })
        assertEquals(listOf(HudTarget.TIME, HudTarget.MOVES), script(16).map { (it.target as Target.Hud).key })
        assertTrue((script(7) + script(11) + script(16)).all { it is TutorialStep.Info })
    }

    @Test
    fun `building a script leaves the board untouched`() {
        for (id in listOf(2, 3, 4, 5, 6)) {
            val engine: GameEngine = engine(id)
            val before = engine.snapshot()
            Tutorials.forLevel(id, engine)
            assertEquals(before, engine.snapshot())
            assertEquals(0, engine.movesMade)
        }
        assertNull(Tutorials.forLevel(8, engine(8)))
    }
}
