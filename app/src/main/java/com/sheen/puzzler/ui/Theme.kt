package com.sheen.puzzler.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.sheen.puzzler.R

object PuzzlerColors {
    val background = Color(0xFF1B1035)
    val surface = Color(0xFF2A1856)
    val tileLight = Color(0xFF3A2470)
    val tileDark = Color(0xFF321F63)
    val jelly = Color(0x99FF6FB5)
    val stone = Color(0xFF8A8F98)
    val stoneHard = Color(0xFF5A606B)
    val lock = Color(0xFFD9DDE3)
    val gold = Color(0xFFFFD34D)
    val bombOrb = Color(0xFF140A26)

    /**
     * Piece colors by color index. Each item's artwork is recolored to its color (docs/SPEC.md §7), so shape and color
     * both identify a piece.
     */
    val pieces = listOf(
        Color(0xFFE5484D), // red: fries
        Color(0xFFFF9F2E), // orange: burger
        Color(0xFFF5D442), // yellow: pizza
        Color(0xFF46C46E), // green: potted plant
        Color(0xFF3E8BFF), // blue: soda can
        Color(0xFFB065F0), // purple: chocolate bar
    )
}

object PuzzlerFonts {
    /** Fredoka (SIL OFL, assets/licenses/Fredoka-OFL.txt), a variable font pinned to Bold. */
    @OptIn(ExperimentalTextApi::class)
    val display = FontFamily(
        Font(R.font.fredoka, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )
}

@Composable
fun PuzzlerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Color(0xFFFF6FB5),
            onPrimary = Color(0xFF2A0019),
            secondary = PuzzlerColors.gold,
            background = PuzzlerColors.background,
            surface = PuzzlerColors.surface,
            onBackground = Color.White,
            onSurface = Color.White,
        ),
        content = content,
    )
}
