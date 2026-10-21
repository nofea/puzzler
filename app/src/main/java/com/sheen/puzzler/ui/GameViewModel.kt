package com.sheen.puzzler.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.Goals
import com.sheen.puzzler.engine.MoveResult
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.level.LevelDef
import com.sheen.puzzler.ui.tutorial.TutorialState
import com.sheen.puzzler.ui.tutorial.TutorialStep
import com.sheen.puzzler.ui.tutorial.Tutorials
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GameStatus { PLAYING, WON, FAILED }

enum class EndReason { FINISHED, OUT_OF_MOVES, OUT_OF_TIME }

data class GameUiState(
    val level: LevelDef,
    /** Board before the current animation phase; the UI animates from here to [snapshot]. */
    val previous: BoardSnapshot,
    val snapshot: BoardSnapshot,
    /** Obstacles on the starting board: the level's goals (docs/SPEC.md §3). */
    val startGoals: Goals,
    val phaseKind: PhaseKind? = null,
    val phaseId: Int = 0,
    val phaseMillis: Int = 0,
    val score: Int = 0,
    val movesLeft: Int,
    val movesMade: Int = 0,
    val timeLeft: Int? = null,
    val status: GameStatus = GameStatus.PLAYING,
    val endReason: EndReason? = null,
    /** Finished on its own: an L1–10 level passed within its 3★ move par. */
    val autoFinished: Boolean = false,
    /** The level-cleared or Game Over splash is still playing; the result dialog waits for it. */
    val splash: Boolean = false,
    val busy: Boolean = false,
    val selected: Pos? = null,
    val banner: String? = null,
    /** In-level tutorial in progress (first play of a level that introduces a mechanic). */
    val tutorial: TutorialState? = null,
) {
    val goals get() = snapshot.goals()
    val passed get() = level.passed(score, goals.total)
    val stars get() = level.stars(score, movesMade, goals.total)
}

