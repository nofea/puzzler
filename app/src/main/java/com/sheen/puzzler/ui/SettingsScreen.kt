package com.sheen.puzzler.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sheen.puzzler.data.Progress
import com.sheen.puzzler.data.ProgressRepository
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(progressRepo: ProgressRepository, onBack: () -> Unit, onRules: () -> Unit) {
    val progress by progressRepo.progress.collectAsState(initial = Progress())
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("‹ Back") }
            Text("Settings", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
        SettingRow("Sound", !progress.muted, "sound_switch") { on -> scope.launch { progressRepo.setMuted(!on) } }
        SettingRow("Vibration", progress.haptics, "haptics_switch") { on -> scope.launch { progressRepo.setHaptics(on) } }
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onRules).padding(vertical = 12.dp).testTag("settings_how_to_play"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("How to play", fontSize = 18.sp, modifier = Modifier.weight(1f))
            Text("›", fontSize = 22.sp)
        }
        Spacer(Modifier.padding(12.dp))
        Button(onClick = { confirmReset = true }) { Text("Reset progress") }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset progress?") },
            text = { Text("This locks all levels except level 1, clears your stars and best scores, and brings back the tutorials.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { progressRepo.reset() }
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SettingRow(label: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 18.sp, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}
