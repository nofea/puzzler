package com.sheen.puzzler.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.Pattern
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import com.sheen.puzzler.level.LevelDef
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class GameStatus { PLAYING, WON, FAILED }

enum class EndReason { FINISHED, OUT_OF_MOVES, OUT_OF_TIME }

data class GameUiState(
    val level: LevelDef,
    /** Board before the current animation phase; the UI animates from here to [snapshot]. */
    val previous: BoardSnapshot,
    val snapshot: BoardSnapshot,
    val phaseKind: PhaseKind? = null,
    val phaseId: Int = 0,
    val phaseMillis: Int = 0,
    val score: Int = 0,
    val movesLeft: Int,
    val timeLeft: Int? = null,
    val status: GameStatus = GameStatus.PLAYING,
    val endReason: EndReason? = null,
    val busy: Boolean = false,
    val selected: Pos? = null,
    val banner: String? = null,
) {
    val stars get() = level.stars(score)
    val passed get() = score >= level.target
}

sealed interface GameEvent {
    data object Swap : GameEvent
    data object Invalid : GameEvent
    data class Match(val cascade: Int) : GameEvent
    data object Special : GameEvent
    data object Shuffle : GameEvent
    data object Win : GameEvent
    data object Fail : GameEvent
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

    init {
        startTimer()
    }

    private fun initialState(): GameUiState {
        val snap = engine.snapshot()
        return GameUiState(
            level = level,
            previous = snap,
            snapshot = snap,
            movesLeft = engine.movesLeft,
            timeLeft = if (level.timed) level.timeSeconds else null,
        )
    }

    private fun startTimer() {
        timerJob?.cancel()
        if (!level.timed) return
        timerJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                val s = _state.value
                if (s.status != GameStatus.PLAYING) break
                if (paused) continue
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
        val selected = s.selected
        when {
            selected == null -> _state.update { it.copy(selected = pos) }
            selected == pos -> _state.update { it.copy(selected = null) }
            selected.isAdjacentTo(pos) -> onSwipe(selected, pos)
            else -> _state.update { it.copy(selected = pos) }
        }
    }

    fun onSwipe(a: Pos, b: Pos) {
        val s = _state.value
        if (s.busy || s.status != GameStatus.PLAYING) return
        if (s.timeLeft == 0) return
        val result = engine.swap(a, b)
        _state.update { it.copy(selected = null) }
        if (result.phases.isEmpty()) return

        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            _events.tryEmit(if (result.valid) GameEvent.Swap else GameEvent.Invalid)
            var score = s.score
            for (phase in result.phases) {
                score += phase.scoreDelta
                val banner = when {
                    phase.kind == PhaseKind.SHUFFLE -> "No moves — shuffling…"
                    phase.kind != PhaseKind.CLEAR -> _state.value.banner
                    phase.cascade >= 2 -> "Cascade x${phase.cascade}!  +${phase.scoreDelta}"
                    Pattern.LINE5 in phase.patterns -> "Line of 5!  +${phase.scoreDelta}"
                    Pattern.LT in phase.patterns -> "L / T shape!  +${phase.scoreDelta}"
                    Pattern.LINE4 in phase.patterns -> "Line of 4!  +${phase.scoreDelta}"
                    else -> "+${phase.scoreDelta}"
                }
                _state.update {
                    it.copy(
                        previous = it.snapshot,
                        snapshot = phase.snapshot,
                        phaseKind = phase.kind,
                        phaseId = it.phaseId + 1,
                        phaseMillis = durationOf(phase.kind),
                        score = score,
                        movesLeft = engine.movesLeft,
                        banner = banner,
                    )
                }
                when (phase.kind) {
                    PhaseKind.CLEAR -> {
                        _events.tryEmit(GameEvent.Match(phase.cascade))
                        if (phase.specialsFired > 0 || phase.specialsCreated.isNotEmpty()) _events.tryEmit(GameEvent.Special)
                    }
                    PhaseKind.SHUFFLE -> _events.tryEmit(GameEvent.Shuffle)
                    else -> Unit
                }
                delay(durationOf(phase.kind).toLong())
            }
            _state.update { it.copy(busy = false, banner = null, score = engine.score, movesLeft = engine.movesLeft) }
            maybeEnd()
        }
    }

    /** Ends the level early once the target is reached, banking the stars so far. */
    fun finish() {
        val s = _state.value
        if (s.busy || s.status != GameStatus.PLAYING || !s.passed) return
        end(EndReason.FINISHED)
    }

    fun restart() {
        engine = level.newEngine()
        _state.value = initialState()
        startTimer()
    }

    private fun maybeEnd() {
        val s = _state.value
        if (s.status != GameStatus.PLAYING) return
        when {
            !engine.canMove -> end(EndReason.OUT_OF_MOVES)
            s.timeLeft == 0 -> end(EndReason.OUT_OF_TIME)
        }
    }

    private fun end(reason: EndReason) {
        timerJob?.cancel()
        val s = _state.value
        val won = s.passed
        _state.update { it.copy(status = if (won) GameStatus.WON else GameStatus.FAILED, endReason = reason, selected = null) }
        _events.tryEmit(if (won) GameEvent.Win else GameEvent.Fail)
        if (won) viewModelScope.launch { progress.recordWin(level.id, s.score, s.stars) }
    }

    companion object {
        fun durationOf(kind: PhaseKind) = when (kind) {
            PhaseKind.SWAP, PhaseKind.SWAP_BACK -> 140
            PhaseKind.CLEAR -> 220
            PhaseKind.FALL -> 260
            PhaseKind.SHUFFLE -> 450
        }
    }
}
