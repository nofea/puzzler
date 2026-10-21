package com.sheen.puzzler.level

import android.content.Context

class LevelRepository(
    context: Context,
) {
    val levels: List<LevelDef> by lazy {
        LevelParser.parse(
            context.assets
                .open("levels.json")
                .bufferedReader()
                .use { it.readText() },
        )
    }

    fun level(id: Int): LevelDef = levels.first { it.id == id }
}
