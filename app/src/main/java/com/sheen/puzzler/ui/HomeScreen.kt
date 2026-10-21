package com.sheen.puzzler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import com.sheen.puzzler.data.Records

/** The screen the app opens on: pick a mode, and see the records of both (docs/SPEC.md §5). */
@Composable
fun HomeScreen(
    progressRepo: ProgressRepository,
    maxStars: Int,
    onCampaign: () -> Unit,
    onEndless: () -> Unit,
    onRules: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp)
            .testTag("home"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PUZZLER", fontFamily = PuzzlerFonts.display, fontSize = 52.sp, color = PuzzlerColors.gold, letterSpacing = 4.sp)
        Text("Match. Cascade. Detonate.", fontFamily = PuzzlerFonts.display, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
        Spacer(Modifier.height(40.dp))
        ModeButton("Campaign", "20 levels", enabled = true, tag = "campaign_button", onClick = onCampaign)
        Spacer(Modifier.height(16.dp))
        val unlocked = progress.endlessUnlocked
        ModeButton(
            "Endless",
            if (unlocked) {
                if (progress.hasSavedRun) "Resume your run" else "Play without limits"
            } else {
                "Clear level ${Records.ENDLESS_UNLOCK_LEVEL} to unlock"
            },
            enabled = unlocked,
            tag = "endless_button",
            onClick = onEndless,
        )
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            TextButton(onClick = onRules, modifier = Modifier.testTag("how_to_play_button")) { Text("How to play") }
            TextButton(onClick = onSettings, modifier = Modifier.testTag("settings_button")) { Text("Settings") }
            TextButton(onClick = onAbout, modifier = Modifier.testTag("about_button")) { Text("About") }
        }
        Spacer(Modifier.height(24.dp))
        RecordsPanel(progress, maxStars)
    }
}

@Composable
private fun ModeButton(
    title: String,
    subtitle: String,
    enabled: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(20.dp),
        colors =
            ButtonDefaults.buttonColors(
                disabledContainerColor = Color(0xFF4A4060),
                disabledContentColor = Color.White.copy(alpha = 0.7f),
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .height(84.dp)
                .testTag(tag)
                .semantics { contentDescription = if (enabled) title else "$title locked" },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (enabled) title else "🔒 $title", fontFamily = PuzzlerFonts.display, fontSize = 26.sp)
            Text(subtitle, fontSize = 13.sp)
        }
    }
}

/** Campaign points and stars, and the Endless bests; a dash until there is one. */
@Composable
private fun RecordsPanel(
    progress: Progress,
    maxStars: Int,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(PuzzlerColors.surface, RoundedCornerShape(16.dp))
            .padding(16.dp)
            .testTag("records"),
    ) {
        Text("Records", fontFamily = PuzzlerFonts.display, fontSize = 20.sp, color = PuzzlerColors.gold)
        Spacer(Modifier.height(8.dp))
        Text("Campaign", fontWeight = FontWeight.Bold)
        RecordLine("Points", "${progress.campaignPoints}", "record_campaign_points")
        RecordLine("Stars", "★ ${progress.totalStars} / $maxStars", "record_stars")
        Spacer(Modifier.height(8.dp))
        Text("Endless", fontWeight = FontWeight.Bold)
        val endless = progress.endless
        RecordLine("Best run", if (endless.bestScore > 0) "${endless.bestScore}" else "–", "record_endless_best")
        RecordLine("Best points per move", endless.bestPointsPerMove?.let { "%.1f".format(it) } ?: "–", "record_endless_ppm")
    }
}

@Composable
private fun RecordLine(
    label: String,
    value: String,
    tag: String,
) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.weight(1f))
        Text(value, fontWeight = FontWeight.Bold, modifier = Modifier.testTag(tag))
    }
}
