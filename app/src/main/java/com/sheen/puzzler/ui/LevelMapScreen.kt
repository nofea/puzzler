package com.sheen.puzzler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.data.Progress
import com.sheen.puzzler.data.ProgressRepository
import com.sheen.puzzler.level.LevelDef

@Composable
fun LevelMapScreen(
    levels: List<LevelDef>,
    progressRepo: ProgressRepository,
    onPlay: (Int) -> Unit,
    onSettings: () -> Unit,
) {
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Puzzler", fontSize = 32.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onSettings, modifier = Modifier.testTag("settings_button")) { Text("Settings") }
        }
        val totalStars = levels.sumOf { progress.of(it.id).stars }
        Text("★ $totalStars / ${levels.size * 3}", color = PuzzlerColors.gold, fontSize = 16.sp)
        LazyColumn(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item { Spacer(Modifier.size(8.dp)) }
            items(levels, key = { it.id }) { level ->
                // Zig-zag path: offset nodes left/right.
                val offset = listOf(-80, -30, 30, 80, 30, -30)[(level.id - 1) % 6]
                LevelNode(level, progress, onPlay, Modifier.padding(start = (offset + 80).dp, end = (80 - offset).dp))
            }
            item { Spacer(Modifier.size(24.dp)) }
        }
    }
}

@Composable
private fun LevelNode(level: LevelDef, progress: Progress, onPlay: (Int) -> Unit, modifier: Modifier) {
    val unlocked = progress.isUnlocked(level.id)
    val stars = progress.of(level.id).stars
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(72.dp)
                .background(if (unlocked) MaterialTheme.colorScheme.primary else Color(0xFF4A4060), CircleShape)
                .border(3.dp, if (stars == 3) PuzzlerColors.gold else Color.White.copy(alpha = 0.3f), CircleShape)
                .testTag("level_${level.id}")
                .semantics { contentDescription = if (unlocked) "Level ${level.id}" else "Level ${level.id} locked" }
                .clickable(enabled = unlocked) { onPlay(level.id) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                if (unlocked) "${level.id}" else "🔒",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (unlocked) MaterialTheme.colorScheme.onPrimary else Color.White.copy(alpha = 0.7f),
            )
        }
        Text(
            "★".repeat(stars) + "☆".repeat(3 - stars),
            color = PuzzlerColors.gold,
            fontSize = 16.sp,
        )
        if (level.timed) Text("⏱ ${level.timeSeconds}s", fontSize = 11.sp, color = Color.White.copy(alpha = 0.7f))
    }
}