class GameViewModel(
    private val level: LevelDef,
    private val progress: ProgressRepository,
) : ViewModel() {
    private var engine: GameEngine = level.newEngine()
    private val _state = MutableStateFlow(initialState())
    val state: StateFlow<GameUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<GameEvent> = _events

    private var timerJob: Job? = null
    private var paused = false

    /** True until we know whether this level's tutorial was already seen; input and the clock wait for it. */
    private var tutorialLoading = false

    init {
        loadTutorial()
        startTimer()
    }

    private fun loadTutorial() {
        if (!Tutorials.hasTutorial(level.id)) return
        tutorialLoading = true
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            val seen = level.id in progress.progress.first().tutorialsSeen
            val steps = if (seen) null else Tutorials.forLevel(level.id, engine)
            _state.update { it.copy(busy = false, tutorial = steps?.let(::TutorialState)?.let(::resolved)) }
            tutorialLoading = false
        }
    }

    private fun initialState(): GameUiState {
        val snap = engine.snapshot()
        return GameUiState(
            level = level,
            previous = snap,
            snapshot = snap,
            startGoals = snap.goals(),
            movesLeft = engine.movesLeft,
            timeLeft = if (level.timed) level.timeSeconds else null,
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        if (!level.timed) return
        timerJob =
            viewModelScope.launch {
                while (true) {
                    delay(1000)
                    val s = _state.value
                    if (s.status != GameStatus.PLAYING) break
                    // The clock only runs once the tutorial (if any) is out of the way.
                    if (paused || tutorialLoading || s.tutorial?.visible == true) continue
                    val left = (s.timeLeft ?: 0) - 1
                    _state.update { it.copy(timeLeft = left.coerceAtLeast(0)) }
                    if (left <= 0) {
                        // A move in flight finishes first; maybeEnd runs again when it does.
                        if (!_state.value.busy) maybeEnd()
                        break
                    }
                }
            }
    }

    fun setPaused(value: Boolean) {
        paused = value
    }

    fun onTap(pos: Pos) {
        val s = _state.value
        if (s.busy || s.status != GameStatus.PLAYING) return
        if (!tutorialAllows(s.tutorial, pos)) return
        val selected = s.selected
        when {
            selected == null -> _state.update { it.copy(selected = pos) }
            selected == pos -> _state.update { it.copy(selected = null) }
            selected.isAdjacentTo(pos) -> onSwipe(selected, pos)
            else -> _state.update { it.copy(selected = pos) }
        }
    }

    fun onSwipe(
        a: Pos,
        b: Pos,
    ) {
        val s = _state.value
        val accepting = !s.busy && s.status == GameStatus.PLAYING && s.timeLeft != 0
        if (!accepting || !tutorialAllows(s.tutorial, a, b)) return
        val result = engine.swap(a, b)
        _state.update { it.copy(selected = null) }
        if (result.phases.isNotEmpty()) viewModelScope.launch { playOut(result, s.score) }
    }

    /** Plays a move's phases one by one so the board can animate each, then settles the state. */
    private suspend fun playOut(
        result: MoveResult,
        startScore: Int,
    ) {
        _state.update { it.copy(busy = true) }
        _events.tryEmit(if (result.valid) GameEvent.Swap else GameEvent.Invalid)
        var score = startScore
        for (phase in result.phases) {
            score += phase.scoreDelta
            val banner = bannerFor(phase) ?: _state.value.banner
            _state.update {
                it.copy(
                    previous = it.snapshot,
                    snapshot = phase.snapshot,
                    phaseKind = phase.kind,
                    phaseId = it.phaseId + 1,
                    phaseMillis = durationOf(phase.kind),
                    score = score,
                    movesLeft = engine.movesLeft,
                    movesMade = engine.movesMade,
                    banner = banner,
                )
            }
            effectsOf(phase).forEach { _events.tryEmit(it) }
            delay(durationOf(phase.kind).toLong())
        }
        _state.update {
            it.copy(busy = false, banner = null, score = engine.score, movesLeft = engine.movesLeft, movesMade = engine.movesMade)
        }
        advanceTutorialAfterMove()
        maybeEnd()
    }

    /** Ends the level once it's passed, banking the stars so far. */
    fun finish() {
        val s = _state.value
        if (s.busy || s.status != GameStatus.PLAYING || !s.passed) return
        end(EndReason.FINISHED)
    }

    /** The level-cleared or Game Over splash has played out; the result dialog shows. */
    fun splashDone() {
        _state.update { it.copy(splash = false) }
    }

    fun restart() {
        engine = level.newEngine()
        _state.value = initialState()
        loadTutorial()
        startTimer()
    }

    /** Dismisses the current callout (Next / Got it). Guided swaps advance by playing the swap instead. */
    fun nextTutorialStep() {
        val t = _state.value.tutorial ?: return
        if (!t.visible || t.step is TutorialStep.GuidedSwap) return
        advanceTutorial(t)
    }

    fun skipTutorial() {
        if (_state.value.tutorial == null) return
        _state.update { it.copy(tutorial = null) }
        markTutorialSeen()
    }

    /** While a tutorial callout is showing, the board only accepts the guided pair (if any). */
    private fun tutorialAllows(
        t: TutorialState?,
        vararg cells: Pos,
    ): Boolean {
        if (t == null || !t.visible) return true
        val step = t.step as? TutorialStep.GuidedSwap ?: return false
        return cells.all { it == step.a || it == step.b }
    }

    private fun advanceTutorialAfterMove() {
        val t = _state.value.tutorial ?: return
        when {
            t.visible && t.step is TutorialStep.GuidedSwap -> advanceTutorial(t)
            t.dormant && _state.value.passed -> _state.update { it.copy(tutorial = t.copy(dormant = false)) }
        }
    }

    private fun advanceTutorial(t: TutorialState) {
        val next = resolved(t.copy(index = t.index + 1, dormant = false))
        when (next.step) {
            null -> {
                _state.update { it.copy(tutorial = null) }
                markTutorialSeen()
            }

            // The rest of the tutorial is a tip that waits for the pass mark; the guided part counts as done.
            is TutorialStep.WhenPassed -> {
                markTutorialSeen()
                _state.update { it.copy(tutorial = next.copy(dormant = !it.passed)) }
            }

            else -> {
                _state.update { it.copy(tutorial = next) }
            }
        }
    }

    /** Turns a step that depends on the board as it is now (see [TutorialStep.FireSpecial]) into a concrete one. */
    private fun resolved(t: TutorialState): TutorialState {
        val step = t.step as? TutorialStep.FireSpecial ?: return t
        val steps = t.steps.toMutableList()
        steps[t.index] = Tutorials.resolveFireSpecial(step, engine)
        return t.copy(steps = steps)
    }

    private fun markTutorialSeen() {
        viewModelScope.launch { progress.markTutorialSeen(level.id) }
    }

    private fun maybeEnd() {
        val s = _state.value
        if (s.status != GameStatus.PLAYING) return
        when {
            // Playing on after this can only cost stars (docs/SPEC.md §3).
            !level.limited && s.passed && s.movesMade <= level.par3 -> end(EndReason.FINISHED, auto = true)

            !engine.canMove -> end(EndReason.OUT_OF_MOVES)

            s.timeLeft == 0 -> end(EndReason.OUT_OF_TIME)
        }
    }

    private fun end(
        reason: EndReason,
        auto: Boolean = false,
    ) {
        timerJob?.cancel()
        val s = _state.value
        val won = s.passed
        _state.update {
            it.copy(
                status = if (won) GameStatus.WON else GameStatus.FAILED,
                endReason = reason,
                autoFinished = auto,
                splash = true,
                selected = null,
                tutorial = null,
            )
        }
        if (won) viewModelScope.launch { progress.recordWin(level.id, s.score, s.movesMade, s.stars) }
    }
}
