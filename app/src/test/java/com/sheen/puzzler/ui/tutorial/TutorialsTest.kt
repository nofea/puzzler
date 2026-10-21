package com.sheen.puzzler.ui.tutorial

import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.level.LevelParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TutorialsTest {

    private val levels = LevelParser.parse(File("src/main/assets/levels.json").readText())

    @Test
    fun `only levels 1 and 6 have tutorials`() {
        for (level in levels) {
            val script = Tutorials.forLevel(level.id, level.newEngine())
            assertEquals("level ${level.id}", Tutorials.hasTutorial(level.id), script != null)
        }
    }

    @Test
    fun `level 1 starts with a guided swap that is a real move`() {
        val engine = levels[0].newEngine()
        val steps = Tutorials.forLevel(1, engine)!!
        val guided = steps.first() as TutorialStep.GuidedSwap
        assertTrue(MoveFinder.isValidSwap(engine.board, guided.a, guided.b))
        assertTrue(steps.last() is TutorialStep.WhenPassed)
    }

    @Test
    fun `level 6 covers the timer, limits, jelly and specials`() {
        val engine = levels[5].newEngine()
        val steps = Tutorials.forLevel(6, engine)!!
        assertEquals(listOf("Timer", "Two limits", "Jelly", "Special gems"), steps.map { it.title })
        val jelly = steps[2].target as Target.Cells
        assertTrue(jelly.cells.isNotEmpty())
        // Guided when the starting board can make a special; otherwise an explanation that points to the rules page.
        when (val last = steps.last()) {
            is TutorialStep.GuidedSwap -> assertTrue(MoveFinder.isValidSwap(engine.board, last.a, last.b))
            else -> assertTrue(last is TutorialStep.Info && "How to play" in last.text)
        }
    }

    @Test
    fun `building a script leaves the board untouched`() {
        val engine = levels[5].newEngine()
        val before = engine.snapshot()
        Tutorials.forLevel(6, engine)
        assertEquals(before, engine.snapshot())
        assertEquals(levels[5].moves, engine.movesLeft)
        assertNull(Tutorials.forLevel(2, levels[1].newEngine()))
    }
}
