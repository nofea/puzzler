package com.sheen.puzzler.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sheen.puzzler.PuzzlerApplication
import com.sheen.puzzler.data.SavedRun
import com.sheen.puzzler.engine.Board
import com.sheen.puzzler.engine.Endless
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.engine.UNLIMITED
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

    /** Fresh progress, with every in-level tutorial already seen so other tests play the board directly. */
    @Before
    fun resetProgress() =
        runBlocking {
            app.progress.reset()
            TUTORIAL_LEVELS.forEach { app.progress.markTutorialSeen(it) }
        }

    @Test
    fun levelOneTutorialGuidesTheFirstSwap() {
        runBlocking { app.progress.reset() }
        openLevel(1)
        rule.waitUntilExactlyOneExists(hasTestTag("tutorial"), 5_000)
        val mirror = app.levels.level(1).newEngine()
        val guided = Tutorials.forLevel(1, mirror)!!.first() as TutorialStep.GuidedSwap

        // While the tutorial guides, any other swap is ignored and costs nothing.
        val other = MoveFinder.allMoves(mirror.board).first { (a, b) -> setOf(a, b) != setOf(guided.a, guided.b) }
        swipe(other.first, other.second)
        rule.waitForIdle()
        rule.onNodeWithTag("moves").assertTextEquals("0")

        playMove(mirror, guided.a to guided.b)
        // The step advances once the move has finished playing out.
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Score"), 10_000)
        rule.onNodeWithTag("tutorial_next").performClick()
        rule.onNode(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Moves")).assertExists()
        rule.onNodeWithTag("tutorial_next").performClick()
        // The last tip waits for the pass; it shows straight away if the first move already got there.
        if (mirror.score >= 100) rule.onNodeWithTag("tutorial_next").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
        rule.waitUntil(5_000) {
            runBlocking {
                1 in
                    app.progress.progress
                        .first()
                        .tutorialsSeen
            }
        }

        // Seen once, it doesn't come back.
        rule.onNodeWithText("Quit").performClick()
        rule.onNodeWithTag("level_1").performClick()
        waitReady()
        rule.onNodeWithTag("tutorial").assertDoesNotExist()
    }

    @Test
    fun levelOneTutorialPointsAtFinishOncePassedAboveThePar() {
        runBlocking { app.progress.reset() }
        openLevel(1)
        rule.waitUntilExactlyOneExists(hasTestTag("tutorial"), 5_000)
        val level = app.levels.level(1)
        val mirror = level.newEngine()
        val guided = Tutorials.forLevel(1, mirror)!!.first() as TutorialStep.GuidedSwap
        playMove(mirror, guided.a to guided.b)
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Score"), 10_000)
        rule.onNodeWithTag("tutorial_next").performClick()
        rule.onNodeWithTag("tutorial_next").assert(hasText("Got it")).performClick()
        // Low-scoring moves past the 3★ par first, so the pass doesn't finish the level on its own.
        while (mirror.movesMade <= level.par3) {
            assertTrue("passed within the par; pick a slower route", mirror.score < 100)
            playMove(mirror, best(mirror, highest = false))
        }
        while (mirror.score < 100) playMove(mirror, best(mirror, highest = true))
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Level passed!"), 10_000)
        // The spotlit Finish button works through the overlay.
        rule.onNodeWithTag("finish_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 5_000)
        rule.onNodeWithText("Level 1 cleared!").assertExists()
    }

    @Test
    fun reachingThreeStarsFinishesTheLevelAndPushesOn() {
        openLevel(1)
        val level = app.levels.level(1)
        val mirror = level.newEngine()
        while (mirror.score < 100) playMove(mirror, best(mirror, highest = true))
        assertTrue("greedy player needed ${mirror.movesMade} moves on level 1", mirror.movesMade <= level.par3)
        // No Finish needed: the level ends by itself, and Next level leads.
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 5_000)
        rule.waitUntil(5_000) {
            runBlocking {
                app.progress.progress
                    .first()
                    .of(1)
                    .stars == 3
            }
        }
        rule.onNodeWithTag("next_button").assertExists().performClick()
        rule.waitUntil(5_000) { runCatching { rule.onNodeWithText("Level 2").assertExists() }.isSuccess }
    }

    @Test
    fun levelSixteenTutorialHoldsTheTimerUntilItEnds() {
        runBlocking {
            app.progress.reset()
            app.progress.recordWin(15, score = 100, moves = 10, stars = 1)
        }
        openLevel(16)
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Timer"), 5_000)
        Thread.sleep(2_500)
        rule.onNodeWithTag("time").assertTextEquals("1:00")
        rule.onNodeWithTag("tutorial_next").performClick()
        rule.waitUntilExactlyOneExists(hasAnyAncestor(hasTestTag("tutorial_card")) and hasText("Two limits"), 5_000)
        Thread.sleep(1_500)
        rule.onNodeWithTag("time").assertTextEquals("1:00")
        rule.onNodeWithTag("tutorial_next").assert(hasText("Got it")).performClick()
        rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
        rule.waitUntil(3_000) { text("time") != "1:00" }

        // How to play pauses the clock too.
        val before = seconds(text("time"))
        rule.onNodeWithTag("rules_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("rules_page"), 5_000)
        Thread.sleep(3_000)
        rule.onNodeWithText("‹ Back").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("time"), 5_000)
        val after = seconds(text("time"))
        assertTrue("timer ran while How to play was open: $before -> $after", before - after <= 1)
    }

    @Test
    fun skippingTheTutorialRemembersIt() {
        runBlocking { app.progress.reset() }
        openLevel(1)
        rule.waitUntilExactlyOneExists(hasTestTag("tutorial"), 5_000)
        rule.onNodeWithTag("tutorial_skip").performClick()
        rule.waitUntilDoesNotExist(hasTestTag("tutorial"), 5_000)
        rule.waitUntil(5_000) {
            runBlocking {
                1 in
                    app.progress.progress
                        .first()
                        .tutorialsSeen
            }
        }
        rule.onNodeWithTag("moves").assertTextEquals("0")
    }

    @Test
    fun rulesPageWalksThroughEveryCard() {
        rule.onNodeWithTag("how_to_play_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("rules_page"), 5_000)
        Rules.cards.forEachIndexed { i, card ->
            rule.waitUntil(5_000) { textOrNull("rules_title") == card.title }
            rule.onNodeWithTag("rules_demo").assertExists()
            rule.onNodeWithTag("rules_next").assert(hasText(if (i == Rules.cards.lastIndex) "Done" else "Next ›")).performClick()
        }
        rule.waitUntilExactlyOneExists(hasTestTag("home"), 5_000)
    }

    @Test
    fun rulesPageOpensFromInsideALevel() {
        openLevel(1)
        waitReady()
        rule.onNodeWithTag("rules_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("rules_page"), 5_000)
        rule.onNodeWithText("‹ Back").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("moves"), 5_000)
        rule.onNodeWithTag("moves").assertTextEquals("0")
    }

    @Test
    fun mapShowsFirstLevelUnlockedAndSecondLocked() {
        openCampaign()
        rule.onNodeWithContentDescription("Level 1").assertIsEnabled()
        rule.onNodeWithContentDescription("Level 2 locked").assertIsNotEnabled()
        rule.onNodeWithTag("home_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("home"), 5_000)
    }

    @Test
    fun winningALevelUnlocksTheNextAndSurvivesRecreation() {
        openLevel(1)
        val mirror = app.levels.level(1).newEngine()
        while (mirror.score < 100) playMove(mirror, best(mirror, highest = true))
        // Within the 3★ par it finishes by itself; otherwise Finish banks it.
        if (mirror.movesMade > app.levels.level(1).par3) {
            waitReady()
            rule.onNodeWithTag("finish_button").assertIsEnabled().performClick()
        }
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 5_000)
        rule.onNodeWithText("Level 1 cleared!").assertExists()
        rule.onNodeWithText("Map").performClick()

        rule.waitUntilExactlyOneExists(hasContentDescription("Level 2"), 5_000)
        rule.onNodeWithContentDescription("Level 3 locked").assertIsNotEnabled()

        rule.activityRule.scenario.recreate()
        rule.waitUntilExactlyOneExists(hasContentDescription("Level 2"), 5_000)
        rule.onNodeWithContentDescription("Level 2").assertIsEnabled()
        // The campaign records on Home count the win.
        rule.onNodeWithTag("home_button").performClick()
        rule.waitUntil(5_000) { text("record_campaign_points") == "${mirror.score}" }
    }

    @Test
    fun runningOutOfMovesBeforePassingFails() {
        val levelId = FAIL_LEVEL
        runBlocking { app.progress.recordWin(levelId - 1, score = 100, moves = 10, stars = 1) }
        openLevel(levelId)
        val level = app.levels.level(levelId)
        val mirror = level.newEngine()
        while (mirror.canMove) playMove(mirror, best(mirror, highest = false))
        assertTrue(
            "worst-move player passed level $levelId; pick another FAIL_LEVEL",
            !level.passed(mirror.score, mirror.board.goals().total),
        )
        // With animations off the Game Over splash is skipped and the dialog opens straight away.
        rule.waitUntilExactlyOneExists(hasTestTag("result_dialog"), 10_000)
        rule.onNodeWithText("Out of moves").assertExists()
        rule.onNodeWithTag("retry_button").assertExists()
        if (mirror.board.goals().total > 0) rule.onNodeWithTag("goals_left").assertExists()
    }

    @Test
    fun goalCountersTickDownAsObstaclesClear() {
        runBlocking { app.progress.recordWin(2, score = 100, moves = 3, stars = 3) }
        openLevel(3)
        val mirror = app.levels.level(3).newEngine()
        rule.onNodeWithTag("goal_jelly").assertTextEquals("${mirror.board.goals().jelly}")
        // A move that clears jelly, simulated on a copy so the mirror stays in step.
        val move =
            MoveFinder.allMoves(mirror.board).first { (a, b) ->
                val sim = GameEngine(mirror.board.copy(Random(0)), 1)
                val before = sim.board.goals().jelly
                sim.swap(a, b)
                sim.board.goals().jelly < before
            }
        playMove(mirror, move)
        waitReady()
        rule.onNodeWithTag("goal_jelly").assertTextEquals(
            mirror.board
                .goals()
                .jelly
                .let { if (it == 0) "✓" else "$it" },
        )
    }

    @Test
    fun swipingAValidMoveCountsAMove() {
        openLevel(1)
        rule.onNodeWithTag("moves").assertTextEquals("0")
        val mirror = app.levels.level(1).newEngine()
        val (a, b) = MoveFinder.findMove(mirror.board)!!
        waitReady()
        swipe(a, b)
        rule.waitUntil(5_000) { text("moves") == "1" }
    }

    @Test
    fun endlessIsLockedUntilLevelTenIsCleared() {
        rule.onNodeWithContentDescription("Endless locked").assertIsNotEnabled()
        runBlocking { app.progress.recordWin(10, score = 100, moves = 9, stars = 1) }
        rule.waitUntilExactlyOneExists(hasContentDescription("Endless"), 5_000)
        rule.onNodeWithTag("endless_button").assertIsEnabled()
    }

    @Test
    fun endlessRunIsSavedAndResumed() {
        // A known run, saved as if left earlier, so this test can mirror the board.
        val mirror = Endless.newEngine(Random(5))
        runBlocking {
            app.progress.recordWin(10, score = 100, moves = 9, stars = 1)
            app.progress.saveRun(SavedRun.of(mirror), scoreDelta = 0)
        }
        openEndless()
        rule.onNodeWithTag("moves").assertTextEquals("0")
        rule.onNodeWithTag("stage").assertTextEquals("Stage 1")
        val (a, b) = MoveFinder.findMove(mirror.board)!!
        swipe(a, b)
        rule.waitUntil(5_000) { text("moves") == "1" }
        waitReady()
        val score = text("score")

        // Leaving keeps the run; coming back resumes it.
        rule.onNodeWithText("Quit").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("home"), 5_000)
        rule.waitUntil(5_000) { text("record_endless_best") == score }
        openEndless()
        rule.onNodeWithTag("moves").assertTextEquals("1")
        rule.onNodeWithTag("score").assertTextEquals(score)
    }

    @Test
    fun aJammedEndlessBoardEndsTheRun() {
        // Two movable items among stones: no shuffle can ever make a move.
        val jammed = GameEngine(Board.fromPieces(listOf("SSS", "S01", "SSS"), colors = 4, random = Random(2)), UNLIMITED, endless = true)
        runBlocking {
            app.progress.recordWin(10, score = 100, moves = 9, stars = 1)
            app.progress.saveRun(SavedRun.of(jammed), scoreDelta = 0)
        }
        rule.onNodeWithTag("endless_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("run_summary"), 10_000)
        rule.waitUntil(5_000) {
            runBlocking {
                app.progress.progress
                    .first()
                    .endless.history.size == 1
            }
        }
        rule.onNodeWithTag("summary_new_run").performClick()
        waitReady()
        rule.onNodeWithTag("moves").assertTextEquals("0")
    }

    @Test
    fun resetEndlessClearsOnlyEndless() {
        runBlocking {
            app.progress.recordWin(1, score = 150, moves = 3, stars = 3)
            app.progress.saveRun(SavedRun.of(Endless.newEngine(Random(3))).copy(score = 700, movesMade = 60), scoreDelta = 700)
        }
        rule.waitUntil(5_000) { text("record_endless_best") == "700" }
        rule.onNodeWithTag("settings_button").performClick()
        rule.onNodeWithTag("reset_endless").performClick()
        rule.onNodeWithTag("confirm_reset").performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                app.progress.progress
                    .first()
                    .endless.bestScore == 0
            }
        }
        val progress = runBlocking { app.progress.progress.first() }
        assertTrue(!progress.hasSavedRun)
        assertTrue(progress.of(1).stars == 3)
    }

    @Test
    fun finaleReplaysFromSettingsAndEndsOnTheMap() {
        runBlocking { app.progress.markFinaleSeen() }
        rule.onNodeWithTag("settings_button").performClick()
        rule.onNodeWithTag("replay_finale").performClick()
        // Animations are off in tests, so it's the still card.
        rule.waitUntilExactlyOneExists(hasTestTag("finale_continue"), 5_000)
        rule.onNodeWithText("ALL LEVELS COMPLETE!").assertExists()
        rule.onNodeWithTag("finale_continue").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("level_map"), 5_000)
    }

    @Test
    fun aboutShowsTheCreatorAndVersion() {
        rule.onNodeWithTag("about_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("about"), 5_000)
        rule.onNodeWithTag("about_creator").assertTextEquals("Sheen Games")
        rule.onNodeWithTag("about_version").assertTextEquals("Version ${appVersion(app)}")
        rule.onNodeWithText("‹ Back").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("home"), 5_000)
    }

    @Test
    fun soundToggleIsRemembered() {
        rule.onNodeWithTag("settings_button").performClick()
        rule.onNodeWithTag("sound_switch").assertIsOn().performClick()
        rule.waitUntil(5_000) {
            runBlocking {
                app.progress.progress
                    .first()
                    .muted
            }
        }
        rule.onNodeWithTag("sound_switch").assertIsOff()
    }

    private fun openCampaign() {
        rule.waitUntilExactlyOneExists(hasTestTag("campaign_button"), 5_000)
        rule.onNodeWithTag("campaign_button").performClick()
        rule.waitUntilExactlyOneExists(hasTestTag("level_map"), 5_000)
    }

    private fun openEndless() {
        rule.waitUntilExactlyOneExists(hasContentDescription("Endless"), 5_000)
        rule.onNodeWithTag("endless_button").performClick()
        waitReady()
    }

    /** Opens the campaign map, scrolls to level [id], waits for it to unlock and opens it. */
    private fun openLevel(id: Int) {
        openCampaign()
        rule.onNodeWithTag("level_map").performScrollToNode(hasTestTag("level_$id"))
        rule.waitUntilExactlyOneExists(hasContentDescription("Level $id"), 5_000)
        rule.onNodeWithTag("level_$id").performClick()
    }

    /** Swipes [move] on screen and plays it on [mirror], which shares the screen engine's seed and stays in lockstep. */
    private fun playMove(
        mirror: GameEngine,
        move: Pair<Pos, Pos>,
    ) {
        waitReady()
        swipe(move.first, move.second)
        mirror.swap(move.first, move.second)
        val expected = if (mirror.limited) mirror.movesLeft else mirror.movesMade
        rule.waitUntil(5_000) { text("moves") == "$expected" }
    }

    /** The move that scores most (or least) right now, simulated on a copy so [mirror] is untouched. */
    private fun best(
        mirror: GameEngine,
        highest: Boolean,
    ): Pair<Pos, Pos> {
        val moves = MoveFinder.allMoves(mirror.board)
        val score = { m: Pair<Pos, Pos> -> GameEngine(mirror.board.copy(Random(0)), 1).swap(m.first, m.second).scoreDelta }
        return if (highest) moves.maxBy(score) else moves.minBy(score)
    }

    private fun seconds(clock: String) = clock.split(":").let { (m, s) -> (m.toInt() * 60) + s.toInt() }

    private fun textOrNull(tag: String): String? = runCatching { text(tag) }.getOrNull()

    private fun text(tag: String): String =
        rule
            .onNodeWithTag(tag)
            .fetchSemanticsNode()
            .config[SemanticsProperties.Text]
            .joinToString("") { it.text }

    private fun waitReady() =
        rule.waitUntil(10_000) {
            runCatching {
                rule
                    .onNodeWithTag("board_container")
                    .fetchSemanticsNode()
                    .config
                    .getOrNull(SemanticsProperties.StateDescription) == "ready"
            }.getOrDefault(false)
        }

    private fun swipe(
        a: Pos,
        b: Pos,
    ) {
        rule.onNodeWithTag("board").performTouchInput {
            val cell = width / 8f

            fun center(p: Pos) = Offset((p.col + 0.5f) * cell, (p.row + 0.5f) * cell)
            swipe(center(a), center(b), durationMillis = 150)
        }
    }

    companion object {
        /** A level with a move limit where always playing the lowest-scoring move ends before passing. */
        const val FAIL_LEVEL = 11

        val TUTORIAL_LEVELS = listOf(1, 2, 3, 4, 5, 6, 7, 11, 16)
    }
}
