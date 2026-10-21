package com.sheen.puzzler.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sheen.puzzler.data.EndlessRunResult
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.data.Records
import com.sheen.puzzler.data.SavedRun
import com.sheen.puzzler.engine.BoardSnapshot
import com.sheen.puzzler.engine.Endless
import com.sheen.puzzler.engine.GameEngine
import com.sheen.puzzler.engine.MoveResult
import com.sheen.puzzler.engine.PhaseKind
import com.sheen.puzzler.engine.Pos
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.random.Random

/** How a jammed run ended up, for the run summary card. */
data class RunSummary(
    val score: Int,
    val moves: Int,
    val stage: Int,
    val pointsPerMove: Double?,
    val newBestScore: Boolean,
    val newBestPointsPerMove: Boolean,
)

data class EndlessUiState(
    /** The saved run (if any) is still loading. */
    val loading: Boolean = true,
    val previous: BoardSnapshot? = null,
    val snapshot: BoardSnapshot? = null,
    val phaseKind: PhaseKind? = null,
    val phaseId: Int = 0,
    val phaseMillis: Int = 0,
    val score: Int = 0,
    val movesMade: Int = 0,
    val busy: Boolean = false,
    val selected: Pos? = null,
    val banner: String? = null,
    /** "Stage N!" toast; [stageToastId] changes each time one shows so the screen can replay it. */
    val stageToast: Int? = null,
    val stageToastId: Int = 0,
    /** The board jammed and the run is over: the Game Over splash plays, then [summary] shows. */
    val jammed: Boolean = false,
    val splash: Boolean = false,
    val summary: RunSummary? = null,
) {
    val stage get() = Endless.stage(score)
    val pointsPerMove get() = if (movesMade == 0) null else score.toDouble() / movesMade
}

/** Endless mode (docs/SPEC.md §4): one run that's saved after every move and resumed on return. */
class EndlessViewModel(
    private val progress: ProgressRepository,
) : ViewModel() {
    private lateinit var engine: GameEngine
    private val _state = MutableStateFlow(EndlessUiState())
    val state: StateFlow<EndlessUiState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<GameEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<GameEvent> = _events

    init {
        viewModelScope.launch {
            val saved = progress.loadRun()
            start(saved?.toEngine(Random(System.nanoTime())) ?: Endless.newEngine(Random(System.nanoTime())))
        }
    }

    private fun start(engine: GameEngine) {
        this.engine = engine
        val snap = engine.snapshot()
        _state.value = EndlessUiState(loading = false, previous = snap, snapshot = snap, score = engine.score, movesMade = engine.movesMade)
        // A saved board that can't be played any more ends straight away.
        if (engine.jammed) viewModelScope.launch { jam() }
    }

    fun onTap(pos: Pos) {
        val s = _state.value
        if (s.loading || s.busy || s.jammed) return
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
        if (s.loading || s.busy || s.jammed) return
        val stageBefore = engine.stage
        val result = engine.swap(a, b)
        _state.update { it.copy(selected = null) }
        if (result.phases.isEmpty()) return
        // Save before animating, so leaving mid-move still keeps it.
        val save = if (result.valid) viewModelScope.launch { progress.saveRun(SavedRun.of(engine), result.scoreDelta) } else null
        viewModelScope.launch {
            playOut(result)
            if (engine.stage > stageBefore) {
                _events.tryEmit(GameEvent.StageUp(engine.stage))
                _state.update { it.copy(stageToast = engine.stage, stageToastId = it.stageToastId + 1) }
            }
            if (engine.jammed) {
                save?.join()
                jam()
            }
        }
    }

    private suspend fun playOut(result: MoveResult) {
        _state.update { it.copy(busy = true) }
        _events.tryEmit(if (result.valid) GameEvent.Swap else GameEvent.Invalid)
        var score = _state.value.score
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
                    movesMade = engine.movesMade,
                    banner = banner,
                )
            }
            effectsOf(phase).forEach { _events.tryEmit(it) }
            delay(durationOf(phase.kind).toLong())
        }
        _state.update { it.copy(busy = false, banner = null, score = engine.score, movesMade = engine.movesMade) }
    }

    /** The board jammed: the run is over and goes into the recent runs. */
    private suspend fun jam() {
        val records = progress.progress.first().endless
        val ppm = Records.pointsPerMove(engine.score, engine.movesMade)
        val summary =
            RunSummary(
                score = engine.score,
                moves = engine.movesMade,
                stage = engine.stage,
                pointsPerMove = ppm,
                newBestScore = engine.score > 0 && engine.score >= records.bestScore,
                newBestPointsPerMove = ppm != null && ppm >= (records.bestPointsPerMove ?: 0.0),
            )
        progress.endRun(result(jammed = true))
        _state.update { it.copy(jammed = true, splash = true, summary = summary, selected = null) }
    }

    fun splashDone() {
        _state.update { it.copy(splash = false) }
    }

    /** Ends the current run (if it had started) and starts a fresh one. */
    fun newRun() {
        val s = _state.value
        if (s.loading || s.busy) return
        if (!s.jammed && engine.movesMade > 0) {
            val ended = result(jammed = false)
            viewModelScope.launch { progress.endRun(ended) }
        }
        start(Endless.newEngine(Random(System.nanoTime())))
    }

    private fun result(jammed: Boolean) = EndlessRunResult(engine.score, engine.movesMade, engine.stage, jammed, System.currentTimeMillis())
}
