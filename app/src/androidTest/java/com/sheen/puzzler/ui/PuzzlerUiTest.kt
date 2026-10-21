package com.sheen.puzzler.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sheen.puzzler.PuzzlerApplication
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.level.LevelDef
import com.sheen.puzzler.ui.rules.Rules
import com.sheen.puzzler.ui.tutorial.TutorialStep
import com.sheen.puzzler.ui.tutorial.Tutorials
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.random.Random

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PuzzlerUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PuzzlerApplication

    /** Fresh progress, with the level 1 and 6 tutorials already seen so other tests play the board directly. */
    @Before
    fun resetProgress() = runBlocking {
        app.progress.reset()
        app.progress.markTutorialSeen(1)
        app.progress.markTutorialSeen(6)
    }

    @Test
    fun levelOneTutorialGuidesTheFirstSwap() {
        runBlocking { app.progress.reset() }
        rule.onNodeWithTag("level_1").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("tutorial"), 5_000)
        val mirror = app.levels.level(1).newEngine()
        val guided = Tutorials.forLevel(1, mirror)!!.first() as TutorialStep.GuidedSwap

        // While the tutorial guides, any other swap is ignored and costs nothing.
        val other = MoveFinder.allMoves(mirror.board).first { (a, b) -> setOf(a, b) != setOf(guided.a, guided.b) }
        swipe(other.first, other.second)
        rule.waitForIdle()
        rule.onNodeWithTag("moves").assertTextEquals("12")

        playMove(mirror, guided.a to guided.b)
        // The step advances once the move has finished playing out.
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Score"), 10_000)
        rule.onNodeWithTag("tutorial_next").performClick()
        rule.onNode(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Moves")).assertExists()
        rule.onNodeWithTag("tutorial_next").performClick()
        // The last tip waits for 100 points; it shows straight away if the first move already got there.
        if (mirror.score >= LevelDef.PASS_SCORE) rule.onNodeWithTag("tutorial_next").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
        rule.waitUntil(5_000) { runBlocking { 1 in app.progress.progress.first().tutorialsSeen } }

        // Seen once, it doesn't come back.
        rule.onNodeWithText("Quit").performClick()
        rule.onNodeWithTag("level_1").performClick()
        waitReady()
        rule.onNodeWithTag("tutorial").assertDoesNotExist()
    }

    @Test
    fun levelOneTutorialPointsAtFinishOncePassed() {
        runBlocking { app.progress.reset() }
        rule.onNodeWithTag("level_1").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("tutorial"), 5_000)
        val mirror = app.levels.level(1).newEngine()
        val guided = Tutorials.forLevel(1, mirror)!!.first() as TutorialStep.GuidedSwap
        playMove(mirror, guided.a to guided.b)
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Score"), 10_000)
        rule.onNodeWithTag("tutorial_next").performClick()
        rule.onNodeWithTag("tutorial_next").assert(hasText("Got it")).performClick()
        while (mirror.score < LevelDef.PASS_SCORE) {
            rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
            playMove(mirror, best(mirror, highest = true))
        }
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Level passed!"), 10_000)
        // The spotlit Finish button works through the overlay.
        rule.onNodeWithTag("finish_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 5_000)
        rule.onNodeWithText("Level 1 cleared!").assertExists()
    }

    @Test
    fun levelSixTutorialHoldsTheTimerUntilItEnds() {
        runBlocking {
            app.progress.reset()
            app.progress.recordWin(5, score = 100, stars = 1)
        }
        rule.waitUntilExactlyOneExists(hasContentDescription("Level 6"), 5_000)
        rule.onNodeWithTag("level_6").performClick()
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Timer"), 5_000)
        Thread.sleep(2_500)
        rule.onNodeWithTag("time").assertTextEquals("1:00")
        for (title in listOf("Two limits", "Jelly", "Special gems")) {
            rule.onNodeWithTag("tutorial_next").performClick()
            rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText(title), 5_000)
            Thread.sleep(800)
        }
        rule.onNodeWithTag("time").assertTextEquals("1:00")
        rule.onNodeWithTag("tutorial_next").assert(hasText("Got it")).performClick()
        rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
        rule.waitUntil(3_000) { timeText() != "1:00" }

        // How to play pauses the clock too.
        val before = seconds(timeText())
        rule.onNodeWithTag("rules_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("rules_page"), 5_000)
        Thread.sleep(3_000)
        rule.onNodeWithText("‹ Back").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("time"), 5_000)
        val after = seconds(timeText())
        assertTrue("timer ran while How to play was open: $before -> $after", before - after <= 1)
    }

    @Test
    fun skippingTheTutorialRemembersIt() {
        runBlocking { app.progress.reset() }
        rule.onNodeWithTag("level_1").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("tutorial"), 5_000)
        rule.onNodeWithTag("tutorial_skip").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
        rule.waitUntil(5_000) { runBlocking { 1 in app.progress.progress.first().tutorialsSeen } }
        rule.onNodeWithTag("moves").assertTextEquals("12")
    }

    @Test
    fun rulesPageWalksThroughEveryCard() {
        rule.onNodeWithTag("how_to_play_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("rules_page"), 5_000)
        Rules.cards.forEachIndexed { i, card ->
            rule.waitUntil(5_000) { titleText() == card.title }
            rule.onNodeWithTag("rules_demo").assertExists()
            rule.onNodeWithTag("rules_next").assert(hasText(if (i == Rules.cards.lastIndex) "Done" else "Next ›")).performClick()
        }
        rule.waitUntilExactlyOneExists(hasTestTag("level_1"), 5_000)
    }

    @Test
    fun rulesPageOpensFromInsideALevel() {
        rule.onNodeWithTag("level_1").performClick()
        waitReady()
        rule.onNodeWithTag("rules_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("rules_page"), 5_000)
        rule.onNodeWithText("‹ Back").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("moves"), 5_000)
        rule.onNodeWithTag("moves").assertTextEquals("12")
    }

    @Test
    fun mapShowsFirstLevelUnlockedAndSecondLocked() {
        rule.onNodeWithContentDescription("Level 1").assertIsEnabled()
        rule.onNodeWithContentDescription("Level 2 locked").assertIsNotEnabled()
    }

    @Test
    fun winningALevelUnlocksTheNextAndSurvivesRecreation() {
        rule.onNodeWithTag("level_1").performClick()
        val mirror = app.levels.level(1).newEngine()
        while (mirror.score < LevelDef.PASS_SCORE) {
            assertTrue("greedy player ran out of moves on level 1", mirror.canMove)
            playMove(mirror, best(mirror, highest = true))
        }
        waitReady()
        rule.onNodeWithTag("finish_button").assertIsEnabled().performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 5_000)
        rule.onNodeWithText("Level 1 cleared!").assertExists()
        rule.onNodeWithText("Map").performClick()

        rule.waitUntilExactlyOneExists(hasContentDescription("Level 2"), 5_000)
        rule.onNodeWithContentDescription("Level 3 locked").assertIsNotEnabled()

        rule.activityRule.scenario.recreate()
        rule.waitUntilExactlyOneExists(hasContentDescription("Level 2"), 5_000)
        rule.onNodeWithContentDescription("Level 2").assertIsEnabled()
    }

    @Test
    fun runningOutOfMovesBelowTheTargetFails() {
        val levelId = FAIL_LEVEL
        runBlocking { app.progress.recordWin(levelId - 1, score = 100, stars = 1) }
        rule.waitUntilExactlyOneExists(hasContentDescription("Level $levelId"), 5_000)
        rule.onNodeWithTag("level_$levelId").performClick()
        val mirror = app.levels.level(levelId).newEngine()
        while (mirror.canMove) playMove(mirror, best(mirror, highest = false))
        assertTrue("worst-move player passed level $levelId; pick another FAIL_LEVEL", mirror.score < LevelDef.PASS_SCORE)
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 10_000)
        rule.onNodeWithText("Out of moves").assertExists()
        rule.onNodeWithTag("retry_button").assertExists()
    }

    @Test
    fun swipingAValidMoveUsesAMove() {
        rule.onNodeWithTag("level_1").performClick()
        rule.onNodeWithTag("moves").assertTextEquals("12")
        val mirror = app.levels.level(1).newEngine()
        val (a, b) = MoveFinder.findMove(mirror.board)!!
        waitReady()
        swipe(a, b)
        rule.waitUntil(5_000) { movesText() == "11" }
    }

    @Test
    fun playingEveryMoveEndsTheLevel() {
        rule.onNodeWithTag("level_1").performClick()
        // The screen's engine and this mirror share a seed, so the same swaps keep them in lockstep.
        val mirror = app.levels.level(1).newEngine()
        while (mirror.canMove) playMove(mirror, MoveFinder.findMove(mirror.board)!!)
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 10_000)
        rule.onNodeWithTag("score").assertTextEquals("${mirror.score}")
    }

    @Test
    fun soundToggleIsRemembered() {
        rule.onNodeWithTag("settings_button").performClick()
        rule.onNodeWithTag("sound_switch").assertIsOn().performClick()
        rule.waitUntil(5_000) { runBlocking { app.progress.progress.first().muted } }
        rule.onNodeWithTag("sound_switch").assertIsOff()
    }

    /** Swipes [move] on screen and plays it on [mirror], which shares the screen engine's seed and stays in lockstep. */
    private fun playMove(mirror: GameEngine, move: Pair<Pos, Pos>) {
        waitReady()
        swipe(move.first, move.second)
        mirror.swap(move.first, move.second)
        rule.waitUntil(5_000) { movesText() == "${mirror.movesLeft}" }
    }

    /** The move that scores most (or least) right now, simulated on a copy so [mirror] is untouched. */
    private fun best(mirror: GameEngine, highest: Boolean): Pair<Pos, Pos> {
        val moves = MoveFinder.allMoves(mirror.board)
        val score = { m: Pair<Pos, Pos> -> GameEngine(mirror.board.copy(Random(0)), 1).swap(m.first, m.second).scoreDelta }
        return if (highest) moves.maxBy(score) else moves.minBy(score)
    }

    private fun titleText(): String? = runCatching {
        rule.onNodeWithTag("rules_title").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }
    }.getOrNull()

    private fun seconds(clock: String) = clock.split(":").let { (m, s) -> m.toInt() * 60 + s.toInt() }

    private fun timeText(): String =
        rule.onNodeWithTag("time").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun movesText(): String =
        rule.onNodeWithTag("moves").fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("") { it.text }

    private fun waitReady() = rule.waitUntil(10_000) {
        rule.onNodeWithTag("board_container").fetchSemanticsNode().config.getOrNull(SemanticsProperties.StateDescription) == "ready"
    }

    private fun swipe(a: Pos, b: Pos) {
        rule.onNodeWithTag("board").performTouchInput {
            val cell = width / 8f
            fun center(p: Pos) = Offset((p.col + 0.5f) * cell, (p.row + 0.5f) * cell)
            swipe(center(a), center(b), durationMillis = 150)
        }
    }

    companion object {
        /** An untimed level where always playing the lowest-scoring move ends below 100 (58 points on its seed). */
        const val FAIL_LEVEL = 4
    }
}
