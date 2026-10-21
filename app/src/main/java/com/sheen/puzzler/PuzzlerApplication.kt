package com.sheen.puzzler

import android.app.Application
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.level.LevelRepository

class PuzzlerApplication : Application() {
    val levels by lazy { LevelRepository(this) }
    val progress by lazy { ProgressRepository(this, levels.levels.size) }

    /** The launch intro plays once per process, i.e. on cold start only. */
    var introShown = false
}
