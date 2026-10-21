package com.sheen.puzzler.ui

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Who made the game and which version this is (docs/SPEC.md §5). */
@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember { appVersion(context) }
    Box(Modifier.fillMaxSize().padding(16.dp).testTag("about")) {
        TextButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Text("‹ Back") }
        Column(
            Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("PUZZLER", fontFamily = PuzzlerFonts.display, fontSize = 48.sp, color = PuzzlerColors.gold, letterSpacing = 4.sp)
            Text("Match. Cascade. Detonate.", fontFamily = PuzzlerFonts.display, fontSize = 16.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(Modifier.height(32.dp))
            Text("A game by", fontSize = 14.sp, color = Color.White.copy(alpha = 0.7f))
            Text("Sheen Games", fontFamily = PuzzlerFonts.display, fontSize = 26.sp, modifier = Modifier.testTag("about_creator"))
            Spacer(Modifier.height(24.dp))
            Text("Version $version", fontSize = 16.sp, color = Color.White.copy(alpha = 0.8f), modifier = Modifier.testTag("about_version"))
        }
    }
}

/** The installed app's version name (from the release tag; see README). */
fun appVersion(context: Context): String {
    val pm = context.packageManager
    val info =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") // The flags overload only exists from API 33.
            pm.getPackageInfo(context.packageName, 0)
        }
    return info.versionName ?: "unknown"
}
