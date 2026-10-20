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
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sheen.puzzler.PuzzlerApplication
import com.sheen.puzzler.engine.MoveFinder
import com.sheen.puzzler.engine.Pos
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class PuzzlerUiTest {

    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as PuzzlerApplication

    @Before
    fun resetProgress() = runBlocking { app.progress.reset() }

    @Test
    fun mapShowsFirstLevelUnlockedAndSecondLocked() {
        rule.onNodeWithContentDescription("Level 1").assertIsEnabled()
        rule.onNodeWithContentDescription("Level 2 locked").assertIsNotEnabled()
    }

    @Test
    fun winningALevelUnlocksTheNext() {
        runBlocking { app.progress.recordWin(1, score = 120, stars = 1) }
        rule.waitUntilExactlyOneExists(hasContentDescription("Level 2"), 5_000)
        rule.onNodeWithContentDescription("Level 3 locked").assertIsNotEnabled()
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
        while (mirror.canMove) {
            val (a, b) = MoveFinder.findMove(mirror.board)!!
            waitReady()
            swipe(a, b)
            mirror.swap(a, b)
            rule.waitUntil(5_000) { movesText() == "${mirror.movesLeft}" }
        }
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
}
